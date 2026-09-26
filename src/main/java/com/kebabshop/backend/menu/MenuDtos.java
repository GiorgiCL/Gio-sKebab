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
import java.util.Map;
import com.kebabshop.backend.ContentTranslations;

record CategoryRequest(@NotBlank @Size(max = 160) String name,
                       @NotNull @Min(0) Integer displayOrder, @NotNull Boolean active,
                       Map<String, CategoryTranslation> translations) {}

record CategoryTranslation(String name) {}
record ItemTranslation(String name, String description) {}

record ItemRequest(@NotNull @Min(1) Long categoryId,
                   @NotBlank @Size(max = 160) String name,
                   @Size(max = 1000) String description,
                   @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal priceEur,
                   @NotNull Boolean active, @NotNull Boolean available,
                   @NotNull Boolean featured, @Size(max = 2048) String imageUrl,
                   @NotNull @Min(0) Integer displayOrder,
                   Map<String, ItemTranslation> translations) {
    ItemRequest {
        if (description != null && description.isBlank()) description = null;
    }
}

record CategoryResponse(Long id, String name, int displayOrder, boolean active,
                        Instant createdAt, Instant updatedAt, Map<String, CategoryTranslation> translations) {
    static CategoryResponse from(MenuCategory category, Map<String, ContentTranslations.Text> translations) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDisplayOrder(),
                category.isActive(), category.getCreatedAt(), category.getUpdatedAt(),
                ContentTranslations.withCanonical(new ContentTranslations.Text(category.getName(), null), translations)
                        .entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                                entry -> new CategoryTranslation(entry.getValue().first()))));
    }
}

record ItemResponse(Long id, Long categoryId, String name, String description, BigDecimal priceEur,
                    boolean active, boolean available, boolean featured, String imageUrl,
                    int displayOrder, Instant createdAt, Instant updatedAt, Map<String, ItemTranslation> translations) {
    static ItemResponse from(MenuItem item, Map<String, ContentTranslations.Text> translations) {
        return new ItemResponse(item.getId(), item.getCategoryId(), item.getName(), item.getDescription(),
                item.getPriceEur(), item.isActive(), item.isAvailable(), item.isFeatured(), item.getImageUrl(), item.getDisplayOrder(),
                item.getCreatedAt(), item.getUpdatedAt(),
                ContentTranslations.withCanonical(new ContentTranslations.Text(item.getName(), item.getDescription()), translations)
                        .entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                                entry -> new ItemTranslation(entry.getValue().first(), entry.getValue().description()))));
    }
}

record PublicMenuResponse(List<PublicCategory> categories) {}
record PublicCategory(Long id, String name, List<PublicItem> items) {}
record PublicItem(Long id, String name, String description, BigDecimal priceEur,
                  boolean available, boolean featured, String imageUrl) {
    static PublicItem from(MenuItem item, ContentTranslations.Text text) {
        return new PublicItem(item.getId(), text.first(), text.description(),
                item.getPriceEur(), item.isAvailable(), item.isFeatured(), item.getImageUrl());
    }
}
