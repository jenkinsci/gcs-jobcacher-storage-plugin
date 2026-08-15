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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * A shipped token cannot be refreshed — asking the Google auth library to refresh one throws rather
 * than minting a replacement. These cover the guard that decides whether a shipped token is still
 * worth using, so an expired one falls back to the node's own ADC instead of failing the build.
 */
class GCSClientHelperTest {

    private static long inMillis(Duration offset) {
        return System.currentTimeMillis() + offset.toMillis();
    }

    @Test
    void tokenWithAmpleLifeIsUsable() {
        GCSClientHelper helper = GCSClientHelper.withShippedToken("p", "token", inMillis(Duration.ofMinutes(30)));
        assertTrue(helper.shippedTokenUsable());
    }

    @Test
    void expiredTokenIsNotUsable() {
        GCSClientHelper helper = GCSClientHelper.withShippedToken("p", "token", inMillis(Duration.ofMinutes(-1)));
        assertFalse(helper.shippedTokenUsable());
    }

    @Test
    void tokenExpiringWithinTheSkewIsNotUsable() {
        // Expires shortly: a transfer starting now would race the clock, so treat it as spent.
        GCSClientHelper helper = GCSClientHelper.withShippedToken("p", "token", inMillis(Duration.ofSeconds(5)));
        assertFalse(helper.shippedTokenUsable());
    }

    @Test
    void tokenWithoutAnExpiryIsUsable() {
        // A mint that reported no expiry: nothing to compare against, so let the client use it.
        GCSClientHelper helper = GCSClientHelper.withShippedToken("p", "token", 0L);
        assertTrue(helper.shippedTokenUsable());
    }

    @Test
    void absentTokenIsNotUsable() {
        // The controller could not mint one; the node resolves its own ADC instead.
        GCSClientHelper helper = GCSClientHelper.withShippedToken("p", null, 0L);
        assertFalse(helper.shippedTokenUsable());
    }
}
