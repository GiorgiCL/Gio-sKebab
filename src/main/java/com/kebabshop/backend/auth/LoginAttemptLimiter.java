package com.kebabshop.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Optional;

/** A bounded, process-local backstop for failed admin login attempts. */
@Component
public final class LoginAttemptLimiter {
    static final int MAX_FAILED_ATTEMPTS = 10;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration COOLDOWN = Duration.ofMinutes(15);
    static final int MAX_TRACKED_SOURCES = 4096;

    private static final int MAX_FORWARDED_HEADER_LENGTH = 512;
    private static final int MAX_FORWARDED_HEADER_VALUES = 16;
    private static final Duration INVALID_SOURCE_RETRY_AFTER = Duration.ofSeconds(60);
    private static final Duration SATURATED_RETRY_AFTER = Duration.ofSeconds(1);

    private final Clock clock;
    private final int maxTrackedSources;
    private final LinkedHashMap<String, SourceState> sources = new LinkedHashMap<>(16, 0.75f, true);

    public LoginAttemptLimiter() {
        this(Clock.systemUTC(), MAX_TRACKED_SOURCES);
    }

    LoginAttemptLimiter(Clock clock, int maxTrackedSources) {
        if (maxTrackedSources < 1) {
            throw new IllegalArgumentException("maxTrackedSources must be positive");
        }
        this.clock = clock;
        this.maxTrackedSources = maxTrackedSources;
    }

    synchronized Decision begin(HttpServletRequest request) {
        Instant now = clock.instant();
        removeExpired(now);
        Optional<String> source = sourceKey(request);
        if (source.isEmpty()) {
            return Decision.rejected(INVALID_SOURCE_RETRY_AFTER);
        }

        SourceState state = sources.get(source.get());
        if (state == null) {
            if (!makeRoom()) {
                return Decision.rejected(SATURATED_RETRY_AFTER);
            }
            state = new SourceState();
            sources.put(source.get(), state);
        }

        if (state.blockedUntil != null) {
            if (now.isBefore(state.blockedUntil)) {
                return Decision.rejected(Duration.between(now, state.blockedUntil));
            }
            reset(state, now);
        } else if (state.windowStarted == null || !now.isBefore(state.windowStarted.plus(WINDOW))) {
            reset(state, now);
        }

        // Reserve a slot before password verification. Concurrent requests cannot
        // all pass the threshold while their authentication calls are in flight.
        if (state.failures + state.inFlight >= MAX_FAILED_ATTEMPTS) {
            return Decision.rejected(SATURATED_RETRY_AFTER);
        }
        state.inFlight++;
        return Decision.allowed(new Attempt(source.get(), state));
    }

    synchronized void failed(Attempt attempt) {
        SourceState state = complete(attempt);
        Instant now = clock.instant();
        if (state.windowStarted == null || !now.isBefore(state.windowStarted.plus(WINDOW))) {
            reset(state, now);
        }
        state.failures++;
        if (state.failures >= MAX_FAILED_ATTEMPTS) {
            state.blockedUntil = now.plus(COOLDOWN);
        }
    }

    synchronized void succeeded(Attempt attempt) {
        SourceState state = complete(attempt);
        reset(state, clock.instant());
        removeIfIdle(attempt.source(), state);
    }

    synchronized void aborted(Attempt attempt) {
        SourceState state = complete(attempt);
        removeIfIdle(attempt.source(), state);
    }

    Optional<String> sourceKey(HttpServletRequest request) {
        Enumeration<String> values = request.getHeaders("X-Forwarded-For");
        boolean forwardedHeaderPresent = false;
        int totalLength = 0;
        int valueCount = 0;
        String finalHeaderValue = null;

        while (values != null && values.hasMoreElements()) {
            forwardedHeaderPresent = true;
            String value = values.nextElement();
            valueCount++;
            if (value == null || valueCount > MAX_FORWARDED_HEADER_VALUES) {
                return Optional.empty();
            }
            if (value.length() > MAX_FORWARDED_HEADER_LENGTH - totalLength) {
                return Optional.empty();
            }
            totalLength += value.length();
            finalHeaderValue = value;
        }

        if (!forwardedHeaderPresent) {
            return normalizeAddress(request.getRemoteAddr());
        }
        if (finalHeaderValue == null || finalHeaderValue.isBlank()) {
            return Optional.empty();
        }

        int finalSeparator = finalHeaderValue.lastIndexOf(',');
        String finalAddress = finalHeaderValue.substring(finalSeparator + 1).trim();
        return normalizeAddress(finalAddress);
    }

