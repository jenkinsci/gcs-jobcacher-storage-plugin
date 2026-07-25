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
import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;

/**
 * Downloads a single cache archive from a GCS object to the agent workspace.
 */
public class GCSDownloadCallable extends GCSCallable<Void> {

    private static final long serialVersionUID = 1L;

    private final String bucketName;

    @SuppressWarnings("lgtm[jenkins/plaintext-storage]") // Object name, not a secret.
    private final String objectName;

    public GCSDownloadCallable(GCSClientHelper helper, String bucketName, String objectName) {
        super(helper);
        this.bucketName = bucketName;
        this.objectName = objectName;
    }

    @Override
    public Void invoke(File target, VirtualChannel channel) throws IOException {
        Blob blob = storage().get(BlobId.of(bucketName, objectName));
        if (blob == null || !blob.exists()) {
            throw new IOException("GCS object not found: gs://" + bucketName + "/" + objectName);
        }
        blob.downloadTo(target.toPath());
        return null;
    }
}
