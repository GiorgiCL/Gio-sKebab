package com.kebabshop.backend.promotion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/promotions")
class PromotionPublicController {
    private final PromotionService service;

    PromotionPublicController(PromotionService service) {
        this.service = service;
    }

    @GetMapping
    PublicPromotionsResponse promotions() {
        return service.publicPromotions();
    }
}
