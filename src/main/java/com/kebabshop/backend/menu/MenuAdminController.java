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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import com.kebabshop.backend.image.ImageUploadCoordinator;
import com.kebabshop.backend.image.ManagedImageStorage.ImageKind;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admin/menu")
class MenuAdminController {
    private final MenuService service;
    private final ImageUploadCoordinator images;

    MenuAdminController(MenuService service, ImageUploadCoordinator images) {
        this.service = service;
        this.images = images;
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
        return images.remove(ImageKind.MENU, () -> service.replaceItem(id, request));
    }

    @PostMapping("/items/{id}/image")
    ItemResponse uploadItemImage(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return images.upload(ImageKind.MENU, file, stored -> service.replaceManagedImage(id, stored.url(), stored.publicId()));
    }

    @DeleteMapping("/items/{id}/image")
    ItemResponse removeItemImage(@PathVariable Long id) {
        return images.remove(ImageKind.MENU, () -> service.removeManagedImage(id));
    }

    @DeleteMapping("/items/{id}")
    ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        images.remove(ImageKind.MENU, () -> service.deleteItem(id));
        return ResponseEntity.noContent().build();
    }
}
