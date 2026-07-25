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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.listeners.ItemListener;
import java.io.File;
import jenkins.plugins.itemstorage.GlobalItemStorage;
import jenkins.plugins.itemstorage.ItemStorage;
import jenkins.plugins.itemstorage.ItemStorageDescriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Google Cloud Storage implementation of the jobcacher {@link ItemStorage} extension point.
 *
 * <p>Authentication is Application Default Credentials (ADC) &mdash; Workload Identity on GKE,
 * {@code GOOGLE_APPLICATION_CREDENTIALS}, or gcloud user credentials locally. No HMAC keys and no
 * service-account JSON are configured here.
 */
public class GCSItemStorage extends ItemStorage<GCSObjectPath> {

    private final String bucketName;
    private String prefix;
    private String projectId;

    @DataBoundConstructor
    public GCSItemStorage(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getPrefix() {
        return prefix;
    }

    @DataBoundSetter
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getProjectId() {
        return projectId;
    }

    @DataBoundSetter
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    @Override
    public GCSObjectPath getObjectPath(Item item, String path) {
        return new GCSObjectPath(createProfile(), bucketName, item.getFullName(), CacheKeys.requireSafe(path, "path"));
    }

    @Override
    public GCSObjectPath getObjectPathForBranch(Item item, String path, String branch) {
        // Multibranch jobs share caches across sibling branches under their parent folder. Top-level
        // jobs have no parent (getParent() == null), so key them under their own name instead of a
        // shared "null/" namespace. The branch is attacker-influenceable, so validate it.
        String parent = new File(item.getFullName()).getParent();
        String namespace = (parent == null) ? item.getFullName() : parent;
        String branchPath = namespace + "/" + CacheKeys.requireSafe(branch, "branch");
        return new GCSObjectPath(createProfile(), bucketName, branchPath, CacheKeys.requireSafe(path, "path"));
    }

    private GCSProfile createProfile() {
        return new GCSProfile(projectId, bucketName, prefix);
    }

    @Symbol("googleCloudStorage")
    @Extension
    public static final class DescriptorImpl extends ItemStorageDescriptor<GCSObjectPath> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Google Cloud Storage";
        }
    }

    /**
     * Keeps the cache in GCS in sync with item lifecycle events on the controller.
     */
    @Extension
    public static final class GCSItemListener extends ItemListener {

        @Override
        public void onDeleted(Item item) {
            GCSItemStorage storage = lookupStorage();
            if (storage == null) {
                return;
            }
            storage.createProfile().delete(storage.bucketName, item.getFullName());
        }

        @Override
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            GCSItemStorage storage = lookupStorage();
            if (storage == null) {
                return;
            }
            storage.createProfile().rename(storage.bucketName, oldFullName, newFullName);
        }

        private GCSItemStorage lookupStorage() {
            ItemStorage<?> storage = GlobalItemStorage.get().getStorage();
            return (storage instanceof GCSItemStorage) ? (GCSItemStorage) storage : null;
        }
    }
}
