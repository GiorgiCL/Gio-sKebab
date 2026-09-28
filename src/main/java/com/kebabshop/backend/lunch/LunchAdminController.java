package com.kebabshop.backend.lunch;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import com.kebabshop.backend.image.ImageUploadCoordinator;
import com.kebabshop.backend.image.ManagedImageStorage.ImageKind;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/lunch-menu/items")
class LunchAdminController {
    private final LunchService service;
    private final ImageUploadCoordinator images;
    LunchAdminController(LunchService service, ImageUploadCoordinator images) { this.service = service; this.images = images; }

    @GetMapping List<LunchResponse> items() { return service.items(); }
    @GetMapping("/{id}") LunchResponse item(@PathVariable Long id) { return service.item(id); }
    @PostMapping ResponseEntity<LunchResponse> create(@Valid @RequestBody LunchRequest request) {
        var response = service.create(request);
        return ResponseEntity.created(URI.create("/api/admin/lunch-menu/items/" + response.id())).body(response);
    }
    @PutMapping("/{id}") LunchResponse replace(@PathVariable Long id, @Valid @RequestBody LunchRequest request) {
        return images.remove(ImageKind.LUNCH, () -> service.replace(id, request));
    }
    @PostMapping("/{id}/image") LunchResponse uploadImage(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return images.upload(ImageKind.LUNCH, file, stored -> service.replaceManagedImage(id, stored.url(), stored.publicId()));
    }
    @DeleteMapping("/{id}/image") LunchResponse removeImage(@PathVariable Long id) {
        return images.remove(ImageKind.LUNCH, () -> service.removeManagedImage(id));
    }
    @DeleteMapping("/{id}") ResponseEntity<Void> delete(@PathVariable Long id) {
        images.remove(ImageKind.LUNCH, () -> service.delete(id));
        return ResponseEntity.noContent().build();
    }
}
