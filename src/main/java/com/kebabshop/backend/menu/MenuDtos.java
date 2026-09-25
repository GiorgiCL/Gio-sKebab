package com.kebabshop.backend.menu;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

record CategoryRequest(@NotBlank @Size(max = 160) String name,
                       @NotNull @Min(0) Integer displayOrder, @NotNull Boolean active) {}

record ItemRequest(@NotNull @Min(1) Long categoryId,
                   @NotBlank @Size(max = 160) String name,
                   @NotBlank @Size(max = 1000) String description,
                   @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal priceEur,
                   @NotNull Boolean active, @NotNull Boolean available,
                   @NotNull @Min(0) Integer displayOrder) {}

record CategoryResponse(Long id, String name, int displayOrder, boolean active,
                        Instant createdAt, Instant updatedAt) {
    static CategoryResponse from(MenuCategory category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDisplayOrder(),
                category.isActive(), category.getCreatedAt(), category.getUpdatedAt());
    }
}

record ItemResponse(Long id, Long categoryId, String name, String description, BigDecimal priceEur,
                    boolean active, boolean available, int displayOrder, Instant createdAt, Instant updatedAt) {
    static ItemResponse from(MenuItem item) {
        return new ItemResponse(item.getId(), item.getCategoryId(), item.getName(), item.getDescription(),
                item.getPriceEur(), item.isActive(), item.isAvailable(), item.getDisplayOrder(),
                item.getCreatedAt(), item.getUpdatedAt());
    }
}

record PublicMenuResponse(List<PublicCategory> categories) {}
record PublicCategory(Long id, String name, List<PublicItem> items) {}
record PublicItem(Long id, String name, String description, BigDecimal priceEur, boolean available) {
    static PublicItem from(MenuItem item) {
        return new PublicItem(item.getId(), item.getName(), item.getDescription(),
                item.getPriceEur(), item.isAvailable());
    }
}
