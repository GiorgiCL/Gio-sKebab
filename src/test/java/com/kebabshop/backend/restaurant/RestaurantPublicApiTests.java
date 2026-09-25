package com.kebabshop.backend.restaurant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RestaurantPublicApiTests {
    private static final LocalDate FRIDAY = LocalDate.of(2026, 9, 25);

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String url = "jdbc:h2:mem:gio_kebab_restaurant_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> "sa");
        registry.add("spring.flyway.password", () -> "");
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    static class MutableClock extends Clock {
        private Instant current = Instant.parse("2026-09-25T10:00:00Z");

        void set(Instant instant) { current = instant; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(current, zone); }
        @Override public Instant instant() { return current; }
    }

    @Autowired MockMvc mvc;
    @Autowired RestaurantProfileRepository profiles;
    @Autowired WeeklyOpeningHoursRepository weekly;
    @Autowired SpecialOpeningHoursRepository special;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetData() {
        special.deleteAll();
        weekly.deleteAll();
        profiles.deleteAll();
        clock.set(Instant.parse("2026-09-25T10:00:00Z"));
    }

    private void profile() {
        profiles.saveAndFlush(new RestaurantProfile("Test Restaurant", "Fresh food", "1 Main Street",
                "+37060000000", "owner@example.com", "https://maps.example.com/test",
                "https://wolt.example.com/test", "https://bolt.example.com/test", null, null));
    }

    private void closedWeek() {
        weekly.saveAllAndFlush(Arrays.stream(DayOfWeek.values())
                .map(day -> new WeeklyOpeningHours(day, false, null, null)).toList());
    }

    private void openFriday(LocalTime opening, LocalTime closing) {
        weekly.saveAndFlush(new WeeklyOpeningHours(DayOfWeek.FRIDAY, true, opening, closing));
    }

    @Test
    void profileIsPublicAndUsesDto() throws Exception {
        profile();
        mvc.perform(get("/api/public/restaurant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Test Restaurant"))
                .andExpect(jsonPath("$.woltUrl").value("https://wolt.example.com/test"))
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void weeklyAndUpcomingSpecialHoursArePublic() throws Exception {
        profile(); closedWeek();
        openFriday(LocalTime.of(12, 0), LocalTime.of(18, 0));
        special.saveAndFlush(new SpecialOpeningHours(FRIDAY.plusDays(1), false, null, null));
        mvc.perform(get("/api/public/opening-hours"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Europe/Vilnius"))
                .andExpect(jsonPath("$.weekly.length()").value(7))
                .andExpect(jsonPath("$.weekly[4].dayOfWeek").value("FRIDAY"))
                .andExpect(jsonPath("$.weekly[4].openingTime").value("12:00:00"))
                .andExpect(jsonPath("$.specialDates[0].date").value("2026-09-26"));
    }

    @Test
    void normalOpenDayUsesLocalTimeAndClosingIsExclusive() throws Exception {
        profile(); closedWeek(); openFriday(LocalTime.of(12, 0), LocalTime.of(18, 0));
        clock.set(Instant.parse("2026-09-25T09:00:00Z"));
        mvc.perform(get("/api/public/opening-status"))
                .andExpect(jsonPath("$.openNow").value(true));
        clock.set(Instant.parse("2026-09-25T10:00:00Z"));
        mvc.perform(get("/api/public/opening-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openNow").value(true))
                .andExpect(jsonPath("$.localTime").value("13:00:00"))
                .andExpect(jsonPath("$.source").value("WEEKLY"));
        clock.set(Instant.parse("2026-09-25T15:00:00Z"));
        mvc.perform(get("/api/public/opening-status"))
                .andExpect(jsonPath("$.openNow").value(false))
                .andExpect(jsonPath("$.closedToday").value(false));
    }

    @Test
    void normalClosedDayIsReported() throws Exception {
        profile(); closedWeek();
        mvc.perform(get("/api/public/opening-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedToday").value(true))
                .andExpect(jsonPath("$.openNow").value(false));
    }

    @Test
    void specialOpenDateReplacesClosedWeeklyDay() throws Exception {
        profile(); closedWeek();
        special.saveAndFlush(new SpecialOpeningHours(FRIDAY, true, LocalTime.NOON, LocalTime.of(14, 0)));
        mvc.perform(get("/api/public/opening-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("SPECIAL"))
                .andExpect(jsonPath("$.openNow").value(true));
    }

    @Test
    void specialClosedDateReplacesOpenWeeklyDay() throws Exception {
        profile(); closedWeek(); openFriday(LocalTime.NOON, LocalTime.of(18, 0));
        special.saveAndFlush(new SpecialOpeningHours(FRIDAY, false, null, null));
        mvc.perform(get("/api/public/opening-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("SPECIAL"))
                .andExpect(jsonPath("$.closedToday").value(true));
    }

    @Test
    void instantIsConvertedBeforeChoosingLocalWeekday() throws Exception {
        profile(); closedWeek(); openFriday(LocalTime.of(1, 0), LocalTime.of(2, 0));
        clock.set(Instant.parse("2026-09-24T22:30:00Z"));
        mvc.perform(get("/api/public/opening-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localDate").value("2026-09-25"))
                .andExpect(jsonPath("$.localTime").value("01:30:00"))
                .andExpect(jsonPath("$.openNow").value(true));
    }

    @Test
    void missingProfileAndIncompleteScheduleHaveDeliberateStatuses() throws Exception {
        mvc.perform(get("/api/public/restaurant")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/opening-status")).andExpect(status().isNotFound());
        profile();
        mvc.perform(get("/api/public/opening-status")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void onlyListedReadsArePublic() throws Exception {
        profile();
        mvc.perform(get("/api/public/restaurant")).andExpect(status().isOk());
        mvc.perform(get("/api/public/unknown")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/example")).andExpect(status().isForbidden());
        mvc.perform(head("/api/public/restaurant")).andExpect(status().isForbidden());
        mvc.perform(post("/api/public/restaurant").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void intervalAndDuplicateDateRulesAreEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeeklyOpeningHours(DayOfWeek.FRIDAY, true, LocalTime.of(18, 0), LocalTime.of(2, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new SpecialOpeningHours(FRIDAY, false, LocalTime.NOON, null));
        jdbc.update("INSERT INTO special_opening_hours (special_date, is_open) VALUES (?, FALSE)", FRIDAY);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO special_opening_hours (special_date, is_open) VALUES (?, FALSE)", FRIDAY));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO weekly_opening_hours (day_of_week, is_open, opening_time, closing_time) VALUES ('MONDAY', TRUE, '18:00:00', '02:00:00')"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO weekly_opening_hours (day_of_week, is_open) VALUES ('FUNDAY', FALSE)"));
        assertThrows(IllegalArgumentException.class,
                () -> new RestaurantProfile("Test", "Food", "Address", "Phone", null,
                        "javascript:alert(1)", null, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new RestaurantTimeConfiguration().restaurantZone("Invalid/Zone"));
    }

    @Test
    void databaseEnforcesSingleProfileAndOneRecordPerWeekday() {
        profile(); closedWeek();
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE restaurant_profile SET id = 2 WHERE id = 1"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO weekly_opening_hours (day_of_week, is_open) VALUES ('MONDAY', FALSE)"));
    }
}
