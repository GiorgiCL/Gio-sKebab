package com.kebabshop.backend.restaurant;

import com.jayway.jsonpath.JsonPath;
import com.kebabshop.backend.auth.AdminAccountRepository;
import com.kebabshop.backend.auth.AdminProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.CookieManager;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RestaurantHttpSemanticsTests {
    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String url = "jdbc:h2:mem:gio_kebab_http_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> "sa");
        registry.add("spring.flyway.password", () -> "");
    }

    @LocalServerPort int port;
    @Autowired RestaurantProfileRepository profiles;
    @Autowired AdminAccountRepository accounts;
    @Autowired AdminProvisioningService provisioning;

    @Test
    void realHttpKeepsMissingProfileAndScheduleStatuses() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> missingProfile = get(client, "/api/public/restaurant");
            assertEquals(404, missingProfile.statusCode());
            assertTrue(missingProfile.body().contains("Restaurant profile is not configured"));
            assertFalse(missingProfile.body().contains("stackTrace"));
            profiles.saveAndFlush(new RestaurantProfile("Test Restaurant", "Fresh food", "1 Main Street",
                    "+37060000000", null, "https://maps.example.com/test", null, null, null, null));
            assertEquals(503, get(client, "/api/public/opening-status").statusCode());
        }
    }

    @Test
    void realHttpUsesJsonAuthErrorsAndRevokesSessionOnLogout() throws Exception {
        accounts.deleteAll();
        provisioning.createFirstAccount("owner@example.com", "temporary-test-password");
        try (HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build()) {
            var unauthenticated = get(client, "/api/admin/auth/me");
            assertEquals(401, unauthenticated.statusCode());
            assertTrue(unauthenticated.headers().firstValue("Content-Type").orElse("").contains("application/problem+json"));
            assertFalse(unauthenticated.body().contains("<html"));
            var csrf = get(client, "/api/admin/auth/csrf");
            assertEquals(200, csrf.statusCode());
            assertTrue(csrf.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
            String token = JsonPath.read(csrf.body(), "$.token");
            var missingToken = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/admin/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
            var csrfRejection = client.send(missingToken, HttpResponse.BodyHandlers.ofString());
            assertEquals(403, csrfRejection.statusCode());
            assertTrue(csrfRejection.headers().firstValue("Content-Type").orElse("")
                    .contains("application/problem+json"));
            var wrongType = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/admin/auth/login"))
                    .header("Content-Type", "text/plain").header("X-CSRF-TOKEN", token)
                    .POST(HttpRequest.BodyPublishers.ofString("credentials"))
                    .build();
            assertEquals(415, client.send(wrongType, HttpResponse.BodyHandlers.ofString()).statusCode());
            var login = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/admin/auth/login"))
                    .header("Content-Type", "application/json").header("X-CSRF-TOKEN", token)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"email\":\"owner@example.com\",\"password\":\"temporary-test-password\"}"))
                    .build();
            assertEquals(200, client.send(login, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(200, get(client, "/api/admin/auth/me").statusCode());
            var logout = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/admin/auth/logout"))
                    .header("X-CSRF-TOKEN", token).POST(HttpRequest.BodyPublishers.noBody()).build();
            assertEquals(204, client.send(logout, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(401, get(client, "/api/admin/auth/me").statusCode());
        }
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
