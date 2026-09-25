package com.kebabshop.backend.restaurant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.time.DateTimeException;

@Configuration
class RestaurantTimeConfiguration {
    @Bean
    ZoneId restaurantZone(@Value("${restaurant.time-zone}") String configuredZone) {
        try {
            return ZoneId.of(configuredZone);
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Invalid restaurant.time-zone: " + configuredZone, ex);
        }
    }

    @Bean
    Clock restaurantClock() {
        return Clock.systemUTC();
    }
}
