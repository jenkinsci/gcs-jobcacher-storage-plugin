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

/**
 * Validation for the untrusted, edge-case-prone components (branch, cache path) that make up a GCS
 * cache object key.
 *
 * <p>The object-key prefix is the isolation boundary between different jobs'/branches' caches, so a
 * component that can climb out of its namespace enables cross-job cache poisoning. Nested segments
 * separated by {@code /} are allowed (branch names like {@code feature/x} are legitimate), but empty,
 * {@code .}, {@code ..} and leading/trailing separators are rejected, as are control characters
 * (which would also allow header injection in the {@code browse} redirect).
 */
final class CacheKeys {

    private CacheKeys() {}

    static String requireSafe(String value, String what) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(what + " must not be empty");
        }
        if (value.startsWith("/") || value.endsWith("/")) {
            throw new IllegalArgumentException(what + " must not start or end with '/': " + value);
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < 0x20) {
                throw new IllegalArgumentException(what + " must not contain control characters");
            }
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(what + " contains an illegal path segment: " + value);
            }
        }
        return value;
    }
}
