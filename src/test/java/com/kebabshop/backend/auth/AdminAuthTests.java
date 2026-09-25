package com.kebabshop.backend.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuthTests {
    @Autowired MockMvc mvc;
    @Autowired AdminAccountRepository accounts;
    @Autowired AdminProvisioningService provisioning;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;

    @BeforeEach
    void resetAccount() {
        accounts.deleteAll();
    }

    @Test
    void provisioningEncodesNormalizesAndRefusesReinitialization() {
        assertThrows(IllegalStateException.class,
                () -> new AdminBootstrapConfiguration().firstAdminBootstrap(provisioning, new MockEnvironment()));
        assertTrue(new AdminBootstrapConfiguration().firstAdminBootstrap(provisioning,
                new MockEnvironment().withProperty("spring.main.web-application-type", "none")) != null);
        assertThrows(IllegalArgumentException.class,
                () -> provisioning.createFirstAccount("other@example.com", "x".repeat(73)));
        provisioning.createFirstAccount(" OWNER@Example.com ", "temporary-test-password");
        var stored = accounts.findById((short) 1).orElseThrow();
        assertEquals("owner@example.com", stored.getEmail());
        assertNotEquals("temporary-test-password", stored.getPasswordHash());
        assertTrue(encoder.matches("temporary-test-password", stored.getPasswordHash()));
        assertThrows(IllegalStateException.class,
                () -> provisioning.createFirstAccount("other@example.com", "another-test-password"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO admin_account (id, email, password_hash, enabled, created_at, updated_at) "
                        + "VALUES (1, 'owner@example.com', 'duplicate', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"));
    }

    @Test
    void loginMeLogoutAndCsrfLifecycle() throws Exception {
        provisioning.createFirstAccount("owner@example.com", "temporary-test-password");
        var csrfResult = mvc.perform(get("/api/admin/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty()).andReturn();
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        assertTrue(session != null);
        String anonymousSessionId = session.getId();
        String token = JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/api/admin/auth/login").session(session)
                        .contentType("application/json")
                        .content("{\"email\":\"OWNER@example.com\",\"password\":\"temporary-test-password\"}"))
                .andExpect(status().isForbidden()); // Login requires CSRF.
        var loggedIn = mvc.perform(post("/api/admin/auth/login").session(session)
                        .header("X-CSRF-TOKEN", token)
                        .contentType("application/json")
                        .content("{\"email\":\"OWNER@example.com\",\"password\":\"temporary-test-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andReturn();
        MockHttpSession authenticated = (MockHttpSession) loggedIn.getRequest().getSession(false);
        assertTrue(authenticated != null);
        assertNotEquals(anonymousSessionId, authenticated.getId());
        mvc.perform(get("/api/admin/auth/me").session(authenticated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@example.com"));
        mvc.perform(get("/api/not-public").session(authenticated)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/auth/logout").session(authenticated))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/auth/logout").session(authenticated).with(csrf()))
                .andExpect(status().isNoContent());
        assertTrue(authenticated.isInvalid());
        mvc.perform(get("/api/admin/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void badAndMissingCredentialsHaveSameResponseAndDisabledAccountCannotLogin() throws Exception {
        provisioning.createFirstAccount("owner@example.com", "temporary-test-password");
        String wrong = mvc.perform(post("/api/admin/auth/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"owner@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(post("/api/admin/auth/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"missing@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertEquals(wrong, missing);
        var account = accounts.findById((short) 1).orElseThrow();
        account.setEnabled(false);
        accounts.saveAndFlush(account);
        mvc.perform(post("/api/admin/auth/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"owner@example.com\",\"password\":\"temporary-test-password\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disablingAccountInvalidatesExistingAuthenticatedSession() throws Exception {
        provisioning.createFirstAccount("owner@example.com", "temporary-test-password");
        var result = mvc.perform(post("/api/admin/auth/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"owner@example.com\",\"password\":\"temporary-test-password\"}"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertTrue(session != null);
        var account = accounts.findById((short) 1).orElseThrow();
        account.setEnabled(false);
        accounts.saveAndFlush(account);
        mvc.perform(get("/api/admin/auth/me").session(session)).andExpect(status().isUnauthorized());
        assertTrue(session.isInvalid());
    }

    @Test
    void publicAndNonPublicBoundariesRemainNarrow() throws Exception {
        mvc.perform(get("/api/public/restaurant")).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/example")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/not-public")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/auth/login")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/auth/csrf").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/public/restaurant").with(csrf())).andExpect(status().isUnauthorized());
        assertTrue(!context.containsBean("firstAdminBootstrap"));
    }

    @Test
    void malformedAndOversizedCredentialsAreRejectedWithoutLeakage() throws Exception {
        assertTrue(!new AdminAuthController.LoginRequest("owner@example.com", "secret-marker")
                .toString().contains("secret-marker"));
        mvc.perform(post("/api/admin/auth/login").with(csrf()).contentType("application/json")
                        .content("{bad json"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/auth/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":null,\"password\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/auth/login").with(csrf()).contentType("application/json")
                        .content("{\"email\":\"owner@example.com\",\"password\":\"" + "x".repeat(257) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid login request"));
    }
}
