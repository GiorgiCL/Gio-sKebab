package com.kebabshop.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "security.cors.allowed-origins=https://admin.example.com")
class AdminCorsHttpTests {
    @Autowired MockMvc mvc;

    @Test
    void preflightAllowsOnlyConfiguredOriginAndRequiredHeaders() throws Exception {
        mvc.perform(options("/api/admin/auth/login")
                        .header("Origin", "https://admin.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://admin.example.com"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/admin/auth/login")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(options("/api/admin/opening-hours/special-dates/2026-09-25")
                        .header("Origin", "https://admin.example.com")
                        .header("Access-Control-Request-Method", "DELETE")
                        .header("Access-Control-Request-Headers", "x-csrf-token"))
                .andExpect(status().isOk());
    }
}
