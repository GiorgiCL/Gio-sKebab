package com.kebabshop.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendApplicationTests {

    // Highest-precedence test properties keep this smoke test on an in-memory database.
    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:gio_kebab_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.flyway.url", () -> "jdbc:h2:mem:gio_kebab_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.flyway.user", () -> "sa");
        registry.add("spring.flyway.password", () -> "");
    }

    @Autowired
    DataSource dataSource;

    @Autowired
    Flyway flyway;

    @Autowired
    MockMvc mockMvc;

    @Test
    void contextLoadsWithIsolatedDatabaseAndFlyway() throws SQLException {
        assertNotNull(flyway);
        try (var connection = dataSource.getConnection()) {
            assertEquals("jdbc:h2:mem:gio_kebab_test", connection.getMetaData().getURL().split(";")[0]);
        }
        assertEquals(0, flyway.info().all().length);
    }

    @Test
    void adminRoutesAreDeniedBeforeAuthenticationExists() throws Exception {
        mockMvc.perform(get("/api/admin/example"))
                .andExpect(status().isForbidden());
    }

}
