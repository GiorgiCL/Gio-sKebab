package com.kebabshop.backend.auth;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.context-path=/f01", "server.address=127.0.0.1"})
@ActiveProfiles("test")
@Import(AdminSessionPathHttpTests.FixtureConfiguration.class)
class AdminSessionPathHttpTests {
    private static final String[] ADMIN_PATHS = {
            "/api/admin/auth/me", "/api/%61dmin/auth/me", "/%61pi/admin/auth/me",
            "/api/ad%6din/auth/me", "/api/ad%6Din/auth/me", "/api/admin/auth/%6de"
    };

    enum SessionState { LIVE, DISABLED, DELETED, EXPIRED }

    @LocalServerPort int port;
    @Autowired AdminAccountRepository accounts;
    @Autowired AdminProvisioningService provisioning;
    @Autowired SessionFixture fixture;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String url = "jdbc:h2:mem:f01_path_http_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> "sa");
        registry.add("spring.flyway.password", () -> "");
    }

    @BeforeEach
    void prepareAccount() {
        accounts.deleteAll();
        provisioning.createFirstAccount("owner@example.com", "temporary-test-password");
        fixture.session.set(null);
        fixture.meDispatches.set(0);
        fixture.meRequestUri.set(null);
    }

    static Stream<Arguments> lifecycleCases() {
        return Stream.of(SessionState.values()).flatMap(state ->
                Stream.of(ADMIN_PATHS).map(path -> Arguments.of(state, path)));
    }

    @ParameterizedTest
    @MethodSource("lifecycleCases")
    void allAdminPathSpellingsEnforceTheSameLifecycle(SessionState state, String path) throws Exception {
        try (HttpClient client = client()) {
            login(client, "/api/admin/auth/login");
            HttpSession session = captureSession(client);
            revoke(state, session);

            assertEquals(state == SessionState.LIVE ? 200 : 401, get(client, path).statusCode());
            assertEquals(state == SessionState.LIVE ? 1 : 0, fixture.meDispatches.get());
            if (state == SessionState.LIVE) {
                assertEquals("/f01" + path, fixture.meRequestUri.get());
                assertNotNull(session.getAttribute(AdminAuthController.REMEMBER_UNTIL));
            } else {
                assertThrows(IllegalStateException.class, () -> session.getAttribute(AdminAuthController.REMEMBER_UNTIL));
                assertEquals(401, get(client, "/api/admin/auth/me").statusCode());
                assertEquals(0, fixture.meDispatches.get());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin/auth/me", "/api/%61dmin/auth/me", "/%61pi/admin/auth/me",
            "/api/ad%6din/auth/me", "/api/ad%6Din/auth/me", "/api/admin/auth/%6de"})
    void alternativeSpellingsNeverAuthenticateAnAnonymousCaller(String path) throws Exception {
        try (HttpClient client = client()) {
            assertEquals(401, get(client, path).statusCode());
            assertEquals(0, fixture.meDispatches.get());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/./admin/auth/me", "/api/public/../admin/auth/me",
            "/api//admin/auth/me", "/api/%2e/admin/auth/me", "/api%2fadmin/auth/me",
            "/api/%2561dmin/auth/me"})
    void normalizedOrRejectedPathsCannotReviveARevokedSession(String path) throws Exception {
        try (HttpClient client = client()) {
            login(client, "/api/admin/auth/login");
            HttpSession session = captureSession(client);
            revoke(SessionState.DISABLED, session);
            int status = get(client, path).statusCode();
            assertTrue(status == 400 || status == 401 || status == 403,
                    "Path must be rejected or lifecycle-checked; status=" + status);
            assertEquals(0, fixture.meDispatches.get());
            assertEquals(401, get(client, "/api/admin/auth/me").statusCode());
            assertThrows(IllegalStateException.class, () -> session.getAttribute(AdminAuthController.REMEMBER_UNTIL));
        }
    }

    @Test
    void publicReadsAndEncodedAuthenticationExceptionsKeepTheirIntendedBoundaries() throws Exception {
        try (HttpClient client = client()) {
            for (String path : new String[]{"/api/public/restaurant", "/api/public/opening-hours",
                    "/api/public/opening-status", "/api/public/menu", "/api/public/lunch-menu",
                    "/api/public/promotions", "/api/%70ublic/menu"}) {
                int status = get(client, path).statusCode();
                assertTrue(status == 200 || status == 404 || status == 503, "Public read must reach its handler");
            }
            assertEquals(403, post(client, "/api/%61dmin/auth/login", null).statusCode());
            String token = login(client, "/api/%61dmin/auth/login");
            HttpSession session = captureSession(client);
            revoke(SessionState.EXPIRED, session);
            assertEquals(200, get(client, "/api/%61dmin/auth/csrf").statusCode());
            assertEquals(200, get(client, "/api/public/menu").statusCode());
            assertNotNull(session.getAttribute(AdminAuthController.REMEMBER_UNTIL));
            // Login stays available even when an old remembered deadline has passed.
            assertEquals(200, post(client, "/api/%61dmin/auth/login", token).statusCode());
            session = captureSession(client);
            assertEquals(200, get(client, "/api/%61dmin/auth/me").statusCode());
            assertEquals(403, post(client, "/api/admin/auth/logout", null).statusCode());
            token = JsonPath.read(get(client, "/api/admin/auth/csrf").body(), "$.token");
            assertEquals(204, post(client, "/api/admin/auth/logout", token).statusCode());
            HttpSession loggedOut = session;
            assertThrows(IllegalStateException.class, () -> loggedOut.getAttribute(AdminAuthController.REMEMBER_UNTIL));
            assertEquals(401, get(client, "/api/%61dmin/auth/me").statusCode());
        }
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager()).connectTimeout(Duration.ofSeconds(5)).build();
    }

    private String login(HttpClient client, String path) throws Exception {
        var csrf = get(client, "/api/admin/auth/csrf");
        assertEquals(200, csrf.statusCode());
        String token = JsonPath.read(csrf.body(), "$.token");
        assertEquals(200, post(client, path, token).statusCode());
        return token;
    }

    private HttpSession captureSession(HttpClient client) throws Exception {
        assertEquals(204, get(client, "/api/admin/f01-fixture/session").statusCode());
        assertNotNull(fixture.session.get());
        fixture.meDispatches.set(0);
        return fixture.session.get();
    }

    private void revoke(SessionState state, HttpSession session) {
        switch (state) {
            case LIVE -> { }
            case DISABLED -> {
                var account = accounts.findById((short) 1).orElseThrow();
                account.setEnabled(false);
                accounts.saveAndFlush(account);
            }
            case DELETED -> accounts.deleteAll();
            case EXPIRED -> session.setAttribute(AdminAuthController.REMEMBER_UNTIL, Instant.now().minusSeconds(1));
        }
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(HttpClient client, String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json");
        if (token != null) request.header("X-CSRF-TOKEN", token);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(
                "{\"email\":\"owner@example.com\",\"password\":\"temporary-test-password\",\"rememberMe\":true}")).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + "/f01" + path); }

    @RestController
    @TestComponent
    static class SessionFixture {
        final AtomicReference<HttpSession> session = new AtomicReference<>();
        final AtomicInteger meDispatches = new AtomicInteger();
        final AtomicReference<String> meRequestUri = new AtomicReference<>();

        @GetMapping("/api/admin/f01-fixture/session")
        @ResponseStatus(NO_CONTENT)
        void capture(HttpServletRequest request) { session.set(request.getSession(false)); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixtureConfiguration {
        @Bean SessionFixture sessionFixture() { return new SessionFixture(); }

        @Bean
        WebMvcConfigurer dispatchTracking(SessionFixture fixture) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(new HandlerInterceptor() {
                        @Override
                        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                            if (handler instanceof HandlerMethod method && method.getBeanType() == AdminAuthController.class
                                    && method.getMethod().getName().equals("me")) {
                                fixture.meDispatches.incrementAndGet();
                                fixture.meRequestUri.set(request.getRequestURI());
                            }
                            return true;
                        }
                    });
                }
            };
        }
    }
}
