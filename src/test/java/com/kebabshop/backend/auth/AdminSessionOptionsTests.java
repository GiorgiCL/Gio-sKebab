package com.kebabshop.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.admin.remember-me-duration=90s",
        "spring.datasource.url=jdbc:h2:mem:session_options_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.flyway.url=jdbc:h2:mem:session_options_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSessionOptionsTests {
    @Autowired MockMvc mvc;

    @Test
    void loginOptionsFollowAnOverriddenDurationWithoutCreatingASession() throws Exception {
        var result = mvc.perform(get("/api/admin/auth/session-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rememberMeSeconds").value(90))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertNull(result.getRequest().getSession(false));
    }
}
