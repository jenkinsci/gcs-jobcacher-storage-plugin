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

import hudson.FilePath;
import hudson.model.Job;
import java.io.IOException;
import jenkins.plugins.itemstorage.ObjectPath;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * {@link ObjectPath} backed by a Google Cloud Storage object.
 */
public class GCSObjectPath extends ObjectPath {

    private final GCSProfile profile;
    private final String bucketName;
    private final String fullName;
    private final String path;

    public GCSObjectPath(GCSProfile profile, String bucketName, String fullName, String path) {
        this.profile = profile;
        this.bucketName = bucketName;
        this.fullName = fullName;
        this.path = path;
    }

    @Override
    public GCSObjectPath child(String childPath) throws IOException, InterruptedException {
        return new GCSObjectPath(profile, bucketName, fullName, path + "/" + CacheKeys.requireSafe(childPath, "path"));
    }

    @Override
    public void copyTo(FilePath target) throws IOException, InterruptedException {
        profile.download(bucketName, fullName + "/" + path, target);
    }

    @Override
    public void copyFrom(FilePath source) throws IOException, InterruptedException {
        profile.upload(bucketName, fullName + "/" + path, source);
    }

    @Override
    public boolean exists() throws IOException, InterruptedException {
        return profile.exists(bucketName, fullName + "/" + path);
    }

    @Override
    public void deleteRecursive() throws IOException, InterruptedException {
        profile.delete(bucketName, fullName + "/" + path);
    }

    @Override
    public HttpResponse browse(StaplerRequest2 request, StaplerResponse2 response, Job<?, ?> job, String name)
            throws IOException {
        // Forward to the Cloud Console object browser scoped to this cache's prefix.
        response.sendRedirect2(
                "https://console.cloud.google.com/storage/browser/" + bucketName + "/" + fullName + "/" + path + "/");
        return null;
    }
}
