package com.kebabshop.backend.menu;

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
@RequestMapping("/api/admin/menu")
class MenuAdminController {
    private final MenuService service;

    MenuAdminController(MenuService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    List<CategoryResponse> categories() { return service.categories(); }

    @GetMapping("/categories/{id}")
    CategoryResponse category(@PathVariable Long id) { return service.category(id); }

    @PostMapping("/categories")
    ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        var response = service.createCategory(request);
        return ResponseEntity.created(URI.create("/api/admin/menu/categories/" + response.id())).body(response);
    }

    @PutMapping("/categories/{id}")
    CategoryResponse replaceCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        return service.replaceCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/items")
    List<ItemResponse> items() { return service.items(); }

    @GetMapping("/items/{id}")
    ItemResponse item(@PathVariable Long id) { return service.item(id); }

    @PostMapping("/items")
    ResponseEntity<ItemResponse> createItem(@Valid @RequestBody ItemRequest request) {
        var response = service.createItem(request);
        return ResponseEntity.created(URI.create("/api/admin/menu/items/" + response.id())).body(response);
    }

    @PutMapping("/items/{id}")
    ItemResponse replaceItem(@PathVariable Long id, @Valid @RequestBody ItemRequest request) {
        return service.replaceItem(id, request);
    }

    @DeleteMapping("/items/{id}")
    ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        service.deleteItem(id);
        return ResponseEntity.noContent().build();
    }
}
