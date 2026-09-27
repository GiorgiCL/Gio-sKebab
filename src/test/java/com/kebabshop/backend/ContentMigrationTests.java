package com.kebabshop.backend;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentMigrationTests {
    @Test
    void v7RowsSurviveLunchMenuUpgrade() {
        String url = "jdbc:h2:mem:gio_kebab_v7_lunch_upgrade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        var dataSource = new DriverManagerDataSource(url, "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("7")).load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO restaurant_profile (id, display_name, description, address, phone, google_maps_url, created_at, updated_at) "
                + "VALUES (1, 'Gio', 'Original', 'Vilnius', '123', 'https://example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO menu_category (name, display_order, active, created_at, updated_at) "
                + "VALUES ('Drinks', 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO menu_item (category_id, name, price_eur, active, available, display_order, created_at, updated_at) "
                + "SELECT id, 'Ayran', 2.50, TRUE, TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM menu_category");
        Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("8")).load();
        flyway.migrate();
        assertEquals("8", flyway.info().current().getVersion().toString());
        assertEquals("Original", jdbc.queryForObject("SELECT description FROM restaurant_profile WHERE id = 1", String.class));
        assertEquals("Ayran", jdbc.queryForObject("SELECT name FROM menu_item", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM lunch_menu_item", Integer.class));
    }

    @Test
    void v9CorrectsOnlyOriginalLargeKebabCategoryContent() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:gio_kebab_v9_category;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("8")).load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO menu_category (id, name, display_order, active, created_at, updated_at) "
                + "VALUES (42, 'Susirink savo KEBABĄ (Didelis)', 7, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                + "(43, 'Owner category', 8, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO menu_category_translation (category_id, locale, name) VALUES "
                + "(42, 'en', 'Build Your Own Kebab (Large)'), (42, 'ru', 'Собери свой кебаб (большой)'), "
                + "(42, 'ka', 'ააწყვე შენი ქაბაბი (დიდი)'), (43, 'en', 'Build Your Own Kebab (Large)')");
        jdbc.update("INSERT INTO menu_item (category_id, name, price_eur, active, available, display_order, created_at, updated_at) "
                + "VALUES (42, 'Original product', 10.50, TRUE, TRUE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertEquals("Dideli kebabai", jdbc.queryForObject("SELECT name FROM menu_category WHERE id = 42", String.class));
        assertEquals("Large Kebabs", jdbc.queryForObject("SELECT name FROM menu_category_translation WHERE category_id = 42 AND locale = 'en'", String.class));
        assertEquals("Большие кебабы", jdbc.queryForObject("SELECT name FROM menu_category_translation WHERE category_id = 42 AND locale = 'ru'", String.class));
        assertEquals("დიდი ქაბაბები", jdbc.queryForObject("SELECT name FROM menu_category_translation WHERE category_id = 42 AND locale = 'ka'", String.class));
        assertEquals("Owner category", jdbc.queryForObject("SELECT name FROM menu_category WHERE id = 43", String.class));
        assertEquals("Build Your Own Kebab (Large)", jdbc.queryForObject("SELECT name FROM menu_category_translation WHERE category_id = 43", String.class));
        assertEquals(7, jdbc.queryForObject("SELECT display_order FROM menu_category WHERE id = 42", Integer.class));
        assertEquals("Original product", jdbc.queryForObject("SELECT name FROM menu_item WHERE category_id = 42", String.class));
    }

    @Test
    void v10SeedsProfileDescriptionsAndPreservesOtherTranslationFields() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:gio_kebab_v10_profile_text;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("9")).load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO restaurant_profile (id, display_name, description, address, phone, google_maps_url, created_at, updated_at) "
                + "VALUES (1, 'Gio''s Kebab', 'Kebabai ir grilio patiekalai Savanorių prospekte, su rankų darbo padažais ir kartveliškais skoniais.', "
                + "'Vilnius', '123', 'https://example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO restaurant_profile_translation (profile_id, locale, display_name) VALUES (1, 'en', 'Gio''s Kebab EN')");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertEquals("11", Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load()
                .info().current().getVersion().toString());
        assertEquals("Kebabs and grilled dishes on Savanorių Avenue, with house-made sauces and Georgian flavours.",
                jdbc.queryForObject("SELECT description FROM restaurant_profile_translation WHERE locale = 'en'", String.class));
        assertEquals("Kebabai ir grilio patiekalai Savanorių prospekte, su rankų darbo padažais ir kartveliškais skoniais.",
                jdbc.queryForObject("SELECT description FROM restaurant_profile WHERE id = 1", String.class));
        assertEquals("Gio's Kebab EN",
                jdbc.queryForObject("SELECT display_name FROM restaurant_profile_translation WHERE locale = 'en'", String.class));
        assertEquals("Кебабы и блюда на гриле на проспекте Саванорю, с соусами собственного приготовления и грузинскими нотками.",
                jdbc.queryForObject("SELECT description FROM restaurant_profile_translation WHERE locale = 'ru'", String.class));
        assertEquals("ქაბაბები და გრილზე მომზადებული კერძები სავანორიუს გამზირზე, ჩვენი მომზადებული სოუსებითა და ქართული გემოებით.",
                jdbc.queryForObject("SELECT description FROM restaurant_profile_translation WHERE locale = 'ka'", String.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM restaurant_profile_translation WHERE profile_id = 1", Integer.class));
    }
    @Test
    void v11PreservesOwnerProvidedTranslationAfterV10() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:gio_kebab_v10_existing_profile_text;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("9")).load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO restaurant_profile (id, display_name, description, address, phone, google_maps_url, created_at, updated_at) "
                + "VALUES (1, 'Gio', 'Lithuanian source', 'Vilnius', '123', 'https://example.com', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO restaurant_profile_translation (profile_id, locale, description) VALUES (1, 'en', 'Owner approved wording')");

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("10")).load().migrate();
        assertEquals("Kebabs and grilled dishes on Savanorių Avenue, with house-made sauces and Georgian flavours.",
                jdbc.queryForObject("SELECT description FROM restaurant_profile_translation WHERE locale = 'en'", String.class));

        jdbc.update("UPDATE restaurant_profile_translation SET description = 'Owner approved wording' WHERE locale = 'en'");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertEquals("Owner approved wording", jdbc.queryForObject(
                "SELECT description FROM restaurant_profile_translation WHERE locale = 'en'", String.class));
        assertEquals("Lithuanian source", jdbc.queryForObject(
                "SELECT description FROM restaurant_profile WHERE id = 1", String.class));
    }

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
