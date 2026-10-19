package com.kebabshop.backend.allergen;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.dao.DataIntegrityViolationException;

public class AllergenMigrationTests {
    @Test void existingItemsRemainUnverifiedAndConstraintsProtectCategories() {
        verifyUpgrade(new DriverManagerDataSource("jdbc:h2:mem:allergen_upgrade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""), "PUBLIC");
    }

    public static void verifyUpgrade(DataSource dataSource, String schema) {
        Flyway.configure().dataSource(dataSource).schemas(schema).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("13")).load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO " + schema + ".menu_category (id,name,display_order,active,created_at,updated_at) VALUES (701,'Owner category',0,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO " + schema + ".menu_item (id,category_id,name,price_eur,active,available,display_order,created_at,updated_at) VALUES (701,701,'Unverified milk dish',7.50,TRUE,TRUE,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO " + schema + ".lunch_menu_item (id,day_of_week,name,price_eur,active,available,display_order,created_at,updated_at) VALUES (701,'MONDAY','Unverified egg dish',6.50,TRUE,TRUE,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        var upgraded = Flyway.configure().dataSource(dataSource).schemas(schema).locations("classpath:db/migration").load();
        upgraded.migrate();
        assertEquals("14", upgraded.info().current().getVersion().toString());
        for (String entity : new String[]{"menu_item", "lunch_menu_item"}) {
            String table = schema + "." + entity;
            assertEquals("NOT_VERIFIED", jdbc.queryForObject("SELECT allergen_status FROM " + table + " WHERE id=701", String.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table + "_allergen", Integer.class));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE " + table + " SET allergen_status='GUESS' WHERE id=701"));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO " + table + "_allergen VALUES (701,'GUESS')"));
            jdbc.update("INSERT INTO " + table + "_allergen VALUES (701,'MILK')");
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO " + table + "_allergen VALUES (701,'MILK')"));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO " + table + "_allergen VALUES (999999,'MILK')"));
            jdbc.update("DELETE FROM " + table + " WHERE id=701");
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table + "_allergen", Integer.class));
        }
    }
}
