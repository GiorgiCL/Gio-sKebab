package com.kebabshop.backend.lunch;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admin/lunch-menu/items")
class LunchAdminController {
    private final LunchService service;
    LunchAdminController(LunchService service) { this.service = service; }

    @GetMapping List<LunchResponse> items() { return service.items(); }
    @GetMapping("/{id}") LunchResponse item(@PathVariable Long id) { return service.item(id); }
    @PostMapping ResponseEntity<LunchResponse> create(@Valid @RequestBody LunchRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/admin/lunch-menu/items/" + response.id())).body(response);
    }
    @PutMapping("/{id}") LunchResponse replace(@PathVariable Long id, @Valid @RequestBody LunchRequest request) {
        return service.replace(id, request);
    }
    @DeleteMapping("/{id}") ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
