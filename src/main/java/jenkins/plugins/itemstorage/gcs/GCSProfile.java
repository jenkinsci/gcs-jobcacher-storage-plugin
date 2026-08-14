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

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import hudson.FilePath;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable operations against a GCS bucket. Metadata operations (exists/delete/rename) run on the
 * controller; the byte transfers (upload/download) are dispatched to the agent via {@link FilePath}
 * callables.
 *
 * <p>Modelled on the S3 extension's {@code S3Profile}.
 */
public class GCSProfile {

    private final GCSClientHelper helper;
    private final String projectId;
    private final String bucketName;
    private final String prefix;

    GCSProfile(String projectId, String bucketName, String prefix) {
        // Controller-side metadata operations use ADC directly. A profile outlives the moment it was
        // created — jobcacher builds it when a cache step opens and reuses it to check for an
        // existing cache when the step closes — so it must hold a credential that can refresh.
        this.helper = GCSClientHelper.usingApplicationDefault(projectId);
        this.projectId = projectId;
        this.bucketName = bucketName;
        this.prefix = prefix;
    }

    /** Injects a pre-built helper; used by tests to target a storage emulator. */
    GCSProfile(GCSClientHelper helper, String prefix) {
        this.helper = helper;
        this.projectId = null;
        this.bucketName = null;
        this.prefix = prefix;
    }

    /**
     * Helper for a transfer that runs on the agent, carrying a token minted now rather than when
     * this profile was built. The emulator-backed test profile has no bucket to downscope to and
     * authenticates nothing, so it reuses the injected helper.
     */
    private GCSClientHelper transferHelper() {
        return bucketName == null ? helper : GCSClientHelper.mintDownscoped(projectId, bucketName);
    }

    public void upload(String bucketName, String objectName, FilePath source) throws IOException, InterruptedException {
        source.act(new GCSUploadCallable(transferHelper(), bucketName, withPrefix(objectName)));
    }

    public void download(String bucketName, String objectName, FilePath target)
            throws IOException, InterruptedException {
        target.act(new GCSDownloadCallable(transferHelper(), bucketName, withPrefix(objectName)));
    }

    public boolean exists(String bucketName, String objectName) {
        Blob blob = helper.storage().get(BlobId.of(bucketName, withPrefix(objectName)));
        return blob != null && blob.exists();
    }

    public void delete(String bucketName, String pathPrefix) {
        String p = withPrefix(pathPrefix);
        List<BlobId> batch = new ArrayList<>();
        for (Blob blob : helper.storage()
                .list(bucketName, Storage.BlobListOption.prefix(p))
                .iterateAll()) {
            if (withinBoundary(blob.getName(), p)) {
                batch.add(blob.getBlobId());
            }
        }
        if (!batch.isEmpty()) {
            helper.storage().delete(batch);
        }
    }

    public void rename(String bucketName, String currentPathPrefix, String newPathPrefix) {
        String from = withPrefix(currentPathPrefix);
        String to = withPrefix(newPathPrefix);
        for (Blob blob : helper.storage()
                .list(bucketName, Storage.BlobListOption.prefix(from))
                .iterateAll()) {
            String name = blob.getName();
            if (!withinBoundary(name, from)) {
                continue;
            }
            String destination = to + name.substring(from.length());
            blob.copyTo(BlobId.of(bucketName, destination)).getResult();
            blob.delete();
        }
    }

    /**
     * True when {@code name} is exactly {@code key} or nested under {@code key/}. A raw GCS prefix
     * list also returns sibling keys that merely share a string prefix (e.g. {@code app} matches
     * {@code application/...}); this restores the path boundary so we never touch another item's cache.
     */
    private static boolean withinBoundary(String name, String key) {
        return name.equals(key) || name.startsWith(key + "/");
    }

    private String withPrefix(String path) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return path;
        }
        return String.format("%s%s", prefix, path);
    }
}
