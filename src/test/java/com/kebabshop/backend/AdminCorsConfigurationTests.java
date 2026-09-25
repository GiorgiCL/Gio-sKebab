package com.kebabshop.backend;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminCorsConfigurationTests {
    private final AdminSecurityConfiguration security = new AdminSecurityConfiguration();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/auth/login");

    @Test
    void sameOriginDefaultHasNoCredentialedCorsPolicy() {
        assertNull(security.corsConfigurationSource("", new MockEnvironment()).getCorsConfiguration(request));
    }

    @Test
    void explicitOriginIsNarrowAndProductionRejectsInsecureOrMalformedOrigins() {
        var prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        var config = security.corsConfigurationSource("https://admin.example.com", prod)
                .getCorsConfiguration(request);
        assertEquals("https://admin.example.com", config.getAllowedOrigins().getFirst());
        assertEquals(true, config.getAllowCredentials());
        assertEquals(4, config.getAllowedMethods().size());
        assertThrows(IllegalArgumentException.class,
                () -> security.corsConfigurationSource("http://admin.example.com", prod));
        assertThrows(IllegalArgumentException.class,
                () -> security.corsConfigurationSource("*", prod));
        assertThrows(IllegalArgumentException.class,
                () -> security.corsConfigurationSource("https://admin.example.com,", prod));
    }
}
