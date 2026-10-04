package com.kebabshop.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerPortConfigurationTests {

    @Test
    void defaultsTo8080WhenPortIsAbsent() throws IOException {
        var environment = new MockEnvironment();
        environment.getPropertySources().addLast(applicationProperties());

        assertEquals(8080, environment.getProperty("server.port", Integer.class));
    }

    @Test
    void usesPortEnvironmentVariableWhenPresent() throws IOException {
        var environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("environment", Map.of("PORT", "9090")));
        environment.getPropertySources().addLast(applicationProperties());

        assertEquals(9090, environment.getProperty("server.port", Integer.class));
    }

    private static PropertySource<?> applicationProperties() throws IOException {
        return new PropertiesPropertySourceLoader()
                .load("application", new ClassPathResource("application.properties"))
                .getFirst();
    }
}