    synchronized int trackedSourceCount() {
        return sources.size();
    }

    private SourceState complete(Attempt attempt) {
        if (attempt.completed()) {
            throw new IllegalStateException("Login attempt has already completed");
        }
        SourceState current = sources.get(attempt.source());
        if (current != attempt.state()) {
            throw new IllegalStateException("Login attempt state is no longer tracked");
        }
        attempt.markCompleted();
        current.inFlight--;
        return current;
    }

    private void removeExpired(Instant now) {
        Iterator<SourceState> iterator = sources.values().iterator();
        while (iterator.hasNext()) {
            SourceState state = iterator.next();
            if (state.inFlight != 0) {
                continue;
            }
            if (state.blockedUntil != null) {
                if (!now.isBefore(state.blockedUntil)) {
                    iterator.remove();
                }
            } else if (state.windowStarted == null || !now.isBefore(state.windowStarted.plus(WINDOW))) {
                iterator.remove();
            }
        }
    }

    private boolean makeRoom() {
        if (sources.size() < maxTrackedSources) {
            return true;
        }
        Iterator<SourceState> iterator = sources.values().iterator();
        while (iterator.hasNext()) {
            SourceState state = iterator.next();
            if (state.inFlight == 0) {
                iterator.remove();
                return true;
            }
        }
        return false;
    }

    private void removeIfIdle(String source, SourceState state) {
        if (state.inFlight == 0 && state.failures == 0 && state.blockedUntil == null) {
            sources.remove(source, state);
        }
    }

    private static void reset(SourceState state, Instant now) {
        state.windowStarted = now;
        state.failures = 0;
        state.blockedUntil = null;
    }

    private static Optional<String> normalizeAddress(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > 45) {
            return Optional.empty();
        }
        String value = candidate.trim();
        String[] octets = value.split("\\.", -1);
        if (octets.length == 4) {
            StringBuilder normalized = new StringBuilder();
            for (String octet : octets) {
                if (octet.length() > 3 || !octet.matches("[0-9]{1,3}")) {
                    return Optional.empty();
                }
                int number;
                try {
                    number = Integer.parseInt(octet);
                } catch (NumberFormatException exception) {
                    return Optional.empty();
                }
                if (number > 255) {
                    return Optional.empty();
                }
                if (!normalized.isEmpty()) {
                    normalized.append('.');
                }
                normalized.append(number);
            }
            return Optional.of(normalized.toString());
        }

        // Only pass numeric IPv6 literals to the JDK parser; never allow DNS lookup
        // from a request header or remote address.
        if (value.indexOf(':') < 0 || !value.matches("[0-9A-Fa-f:.]+")) {
            return Optional.empty();
        }
        try {
            return Optional.of(InetAddress.getByName(value).getHostAddress().toLowerCase(Locale.ROOT));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    record Decision(Attempt attempt, Duration retryAfter) {
        static Decision allowed(Attempt attempt) {
            return new Decision(attempt, Duration.ZERO);
        }

        static Decision rejected(Duration retryAfter) {
            return new Decision(null, retryAfter);
        }

        boolean isAllowed() {
            return attempt != null;
        }
    }

    static final class Attempt {
        private final String source;
        private final SourceState state;
        private boolean completed;

        private Attempt(String source, SourceState state) {
            this.source = source;
            this.state = state;
        }

        String source() {
            return source;
        }

        SourceState state() {
            return state;
        }

        boolean completed() {
            return completed;
        }

        void markCompleted() {
            completed = true;
        }
    }

    private static final class SourceState {
        private Instant windowStarted;
        private Instant blockedUntil;
        private int failures;
        private int inFlight;
    }
}
