package dev.stym.tickradar.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class PermissionCacheTest {

    private final Set<String> granted = new HashSet<>(Set.of(Permissions.DISPLAY, Permissions.ALERTS));
    private final AtomicInteger checks = new AtomicInteger();
    private final PermissionCache cache = new PermissionCache(permission -> {
        checks.incrementAndGet();
        return granted.contains(permission);
    });

    @Test
    void theFirstSampleReadsEveryPermission() {
        assertEquals(new Permissions(true, true, false), cache.next());
        assertEquals(3, checks.get());
    }

    @Test
    void eachFollowingSampleReadsOnePermission() {
        cache.next();
        for (int sample = 1; sample <= 30; sample++) {
            cache.next();
            assertEquals(3 + sample, checks.get());
        }
    }

    @Test
    void aRevokedPermissionIsSeenWithinOneRound() {
        for (String permission : Permissions.ALL) {
            for (int offset = 0; offset < PermissionCache.SAMPLES_PER_ROUND; offset++) {
                granted.addAll(Permissions.ALL);
                PermissionCache fresh = new PermissionCache(granted::contains);
                fresh.next();
                for (int i = 0; i < offset; i++) {
                    fresh.next();
                }
                granted.remove(permission);
                int samples = samplesUntil(fresh, p -> !p.has(Permissions.ALL.indexOf(permission)));
                assertTrue(samples >= 1 && samples <= PermissionCache.SAMPLES_PER_ROUND,
                        permission + " revoked after " + offset + " samples, seen after " + samples);
            }
        }
    }

    @Test
    void aGrantedPermissionIsSeenWithinOneRound() {
        cache.next();
        granted.add(Permissions.TELEPORT);
        assertTrue(samplesUntil(cache, Permissions::teleport) <= PermissionCache.SAMPLES_PER_ROUND);
    }

    @Test
    void anExplicitRefreshReadsEverythingAtOnce() {
        cache.next();
        granted.clear();
        assertEquals(new Permissions(false, false, false), cache.refresh());
    }

    @Test
    void unchangedPermissionsKeepTheSameInstance() {
        Permissions first = cache.next();
        for (int sample = 0; sample < 6; sample++) {
            assertSame(first, cache.next());
        }
        assertSame(first, cache.refresh());
    }

    @Test
    void permissionsAreIndexedInTheReadingOrder() {
        Permissions permissions = new Permissions(true, false, true);
        assertTrue(permissions.has(0));
        assertFalse(permissions.has(1));
        assertTrue(permissions.has(2));
        assertEquals(new Permissions(true, true, true), permissions.with(1, true));
        assertEquals(new Permissions(false, false, true), permissions.with(0, false));
        assertEquals(new Permissions(true, false, false), permissions.with(2, false));
        assertThrows(IllegalArgumentException.class, () -> permissions.has(3));
    }

    private static int samplesUntil(PermissionCache cache, Predicate<Permissions> seen) {
        for (int sample = 1; sample <= 10; sample++) {
            if (seen.test(cache.next())) {
                return sample;
            }
        }
        return Integer.MAX_VALUE;
    }
}
