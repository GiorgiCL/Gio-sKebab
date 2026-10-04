package com.kebabshop.backend.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Set;

@Component
public class RequestFailureLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestFailureLoggingFilter.class);
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String exceptionClass = "HTTP_5XX";
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            Throwable cause = exception.getCause();
            exceptionClass = (cause == null ? exception : cause).getClass().getSimpleName();
            throw exception;
        } finally {
            int status = response.getStatus();
            if (status >= 500 || !"HTTP_5XX".equals(exceptionClass)) {
                String route = safeRoute(request);
                if (!expectedUnconfiguredHours(status, route)) {
                    String method = METHODS.contains(request.getMethod()) ? request.getMethod() : "OTHER";
                    int failureStatus = status >= 500 ? status : 500;
                    log.error("Backend request failed method={} route={} status={} exception={}", method, route, failureStatus, exceptionClass);
                }
            }
        }
    }

    static String safeRoute(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String route && route.startsWith("/api/") && route.length() <= 120) return route;
        return "unmatched";
    }

    private static boolean expectedUnconfiguredHours(int status, String route) {
        return status == 503 && (route.equals("/api/public/opening-hours") || route.equals("/api/public/opening-status"));
    }
}
