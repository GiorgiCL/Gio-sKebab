package com.kebabshop.backend.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestFailureLoggingFilterTests {
    @Test
    void logsOnlyNormalizedRouteAndNeverQueryOrExceptionMessage() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestFailureLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/menu/123");
            request.setQueryString("token=do-not-log");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/public/menu/{id}");
            MockHttpServletResponse response = new MockHttpServletResponse();
            new RequestFailureLoggingFilter().doFilter(request, response,
                    (incoming, outgoing) -> ((MockHttpServletResponse) outgoing).setStatus(500));

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                    .contains("method=GET", "route=/api/public/menu/{id}", "status=500")
                    .doesNotContain("123", "token", "do-not-log");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void expectedMissingHoursDoesNotCreateFailureLog() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestFailureLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/opening-hours");
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/public/opening-hours");
            new RequestFailureLoggingFilter().doFilter(request, new MockHttpServletResponse(),
                    (incoming, outgoing) -> ((MockHttpServletResponse) outgoing).setStatus(503));
            assertThat(appender.list).isEmpty();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void thrownFailureLogsClassWithoutExceptionText() {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestFailureLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/images/private-file.png");
            assertThatThrownBy(() -> new RequestFailureLoggingFilter().doFilter(request, new MockHttpServletResponse(),
                    (incoming, outgoing) -> { throw new ServletException("password=do-not-log", new IllegalStateException("private detail")); }))
                    .isInstanceOf(ServletException.class);
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                    .contains("route=unmatched", "exception=IllegalStateException")
                    .doesNotContain("private-file", "password", "do-not-log", "private detail");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
