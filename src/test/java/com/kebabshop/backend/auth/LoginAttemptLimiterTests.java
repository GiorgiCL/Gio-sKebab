package com.kebabshop.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptLimiterTests {

    @Test
    void cooldownExpiresAndAllowsAuthenticationAgain() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock, 16);
        HttpServletRequest request = fromAddress("203.0.113.10");

        fail(limiter, request, LoginAttemptLimiter.MAX_FAILED_ATTEMPTS);
        var blocked = limiter.begin(request);
        assertFalse(blocked.isAllowed());
        assertEquals(LoginAttemptLimiter.COOLDOWN, blocked.retryAfter());

        clock.advance(LoginAttemptLimiter.COOLDOWN);
        var afterCooldown = limiter.begin(request);
        assertTrue(afterCooldown.isAllowed());
        limiter.aborted(afterCooldown.attempt());
    }

    @Test
    void successfulAuthenticationClearsTheSourceFailureWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock, 16);
        HttpServletRequest request = fromAddress("203.0.113.11");

        fail(limiter, request, LoginAttemptLimiter.MAX_FAILED_ATTEMPTS - 1);
        var successful = limiter.begin(request);
        assertTrue(successful.isAllowed());
        limiter.succeeded(successful.attempt());

        fail(limiter, request, LoginAttemptLimiter.MAX_FAILED_ATTEMPTS);
        assertFalse(limiter.begin(request).isAllowed());
    }

    @Test
    void usesOnlyKoyebAppendedRightmostAddressAndIgnoresCloudflareHeader() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC(), 16);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.1, 203.0.113.20");
        assertEquals("203.0.113.20", limiter.sourceKey(request).orElseThrow());

        request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "192.0.2.99, 203.0.113.20");
        assertEquals("203.0.113.20", limiter.sourceKey(request).orElseThrow());

        request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.1, 203.0.113.21");
        assertEquals("203.0.113.21", limiter.sourceKey(request).orElseThrow());

        request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.22");
        request.addHeader("CF-Connecting-IP", "198.51.100.88");
        assertEquals("203.0.113.22", limiter.sourceKey(request).orElseThrow());
    }

    @Test
    void fallsBackToRemoteAddressOnlyWhenForwardedHeaderIsAbsent() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC(), 16);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.23");

        assertEquals("203.0.113.23", limiter.sourceKey(request).orElseThrow());

        request.addHeader("X-Forwarded-For", " ");
        assertTrue(limiter.sourceKey(request).isEmpty());
    }

    @Test
    void malformedOrHostileForwardingInputFailsClosed() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC(), 16);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.24");
        request.addHeader("X-Forwarded-For", "198.51.100.1, not-an-ip");
        assertTrue(limiter.sourceKey(request).isEmpty());

        request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.24");
        request.addHeader("X-Forwarded-For", "x".repeat(513) + ", 203.0.113.24");
        assertTrue(limiter.sourceKey(request).isEmpty());

        request = new MockHttpServletRequest();
        request.setRemoteAddr("not-an-ip");
        assertTrue(limiter.sourceKey(request).isEmpty());
    }

    @Test
    void evictsOldIdleSourcesAndNeverExceedsConfiguredCapacity() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(
                new MutableClock(Instant.parse("2026-01-01T00:00:00Z")), 2);
        HttpServletRequest first = fromAddress("203.0.113.30");
        HttpServletRequest second = fromAddress("203.0.113.31");
        HttpServletRequest third = fromAddress("203.0.113.32");

        fail(limiter, first, 1);
        fail(limiter, second, 1);
        fail(limiter, third, 1);

        assertEquals(2, limiter.trackedSourceCount());
        var firstAgain = limiter.begin(first);
        assertTrue(firstAgain.isAllowed()); // The oldest idle entry was evicted.
        limiter.aborted(firstAgain.attempt());
    }

    @Test
    void concurrentRequestsCannotReserveMoreThanTheAttemptThreshold() throws Exception {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(
                new MutableClock(Instant.parse("2026-01-01T00:00:00Z")), 16);
        int requests = 24;
        var pool = Executors.newFixedThreadPool(requests);
        var ready = new CountDownLatch(requests);
        var start = new CountDownLatch(1);
        var allowed = new AtomicInteger();
        var rejected = new AtomicInteger();
        try {
            for (int index = 0; index < requests; index++) {
                pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    var decision = limiter.begin(fromAddress("203.0.113.40"));
                    if (decision.isAllowed()) {
                        allowed.incrementAndGet();
                        limiter.failed(decision.attempt());
                    } else {
                        rejected.incrementAndGet();
                    }
                    return null;
                });
            }
            ready.await();
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(LoginAttemptLimiter.MAX_FAILED_ATTEMPTS, allowed.get());
        assertEquals(requests - LoginAttemptLimiter.MAX_FAILED_ATTEMPTS, rejected.get());
        assertFalse(limiter.begin(fromAddress("203.0.113.40")).isAllowed());
    }

    private static void fail(LoginAttemptLimiter limiter, HttpServletRequest request, int count) {
        for (int index = 0; index < count; index++) {
            var decision = limiter.begin(request);
            assertTrue(decision.isAllowed());
            limiter.failed(decision.attempt());
        }
    }

    private static MockHttpServletRequest fromAddress(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        return request;
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public synchronized Instant instant() {
            return now;
        }

        synchronized void advance(Duration duration) {
            now = now.plus(duration);
        }
    }
}
