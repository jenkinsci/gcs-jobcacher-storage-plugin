package jenkins.plugins.itemstorage.gcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CacheKeysTest {

    @Test
    void acceptsPlainAndNestedSegments() {
        assertEquals("cache", CacheKeys.requireSafe("cache", "path"));
        assertEquals("feature/foo", CacheKeys.requireSafe("feature/foo", "branch"));
    }

    @Test
    void rejectsTraversalAndSeparatorEdges() {
        assertThrows(IllegalArgumentException.class, () -> CacheKeys.requireSafe("../main", "branch"));
        assertThrows(IllegalArgumentException.class, () -> CacheKeys.requireSafe("a/../b", "branch"));
        assertThrows(IllegalArgumentException.class, () -> CacheKeys.requireSafe("a/./b", "branch"));
        assertThrows(IllegalArgumentException.class, () -> CacheKeys.requireSafe("/leading", "branch"));
        assertThrows(IllegalArgumentException.class, () -> CacheKeys.requireSafe("trailing/", "branch"));
        assertThrows(IllegalArgumentException.class, () -> CacheKeys.requireSafe("a//b", "branch"));
    }

    @Test
    void rejectsEmptyNullAndControlChars() {
        assertThrows(IllegalArgumentException.class, () -> CacheKeys.requireSafe(null, "branch"));
        assertThrows(IllegalArgumentException.class, () -> CacheKeys.requireSafe("", "branch"));
        assertThrows(IllegalArgumentException.class, () -> CacheKeys.requireSafe("main\r\nHost: evil", "branch"));
    }
}
