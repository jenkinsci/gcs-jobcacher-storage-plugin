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

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;

/**
 * Uploads a single cache archive from the agent workspace to a GCS object using a resumable upload.
 */
public class GCSUploadCallable extends GCSCallable<Void> {

    private static final long serialVersionUID = 1L;

    private final String bucketName;

    @SuppressWarnings("lgtm[jenkins/plaintext-storage]") // Object name, not a secret.
    private final String objectName;

    public GCSUploadCallable(GCSClientHelper helper, String bucketName, String objectName) {
        super(helper);
        this.bucketName = bucketName;
        this.objectName = objectName;
    }

    @Override
    public Void invoke(File source, VirtualChannel channel) throws IOException {
        if (!source.exists()) {
            return null;
        }
        BlobInfo blobInfo =
                BlobInfo.newBuilder(BlobId.of(bucketName, objectName)).build();
        // createFrom performs a chunked resumable upload; suited to large caches. No precondition:
        // re-running a build may legitimately overwrite the cache object for the same key.
        storage().createFrom(blobInfo, source.toPath());
        return null;
    }
}
