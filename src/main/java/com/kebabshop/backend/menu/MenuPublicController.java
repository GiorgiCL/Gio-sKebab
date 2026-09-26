package com.kebabshop.backend.menu;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/public/menu")
class MenuPublicController {
    private final MenuService service;

    MenuPublicController(MenuService service) {
        this.service = service;
    }

    @GetMapping
    PublicMenuResponse menu(@RequestParam(required = false) String lang) {
        return service.publicMenu(lang);
    }
}
