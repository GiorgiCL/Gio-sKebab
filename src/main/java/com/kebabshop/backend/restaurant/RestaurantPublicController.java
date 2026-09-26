package com.kebabshop.backend.restaurant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/public")
class RestaurantPublicController {
    private final RestaurantPublicService service;

    RestaurantPublicController(RestaurantPublicService service) {
        this.service = service;
    }

    @GetMapping("/restaurant")
    RestaurantResponse restaurant(@RequestParam(required = false) String lang) {
        return service.restaurant(lang);
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
