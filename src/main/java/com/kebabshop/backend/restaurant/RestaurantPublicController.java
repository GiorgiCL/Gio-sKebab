package com.kebabshop.backend.restaurant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
class RestaurantPublicController {
    private final RestaurantPublicService service;

    RestaurantPublicController(RestaurantPublicService service) {
        this.service = service;
    }

    @GetMapping("/restaurant")
    RestaurantResponse restaurant() {
        return service.restaurant();
    }

    @GetMapping("/opening-hours")
    OpeningHoursResponse openingHours() {
        return service.openingHours();
    }

    @GetMapping("/opening-status")
    OpeningStatusResponse openingStatus() {
        return service.openingStatus();
    }
}
