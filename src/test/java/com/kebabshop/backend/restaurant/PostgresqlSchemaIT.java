package com.kebabshop.backend.restaurant;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
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

    @Test
    void flywayJpaAndPostgresqlConstraintsWorkTogether() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:postgresql:"));
            assertEquals(17, connection.getMetaData().getDatabaseMajorVersion());
        }
        assertNotNull(entityManagerFactory); // Context startup has already run Hibernate schema validation.
        assertEquals("1", flyway.info().current().getVersion().toString());
        assertEquals(1, flyway.info().applied().length);

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
    }
}
