package com.kebabshop.backend.restaurant;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
class RestaurantAdminApiTests {
    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String url = "jdbc:h2:mem:gio_kebab_admin_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.flyway.url", () -> url);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTime {
        @Bean @Primary Clock testClock() {
            return Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired AdminAccountRepository accounts;
    @Autowired AdminProvisioningService provisioning;
    @Autowired RestaurantProfileRepository profiles;
    @MockitoSpyBean WeeklyOpeningHoursRepository weekly;
    @Autowired SpecialOpeningHoursRepository special;
    @Autowired RestaurantAdminService admin;

    @BeforeEach
    void reset() {
        special.deleteAll();
        weekly.deleteAll();
        profiles.deleteAll();
        accounts.deleteAll();
        provisioning.createFirstAccount("owner@example.com", "temporary-test-password");
    }

    private MockHttpSession ownerSession() throws Exception {
        var result = mvc.perform(post("/api/admin/auth/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"owner@example.com\",\"password\":\"temporary-test-password\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }

    private String profile(String name, String mapsUrl) {
        return "{\"displayName\":\"" + name + "\",\"description\":\"Fresh food\","
                + "\"address\":\"1 Main Street\",\"phone\":\"+37060000000\","
                + "\"googleMapsUrl\":\"" + mapsUrl + "\"}";
    }

    private String completeWeek(boolean fridayOpen) {
        var days = new StringBuilder("{\"days\":[");
        for (DayOfWeek day : DayOfWeek.values()) {
            if (days.charAt(days.length() - 1) != '[') days.append(',');
            days.append("{\"dayOfWeek\":\"").append(day).append("\",\"open\":")
                    .append(day == DayOfWeek.FRIDAY && fridayOpen);
            if (day == DayOfWeek.FRIDAY && fridayOpen) {
                days.append(",\"openingTime\":\"12:00:00\",\"closingTime\":\"18:00:00\"");
            }
            days.append('}');
        }
        return days.append("]}").toString();
    }

    @Test
    void profileCreateUpdateAndInvalidReplacementPreserveSingleRow() throws Exception {
        MockHttpSession session = ownerSession();
        mvc.perform(put("/api/admin/restaurant").with(csrf()).contentType("application/json")
                        .content(profile("Anonymous", "https://maps.example.com")))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/restaurant").session(session).contentType("application/json")
                        .content(profile("Gio", "https://maps.example.com")))
                .andExpect(status().isForbidden());
        var created = mvc.perform(put("/api/admin/restaurant").session(session).with(csrf())
                        .contentType("application/json").content(profile("Gio", "https://maps.example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Gio"))
                .andReturn();
        String createdAt = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.createdAt");
        mvc.perform(put("/api/admin/restaurant").session(session).with(csrf())
                        .contentType("application/json").content(profile("Gio Updated", "https://maps.example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").value(createdAt));
        mvc.perform(put("/api/admin/restaurant").session(session).with(csrf())
                        .contentType("application/json").content(profile("Bad", "javascript:alert(1)")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/restaurant").session(session).with(csrf())
                        .contentType("application/json").content(profile("", "https://maps.example.com")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/restaurant"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Gio Updated"));
        assertEquals(1, profiles.count());
    }

    @Test
    void weeklyReplacementRejectsIncompleteDuplicateAndInvalidRulesWithoutChangingPublishedHours() throws Exception {
        MockHttpSession session = ownerSession();
        mvc.perform(put("/api/admin/restaurant").session(session).with(csrf())
                .contentType("application/json").content(profile("Gio", "https://maps.example.com")))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/admin/opening-hours/weekly").session(session).with(csrf())
                        .contentType("application/json").content(completeWeek(true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));
        mvc.perform(get("/api/public/opening-status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.openNow").value(true));

        String missing = completeWeek(true).replaceFirst("\\{\"dayOfWeek\":\"SUNDAY\"[^}]*}", "");
        missing = missing.replace(",]", "]");
        mvc.perform(put("/api/admin/opening-hours/weekly").session(session).with(csrf())
                        .contentType("application/json").content(missing))
                .andExpect(status().isBadRequest());
        String duplicate = completeWeek(true).replace("\"SUNDAY\"", "\"MONDAY\"");
        mvc.perform(put("/api/admin/opening-hours/weekly").session(session).with(csrf())
                        .contentType("application/json").content(duplicate))
                .andExpect(status().isBadRequest());
        String overnight = completeWeek(true).replace("\"closingTime\":\"18:00:00\"",
                "\"closingTime\":\"02:00:00\"");
        mvc.perform(put("/api/admin/opening-hours/weekly").session(session).with(csrf())
                        .contentType("application/json").content(overnight))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/opening-status"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.openNow").value(true));
        assertEquals(7, weekly.count());
        mvc.perform(put("/api/admin/opening-hours/weekly").session(session).with(csrf())
                        .contentType("application/json").content(completeWeek(false)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/public/opening-status"))
                .andExpect(jsonPath("$.closedToday").value(true));
    }

    @Test
    void specialDateCreateReplaceDeleteAndConflictChangePublicPrecedence() throws Exception {
        MockHttpSession session = ownerSession();
        mvc.perform(put("/api/admin/restaurant").session(session).with(csrf())
                .contentType("application/json").content(profile("Gio", "https://maps.example.com")))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/admin/opening-hours/weekly").session(session).with(csrf())
                .contentType("application/json").content(completeWeek(true)))
                .andExpect(status().isOk());
        String closed = "{\"date\":\"2026-09-25\",\"open\":false}";
        mvc.perform(post("/api/admin/opening-hours/special-dates").session(session).with(csrf())
                .contentType("application/json").content(closed))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/opening-hours/special-dates").session(session).with(csrf())
                .contentType("application/json").content(closed))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/opening-hours/special-dates").with(csrf())
                .contentType("application/json").content(closed))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/opening-hours/special-dates").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/public/opening-status"))
                .andExpect(jsonPath("$.source").value("SPECIAL"))
                .andExpect(jsonPath("$.closedToday").value(true));
        mvc.perform(put("/api/admin/opening-hours/special-dates/2026-09-25").session(session).with(csrf())
                .contentType("application/json").content("{\"open\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/opening-status"))
                .andExpect(jsonPath("$.closedToday").value(true));
        mvc.perform(put("/api/admin/opening-hours/special-dates/2026-09-26").session(session).with(csrf())
                .contentType("application/json").content("{\"open\":false}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/opening-hours/special-dates/2026-09-25").session(session).with(csrf())
                .contentType("application/json")
                .content("{\"open\":true,\"openingTime\":\"12:00:00\",\"closingTime\":\"14:00:00\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/public/opening-status"))
                .andExpect(jsonPath("$.source").value("SPECIAL"))
                .andExpect(jsonPath("$.openNow").value(true));
        mvc.perform(delete("/api/admin/opening-hours/special-dates/2026-09-25").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/opening-hours/special-dates/2026-09-25").session(session).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/public/opening-status"))
                .andExpect(jsonPath("$.source").value("WEEKLY"));
    }

    @Test
    void everyMutationRequiresCsrfAndMalformedInputStaysAClientError() throws Exception {
        MockHttpSession session = ownerSession();
        mvc.perform(put("/api/admin/opening-hours/weekly").session(session)
                        .contentType("application/json").content(completeWeek(true)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/opening-hours/special-dates").session(session)
                        .contentType("application/json").content("{\"date\":\"2026-09-25\",\"open\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/opening-hours/special-dates/2026-09-25").session(session)
                        .contentType("application/json").content("{\"open\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/opening-hours/special-dates/2026-09-25").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/restaurant").session(session).with(csrf())
                        .contentType("application/json").content("{bad json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid restaurant or opening-hours data"));
        mvc.perform(delete("/api/admin/opening-hours/special-dates/not-a-date").session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid restaurant or opening-hours data"));
        assertEquals(0, profiles.count());
        assertEquals(0, weekly.count());
        assertEquals(0, special.count());
    }

    @Test
    void failedPersistenceRollsBackWeeklyDeletion() throws Exception {
        MockHttpSession session = ownerSession();
        mvc.perform(put("/api/admin/opening-hours/weekly").session(session).with(csrf())
                        .contentType("application/json").content(completeWeek(true)))
                .andExpect(status().isOk());
        doThrow(new IllegalStateException("simulated persistence failure"))
                .when(weekly).saveAllAndFlush(any());
        var closedWeek = java.util.Arrays.stream(DayOfWeek.values())
                .map(day -> new WeeklyDayRequest(day, false, null, null)).toList();
        assertThrows(IllegalStateException.class,
                () -> admin.replaceWeekly(new WeeklyScheduleRequest(closedWeek)));
        assertEquals(7, weekly.count());
        assertEquals(true, weekly.findById(DayOfWeek.FRIDAY).orElseThrow().isOpen());
    }
}
