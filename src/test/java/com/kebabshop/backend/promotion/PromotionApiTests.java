package com.kebabshop.backend.promotion;

import com.jayway.jsonpath.JsonPath;
import com.kebabshop.backend.auth.AdminAccountRepository;
import com.kebabshop.backend.auth.AdminProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PromotionApiTests {
    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String url = "jdbc:h2:mem:gio_kebab_promotions_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.flyway.url", () -> url);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTime {
        @Bean @Primary Clock promotionTestClock() {
            return Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired AdminAccountRepository accounts;
    @Autowired AdminProvisioningService provisioning;
    @Autowired PromotionRepository promotions;

    @BeforeEach
    void reset() {
        promotions.deleteAll();
        accounts.deleteAll();
        provisioning.createFirstAccount("owner@example.com", "temporary-test-password");
    }

    private MockHttpSession ownerSession() throws Exception {
        var result = mvc.perform(post("/api/admin/auth/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"owner@example.com\",\"password\":\"temporary-test-password\"}"))
                .andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }

    private String request(String title, String description, boolean active, String startsAt,
                           String endsAt, int order) {
        return "{\"title\":" + (title == null ? "null" : "\"" + title + "\"")
                + ",\"description\":" + (description == null ? "null" : "\"" + description + "\"")
                + ",\"active\":" + active + ",\"startsAt\":"
                + (startsAt == null ? "null" : "\"" + startsAt + "\"") + ",\"endsAt\":"
                + (endsAt == null ? "null" : "\"" + endsAt + "\"")
                + ",\"displayOrder\":" + order + "}";
    }

    private long create(MockHttpSession session, String title, String description, boolean active,
                        String startsAt, String endsAt, int order) throws Exception {
        var result = mvc.perform(post("/api/admin/promotions").session(session).with(csrf())
                        .contentType("application/json")
                        .content(request(title, description, active, startsAt, endsAt, order)))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void publicPromotionsRespectActiveWindowsOpenBoundsOrderingAndExactBoundaries() throws Exception {
        MockHttpSession session = ownerSession();
        create(session, "No bounds", null, true, null, null, 2);
        create(session, "Starts now", "At inclusive start", true, "2026-09-25T13:00:00", null, 1);
        create(session, "Ends now", null, true, null, "2026-09-25T13:00:00", 0);
        create(session, "Future open-ended", null, true, "2026-09-25T13:00:01", null, 0);
        create(session, "Expired", null, true, null, "2026-09-25T12:59:59", 0);
        create(session, "Inactive", null, false, null, null, 0);
        create(session, "Open start", null, true, null, "2026-09-25T13:00:01", 2);
        create(session, "Open end", null, true, "2026-09-25T12:59:59", null, 3);

        mvc.perform(get("/api/public/promotions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Europe/Vilnius"))
                .andExpect(jsonPath("$.promotions.length()").value(4))
                .andExpect(jsonPath("$.promotions[0].title").value("Starts now"))
                .andExpect(jsonPath("$.promotions[1].title").value("No bounds"))
                .andExpect(jsonPath("$.promotions[2].title").value("Open start"))
                .andExpect(jsonPath("$.promotions[3].title").value("Open end"));
        mvc.perform(post("/api/public/promotions").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCrudValidatesInputAndRequiresAuthenticationAndCsrf() throws Exception {
        MockHttpSession session = ownerSession();
        mvc.perform(post("/api/admin/promotions").with(csrf()).contentType("application/json")
                        .content(request("Promo", null, true, null, null, 0)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/promotions").session(session).contentType("application/json")
                        .content(request("Promo", null, true, null, null, 0)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/promotions").session(session).with(csrf()).contentType("application/json")
                        .content(request(null, null, true, null, null, 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/promotions").session(session).with(csrf()).contentType("application/json")
                        .content(request(" ", null, true, null, null, 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/promotions").session(session).with(csrf()).contentType("application/json")
                        .content(request("x".repeat(161), null, true, null, null, 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/promotions").session(session).with(csrf()).contentType("application/json")
                        .content(request("Promo", "x".repeat(501), true, null, null, 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/promotions").session(session).with(csrf()).contentType("application/json")
                        .content(request("Promo", " ", true, null, null, 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/promotions").session(session).with(csrf()).contentType("application/json")
                        .content(request("Promo", null, true, null, null, -1)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/promotions").session(session).with(csrf()).contentType("application/json")
                        .content(request("Promo", null, true, "2026-09-26T10:00:00", "2026-09-25T10:00:00", 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/promotions").session(session).with(csrf()).contentType("application/json")
                        .content("{\"title\":\"Bad time\",\"active\":true,\"startsAt\":\"not-a-time\",\"displayOrder\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/promotions").session(session).with(csrf()).contentType("application/json")
                        .content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid promotion data"));

        long id = create(session, "Weekend offer", "Short description", true, null, null, 1);
        mvc.perform(get("/api/admin/promotions").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/admin/promotions/" + id).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Weekend offer"));
        mvc.perform(put("/api/admin/promotions/" + id).session(session).with(csrf())
                        .contentType("application/json")
                        .content(request("Updated offer", null, false, "2026-09-26T10:00:00", null, 0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.description").doesNotExist());
        mvc.perform(get("/api/public/promotions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.promotions.length()").value(0));
        mvc.perform(get("/api/admin/promotions/99999").session(session))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/promotions/99999").session(session).with(csrf())
                        .contentType("application/json")
                        .content(request("Updated offer", null, false, null, null, 0)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/promotions/" + id).session(session))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/promotions/" + id).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/promotions/" + id).session(session).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void ambiguousAndNonexistentLocalTimesAreRejectedInConfiguredRestaurantZone() throws Exception {
        MockHttpSession session = ownerSession();
        mvc.perform(post("/api/admin/promotions").session(session).with(csrf()).contentType("application/json")
                        .content(request("Gap", null, true, "2026-03-29T03:30:00", null, 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/promotions").session(session).with(csrf()).contentType("application/json")
                        .content(request("Overlap", null, true, "2026-10-25T03:30:00", null, 0)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/promotions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.promotions.length()").value(0));
    }
}
