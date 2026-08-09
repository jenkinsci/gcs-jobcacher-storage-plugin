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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import hudson.FilePath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Exercises {@link GCSProfile} against a real GCS emulator, covering the operations jobcacher
 * drives: upload, existence check, download, rename on job move, and delete on job removal.
 *
 * <p>Skipped automatically when no Docker daemon is available, so {@code mvn verify} still passes
 * on machines and CI agents without one.
 */
@Testcontainers(disabledWithoutDocker = true)
class GCSProfileEmulatorTest {

    private static final String BUCKET = "jenkins-cache-test";
    private static final String PREFIX = "jenkins/caches/";
    private static final int GCS_PORT = 4443;

    @Container
    private static final GenericContainer<?> FAKE_GCS = new GenericContainer<>(
                    DockerImageName.parse("fsouza/fake-gcs-server:1.49"))
            .withExposedPorts(GCS_PORT)
            .withCommand("-scheme", "http", "-backend", "memory", "-port", String.valueOf(GCS_PORT));

    @TempDir
    private Path tmp;

    private GCSProfile profile;

    private static String endpoint() {
        return "http://" + FAKE_GCS.getHost() + ":" + FAKE_GCS.getMappedPort(GCS_PORT);
    }

    @BeforeEach
    void setUp() throws Exception {
        // fake-gcs-server builds resumable-upload session URLs from its configured external URL.
        // Left at the default it advertises its in-container address, and createFrom() -- which
        // performs a resumable upload -- would fail from the host.
        HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(endpoint() + "/_internal/config"))
                                .header("Content-Type", "application/json")
                                .PUT(HttpRequest.BodyPublishers.ofString("{\"externalUrl\":\"" + endpoint() + "\"}"))
                                .build(),
                        HttpResponse.BodyHandlers.discarding());

        GCSClientHelper helper = GCSClientHelper.forHost(endpoint());
        Storage storage = helper.storage();
        if (storage.get(BUCKET) == null) {
            storage.create(BucketInfo.of(BUCKET));
        }
        profile = new GCSProfile(helper, PREFIX);
    }

    private FilePath localFile(String name, String content) throws Exception {
        Path file = tmp.resolve(name);
        Files.createDirectories(file.getParent() == null ? tmp : file.getParent());
        Files.writeString(file, content);
        return new FilePath(file.toFile());
    }

    @Test
    void roundTripsACacheThroughTheBucket() throws Exception {
        profile.upload(BUCKET, "my-job/cache.tgz", localFile("cache.tgz", "cache-payload"));

        assertTrue(profile.exists(BUCKET, "my-job/cache.tgz"), "uploaded object should exist");

        Path restored = tmp.resolve("restored.tgz");
        profile.download(BUCKET, "my-job/cache.tgz", new FilePath(restored.toFile()));

        assertEquals("cache-payload", Files.readString(restored));
    }

    @Test
    void reportsMissingObjectsAsAbsent() {
        assertFalse(profile.exists(BUCKET, "no-such-job/cache.tgz"));
    }

    @Test
    void deleteRemovesOnlyTheItemsOwnCaches() throws Exception {
        profile.upload(BUCKET, "doomed/cache.tgz", localFile("a.tgz", "a"));
        profile.upload(BUCKET, "doomed-sibling/cache.tgz", localFile("b.tgz", "b"));

        profile.delete(BUCKET, "doomed");

        assertFalse(profile.exists(BUCKET, "doomed/cache.tgz"), "the item's own cache should be gone");
        assertTrue(
                profile.exists(BUCKET, "doomed-sibling/cache.tgz"),
                "a sibling sharing the name as a string prefix must survive");
    }

    @Test
    void renameMovesCachesToTheNewItemPath() throws Exception {
        profile.upload(BUCKET, "old-name/cache.tgz", localFile("c.tgz", "moved"));

        profile.rename(BUCKET, "old-name", "new-name");

        assertFalse(profile.exists(BUCKET, "old-name/cache.tgz"));
        assertTrue(profile.exists(BUCKET, "new-name/cache.tgz"));

        Path restored = tmp.resolve("moved.tgz");
        profile.download(BUCKET, "new-name/cache.tgz", new FilePath(restored.toFile()));
        assertEquals("moved", Files.readString(restored));
    }
}
