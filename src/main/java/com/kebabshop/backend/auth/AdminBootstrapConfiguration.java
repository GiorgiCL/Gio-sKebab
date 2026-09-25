package com.kebabshop.backend.auth;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class AdminBootstrapConfiguration {
    @Bean
    @ConditionalOnProperty(name = "app.admin.bootstrap", havingValue = "true")
    ApplicationRunner firstAdminBootstrap(AdminProvisioningService provisioning, Environment environment) {
        if (!"none".equals(environment.getProperty("spring.main.web-application-type"))) {
            throw new IllegalStateException("Admin bootstrap requires --spring.main.web-application-type=none");
        }
        return args -> {
            String email = System.getenv("ADMIN_BOOTSTRAP_EMAIL");
            String password = System.getenv("ADMIN_BOOTSTRAP_PASSWORD");
            if (email == null || password == null) {
                throw new IllegalStateException("ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD are required");
            }
            provisioning.createFirstAccount(email, password);
        };
    }
}
