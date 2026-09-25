package com.kebabshop.backend.promotion;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admin/promotions")
class PromotionAdminController {
    private final PromotionService service;

    PromotionAdminController(PromotionService service) {
        this.service = service;
    }

    @GetMapping
    List<PromotionResponse> list() { return service.list(); }

    @GetMapping("/{id}")
    PromotionResponse get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    ResponseEntity<PromotionResponse> create(@Valid @RequestBody PromotionRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/admin/promotions/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    PromotionResponse replace(@PathVariable Long id, @Valid @RequestBody PromotionRequest request) {
        return service.replace(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
