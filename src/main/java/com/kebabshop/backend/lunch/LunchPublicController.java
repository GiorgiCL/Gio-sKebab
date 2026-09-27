package com.kebabshop.backend.lunch;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/lunch-menu")
class LunchPublicController {
    private final LunchService service;
    LunchPublicController(LunchService service) { this.service = service; }
    @GetMapping PublicLunchMenuResponse menu(@RequestParam(required = false) String lang) {
        return service.publicMenu(lang);
    }
}
