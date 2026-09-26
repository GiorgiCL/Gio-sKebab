package com.kebabshop.backend;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentMigrationTests {
    @Test
    void existingCanonicalRowsSurviveUpgradeFromV5() {
        String url = "jdbc:h2:mem:gio_kebab_v5_upgrade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        var dataSource = new DriverManagerDataSource(url, "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("5")).load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO restaurant_profile (id, display_name, description, address, phone, google_maps_url, created_at, updated_at) "
                + "VALUES (1, 'Gio', 'Original text', 'Vilnius', '123', 'https://example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO menu_category (name, display_order, active, created_at, updated_at) "
                + "VALUES ('Kebabai', 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO menu_item (category_id, name, description, price_eur, active, available, display_order, created_at, updated_at) "
                + "SELECT id, 'Kebabas', 'Fresh', 8.50, TRUE, TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM menu_category");
        jdbc.update("INSERT INTO promotion (title, active, display_order, created_at, updated_at) "
                + "VALUES ('Offer', TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertEquals("Original text", jdbc.queryForObject("SELECT description FROM restaurant_profile WHERE id = 1", String.class));
        assertEquals("Kebabai", jdbc.queryForObject("SELECT name FROM menu_category", String.class));
        assertEquals("Fresh", jdbc.queryForObject("SELECT description FROM menu_item", String.class));
        assertEquals("Offer", jdbc.queryForObject("SELECT title FROM promotion", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM menu_category_translation", Integer.class));
    }
}
