/*
 * The MIT License
 *
 * Copyright 2026 Ilia Lazebnik.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package jenkins.plugins.itemstorage.gcs;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.CredentialAccessBoundary;
import com.google.auth.oauth2.CredentialAccessBoundary.AccessBoundaryRule;
import com.google.auth.oauth2.DownscopedCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds the settings needed to build a GCS {@link Storage} client from Application Default
 * Credentials (ADC).
 *
 * <p>ADC resolves Workload Identity on GKE, {@code GOOGLE_APPLICATION_CREDENTIALS}, or gcloud user
 * credentials locally &mdash; no HMAC keys and no service-account JSON on disk.
 *
 * <p>The upload/download of a cache runs on the build agent (see the {@code *Callable} classes),
 * which may not share the controller's Google identity. To keep the IAM grant on the controller
 * only, this helper mints a short-lived OAuth access token from ADC <em>on the controller</em> and
 * ships it to the agent. That token is <em>downscoped</em> with a Credential Access Boundary to
 * {@code objectAdmin} on the single configured bucket, so an untrusted build cannot use it against
 * any other bucket the controller identity can reach. If a downscoped token cannot be minted (e.g.
 * end-user ADC that does not support token exchange), no token is shipped and the agent falls back
 * to resolving its own ADC (e.g. Workload Identity bound to the agent pod).
 */
public class GCSClientHelper implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(GCSClientHelper.class.getName());
    private static final String STORAGE_SCOPE = "https://www.googleapis.com/auth/devstorage.read_write";
    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final String projectId;

    // Short-lived OAuth access token minted on the controller and shipped to the agent over the
    // remoting channel. The helper is built per cache operation and never saved to config.xml, so
    // this value is never persisted to disk.
    @SuppressWarnings("lgtm[jenkins/plaintext-storage]")
    private final String accessToken;

    private final long accessTokenExpiryEpochMilli;

    // Storage endpoint override. Null in production, where the client targets the public GCS
    // endpoint; set only by tests to point at a local emulator.
    private final String host;

    private transient Storage storage;

    private GCSClientHelper(String projectId, String accessToken, long accessTokenExpiryEpochMilli, String host) {
        this.projectId = projectId;
        this.accessToken = accessToken;
        this.accessTokenExpiryEpochMilli = accessTokenExpiryEpochMilli;
        this.host = host;
    }

    /**
     * Build a helper on the controller, eagerly minting a bucket-scoped token to ship to the agent.
     *
     * @param projectId optional GCP project id; inferred from ADC/metadata when blank
     * @param bucketName bucket the shipped token is downscoped to
     */
    static GCSClientHelper fromApplicationDefault(String projectId, String bucketName) {
        String token = null;
        long expiry = 0L;
        try {
            GoogleCredentials source = GoogleCredentials.getApplicationDefault().createScoped(CLOUD_PLATFORM_SCOPE);
            AccessBoundaryRule rule = AccessBoundaryRule.newBuilder()
                    .setAvailableResource("//storage.googleapis.com/projects/_/buckets/" + bucketName)
                    .addAvailablePermission("inRole:roles/storage.objectAdmin")
                    .build();
            DownscopedCredentials downscoped = DownscopedCredentials.newBuilder()
                    .setSourceCredential(source)
                    .setCredentialAccessBoundary(
                            CredentialAccessBoundary.newBuilder().addRule(rule).build())
                    .build();
            AccessToken minted = downscoped.refreshAccessToken();
            if (minted != null) {
                token = minted.getTokenValue();
                expiry = minted.getExpirationTime() != null
                        ? minted.getExpirationTime().getTime()
                        : 0L;
            }
        } catch (IOException e) {
            // Could not mint a downscoped controller-side token; the agent will attempt its own ADC.
            LOGGER.log(Level.FINE, "Could not mint a downscoped GCS token on the controller", e);
        }
        return new GCSClientHelper(projectId, token, expiry, null);
    }

    /**
     * Build a helper targeting a storage emulator rather than the public GCS endpoint. Test-only:
     * an emulator authenticates nothing, so ADC resolution is skipped entirely.
     *
     * @param host emulator base URL, e.g. {@code http://localhost:4443}
     */
    static GCSClientHelper forHost(String host) {
        return new GCSClientHelper("test-project", null, 0L, host);
    }

    synchronized Storage storage() {
        if (storage == null) {
            StorageOptions.Builder builder = StorageOptions.newBuilder();
            if (host != null) {
                builder.setHost(host).setCredentials(NoCredentials.getInstance());
            } else {
                builder.setCredentials(resolveCredentials());
            }
            if (projectId != null && !projectId.isBlank()) {
                builder.setProjectId(projectId);
            }
            storage = builder.build().getService();
        }
        return storage;
    }

    private GoogleCredentials resolveCredentials() {
        if (accessToken != null) {
            Date expiry = accessTokenExpiryEpochMilli > 0 ? new Date(accessTokenExpiryEpochMilli) : null;
            return GoogleCredentials.create(new AccessToken(accessToken, expiry));
        }
        try {
            return GoogleCredentials.getApplicationDefault().createScoped(STORAGE_SCOPE);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No Google Application Default Credentials available on this node. "
                            + "Configure Workload Identity, GOOGLE_APPLICATION_CREDENTIALS, or gcloud ADC.",
                    e);
        }
    }
}
