package com.kebabshop.backend.restaurant;

import jakarta.persistence.EntityManagerFactory;
import com.kebabshop.backend.auth.AdminProvisioningService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("postgres-it")
@Import(PostgresqlSchemaIT.ContainerConfiguration.class)
class PostgresqlSchemaIT {
    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:17.9-alpine")
                    .withDatabaseName("gios_kebab_it");
        }
    }

    @Autowired DataSource dataSource;
    @Autowired Flyway flyway;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired JdbcTemplate jdbc;
    @Autowired RestaurantProfileRepository profiles;
    @Autowired AdminProvisioningService provisioning;
    @Autowired RestaurantAdminService admin;

    @Test
    void flywayJpaAndPostgresqlConstraintsWorkTogether() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:postgresql:"));
            assertEquals(17, connection.getMetaData().getDatabaseMajorVersion());
        }
        assertNotNull(entityManagerFactory); // Context startup has already run Hibernate schema validation.
        assertEquals("7", flyway.info().current().getVersion().toString());
        assertEquals(7, flyway.info().applied().length);

        RestaurantProfile profile = profiles.saveAndFlush(new RestaurantProfile("Container Restaurant",
                "Fresh food", "1 Main Street", "+37060000000", null,
                "https://maps.example.com/test", null, null, null, null));
        assertNotNull(profile.getCreatedAt());
        assertNotNull(profiles.findById((short) 1).orElseThrow().getUpdatedAt());

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE restaurant_profile SET id = 2 WHERE id = 1"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO weekly_opening_hours (day_of_week, is_open) VALUES ('FUNDAY', FALSE)"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO weekly_opening_hours (day_of_week, is_open, opening_time, closing_time) VALUES ('MONDAY', TRUE, '18:00:00', '02:00:00')"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO weekly_opening_hours (day_of_week, is_open, opening_time) VALUES ('TUESDAY', FALSE, '12:00:00')"));
        jdbc.update("INSERT INTO special_opening_hours (special_date, is_open) VALUES (?, FALSE)", LocalDate.of(2026, 12, 25));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO special_opening_hours (special_date, is_open) VALUES (?, FALSE)", LocalDate.of(2026, 12, 25)));

        provisioning.createFirstAccount("OWNER@Example.com", "temporary-test-password");
        assertEquals("owner@example.com", jdbc.queryForObject("SELECT email FROM admin_account WHERE id = 1", String.class));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE admin_account SET id = 2 WHERE id = 1"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE admin_account SET email = 'OWNER@example.com' WHERE id = 1"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE admin_account SET password_hash = ' ' WHERE id = 1"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE admin_account SET enabled = NULL WHERE id = 1"));

        var days = Arrays.stream(DayOfWeek.values())
                .map(day -> new WeeklyDayRequest(day, day == DayOfWeek.FRIDAY,
                        day == DayOfWeek.FRIDAY ? LocalTime.NOON : null,
                        day == DayOfWeek.FRIDAY ? LocalTime.of(18, 0) : null)).toList();
        assertEquals(7, admin.replaceWeekly(new WeeklyScheduleRequest(days)).size());
        assertEquals(7, admin.weeklySchedule().size());
        LocalDate date = LocalDate.of(2027, 1, 1);
        admin.createSpecial(new SpecialDateRequest(date, false, null, null));
        assertEquals(2, admin.specialDates().size()); // The earlier V1 constraint check inserted one other date.
        admin.replaceSpecial(date, new SpecialDateReplacementRequest(true, LocalTime.NOON, LocalTime.of(14, 0)));
        admin.deleteSpecial(date);
        assertEquals(1, admin.specialDates().size());

        Long categoryId = jdbc.queryForObject("INSERT INTO menu_category "
                + "(name, display_order, active, created_at, updated_at) "
                + "VALUES ('Food', 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id", Long.class);
        assertNotNull(categoryId);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE menu_category SET display_order = -1 WHERE id = ?", categoryId));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE menu_category SET name = ' ' WHERE id = ?", categoryId));
        jdbc.update("INSERT INTO menu_item (category_id, name, description, price_eur, active, available, "
                        + "display_order, created_at, updated_at) "
                        + "VALUES (?, 'Kebab', 'Fresh food', 8.50, TRUE, TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                categoryId);
        assertEquals("Fresh food", jdbc.queryForObject("SELECT description FROM menu_item WHERE name = 'Kebab'", String.class));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE menu_item SET description = ' ' WHERE name = 'Kebab'"));
        jdbc.update("UPDATE menu_item SET description = NULL WHERE name = 'Kebab'");
        assertEquals(null, jdbc.queryForObject("SELECT description FROM menu_item WHERE name = 'Kebab'", String.class));
        jdbc.update("UPDATE menu_item SET description = 'Fresh food' WHERE name = 'Kebab'");
        assertEquals(false, jdbc.queryForObject("SELECT featured FROM menu_item WHERE category_id = ?", Boolean.class, categoryId));
        assertEquals(null, jdbc.queryForObject("SELECT image_url FROM menu_item WHERE category_id = ?", String.class, categoryId));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE menu_item SET featured = NULL WHERE category_id = ?", categoryId));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE menu_item SET image_url = ? WHERE category_id = ?", "x".repeat(2049), categoryId));
        jdbc.update("UPDATE menu_item SET featured = TRUE, image_url = ? WHERE category_id = ?",
                "https://images.example.com/kebab.jpg", categoryId);
        assertEquals(true, jdbc.queryForObject("SELECT featured FROM menu_item WHERE category_id = ?", Boolean.class, categoryId));
        assertEquals("https://images.example.com/kebab.jpg",
                jdbc.queryForObject("SELECT image_url FROM menu_item WHERE category_id = ?", String.class, categoryId));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM menu_category WHERE id = ?", categoryId));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE menu_item SET price_eur = 0 WHERE category_id = ?", categoryId));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE menu_item SET category_id = 999999 WHERE category_id = ?", categoryId));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE menu_item SET display_order = -1 WHERE category_id = ?", categoryId));

        jdbc.update("INSERT INTO promotion (title, description, active, starts_at, ends_at, display_order, "
                        + "created_at, updated_at) VALUES ('Open ended', NULL, TRUE, NULL, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO promotion (title, active, display_order, created_at, updated_at) "
                        + "VALUES (' ', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO promotion (title, active, display_order, created_at, updated_at) "
                        + "VALUES ('Invalid order', TRUE, -1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO promotion (title, active, starts_at, ends_at, display_order, created_at, updated_at) "
                        + "VALUES ('Invalid window', TRUE, TIMESTAMPTZ '2026-09-26 10:00:00+00', "
                        + "TIMESTAMPTZ '2026-09-25 10:00:00+00', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"));

        assertEquals("Food", jdbc.queryForObject("SELECT name FROM menu_category WHERE id = ?", String.class, categoryId));
        assertEquals("Kebab", jdbc.queryForObject("SELECT name FROM menu_item WHERE category_id = ?", String.class, categoryId));
        jdbc.update("INSERT INTO menu_category_translation (category_id, locale, name) VALUES (?, 'en', 'Food')", categoryId);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO menu_category_translation (category_id, locale, name) VALUES (?, 'en', 'Other')", categoryId));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO menu_category_translation (category_id, locale, name) VALUES (?, 'de', 'Essen')", categoryId));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO menu_item_translation (item_id, locale, name) VALUES (999999, 'en', 'Missing')"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO menu_item_translation (item_id, locale, name) "
                        + "SELECT id, 'ru', ' ' FROM menu_item WHERE category_id = ?", categoryId));
        jdbc.update("INSERT INTO menu_item_translation (item_id, locale, description) "
                + "SELECT id, 'ru', 'Tasty' FROM menu_item WHERE category_id = ?", categoryId);
        jdbc.update("INSERT INTO menu_item_translation (item_id, locale, name) "
                + "SELECT id, 'ka', 'Translated name' FROM menu_item WHERE name = 'Kebab'");
        assertEquals(null, jdbc.queryForObject("SELECT description FROM menu_item_translation "
                + "WHERE locale = 'ka' AND item_id = (SELECT id FROM menu_item WHERE name = 'Kebab')", String.class));
        assertEquals("Kebab", jdbc.queryForObject("SELECT name FROM menu_item WHERE category_id = ?", String.class, categoryId));

        jdbc.update("INSERT INTO restaurant_profile_translation (profile_id, locale, description) "
                + "VALUES (1, 'en', 'Fresh food in English')");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO restaurant_profile_translation (profile_id, locale, description) "
                        + "VALUES (1, 'en', 'Duplicate')"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO restaurant_profile_translation (profile_id, locale, description) "
                        + "VALUES (1, 'lt', 'Canonical belongs in profile')"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO restaurant_profile_translation (profile_id, locale, description) "
                        + "VALUES (1, 'ka', ' ')"));
        assertEquals("Fresh food", jdbc.queryForObject(
                "SELECT description FROM restaurant_profile WHERE id = 1", String.class));

        jdbc.update("INSERT INTO promotion_translation (promotion_id, locale, title) "
                + "SELECT id, 'ka', 'Offer' FROM promotion");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO promotion_translation (promotion_id, locale, title) "
                        + "SELECT id, 'ka', 'Duplicate' FROM promotion"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO promotion_translation (promotion_id, locale, title) "
                        + "SELECT id, 'de', 'Unsupported' FROM promotion"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO promotion_translation (promotion_id, locale, title) "
                        + "SELECT id, 'ru', ' ' FROM promotion"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO promotion_translation (promotion_id, locale, title) "
                        + "VALUES (999999, 'en', 'Missing parent')"));
        assertEquals("Open ended", jdbc.queryForObject("SELECT title FROM promotion", String.class));

        Long drinksId = jdbc.queryForObject("INSERT INTO menu_category "
                + "(name, display_order, active, created_at, updated_at) "
                + "VALUES ('Drinks', 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id", Long.class);
        jdbc.update("INSERT INTO menu_item (category_id, name, price_eur, active, available, "
                + "display_order, created_at, updated_at) VALUES (?, 'Cola', 2.90, TRUE, TRUE, 0, "
                + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", drinksId);
        assertEquals(null, jdbc.queryForObject("SELECT description FROM menu_item WHERE name = 'Cola'", String.class));
    }

    @Test
    void existingContentSurvivesPostgresqlUpgradeFromV5() {
        String schema = "localization_upgrade";
        Flyway.configure().dataSource(dataSource).schemas(schema).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("5")).load().migrate();
        jdbc.update("INSERT INTO localization_upgrade.restaurant_profile "
                + "(id, display_name, description, address, phone, google_maps_url, created_at, updated_at) "
                + "VALUES (1, 'Gio', 'Original description', 'Vilnius', '123', 'https://example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO localization_upgrade.menu_category (name, display_order, active, created_at, updated_at) "
                + "VALUES ('Kebabai', 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO localization_upgrade.menu_item "
                + "(category_id, name, description, price_eur, active, available, display_order, created_at, updated_at) "
                + "SELECT id, 'Kebabas', 'Fresh', 8.50, TRUE, TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP "
                + "FROM localization_upgrade.menu_category");
        jdbc.update("INSERT INTO localization_upgrade.promotion "
                + "(title, active, display_order, created_at, updated_at) "
                + "VALUES ('Offer', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        var upgraded = Flyway.configure().dataSource(dataSource).schemas(schema)
                .locations("classpath:db/migration").load();
        upgraded.migrate();

        assertEquals("7", upgraded.info().current().getVersion().toString());
        assertEquals("Original description", jdbc.queryForObject(
                "SELECT description FROM localization_upgrade.restaurant_profile WHERE id = 1", String.class));
        assertEquals("Kebabai", jdbc.queryForObject("SELECT name FROM localization_upgrade.menu_category", String.class));
        assertEquals("Fresh", jdbc.queryForObject("SELECT description FROM localization_upgrade.menu_item", String.class));
        assertEquals("Offer", jdbc.queryForObject("SELECT title FROM localization_upgrade.promotion", String.class));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM localization_upgrade.menu_item_translation", Integer.class));
    }
}
