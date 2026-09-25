package com.kebabshop.backend.auth;

import com.kebabshop.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminBootstrapStartupTests {
    @Test
    void nonWebApplicationCanStartWithoutBootstrapCredentials() {
        try (var context = new SpringApplicationBuilder(BackendApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .properties("app.admin.bootstrap=false")
                .run()) {
            assertNotNull(context.getBean(AdminProvisioningService.class));
        }
    }
}
