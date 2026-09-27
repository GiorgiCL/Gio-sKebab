package com.kebabshop.backend.lunch;

import com.kebabshop.backend.ContentTranslations;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

record LunchTranslation(String name, String description) {}

record LunchRequest(@NotNull DayOfWeek dayOfWeek,
                    @NotBlank @Size(max = 160) String name,
                    @Size(max = 1000) String description,
                    @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal priceEur,
                    @NotNull Boolean active, @NotNull Boolean available,
                    @NotNull @Min(0) Integer displayOrder,
                    @Size(max = 2048) String imageUrl,
                    Map<String, LunchTranslation> translations) {
    LunchRequest {
        if (description != null && description.isBlank()) description = null;
    }
}

record LunchResponse(Long id, DayOfWeek dayOfWeek, String name, String description, BigDecimal priceEur,
                     boolean active, boolean available, int displayOrder, String imageUrl,
                     Instant createdAt, Instant updatedAt, Map<String, LunchTranslation> translations) {
    static LunchResponse from(LunchMenuItem item, Map<String, ContentTranslations.Text> translations) {
        var localized = ContentTranslations.withCanonical(
                new ContentTranslations.Text(item.getName(), item.getDescription()), translations);
        return new LunchResponse(item.getId(), item.getDayOfWeek(), item.getName(), item.getDescription(),
                item.getPriceEur(), item.isActive(), item.isAvailable(), item.getDisplayOrder(), item.getImageUrl(),
                item.getCreatedAt(), item.getUpdatedAt(), localized.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> new LunchTranslation(entry.getValue().first(), entry.getValue().description()))));
    }
}

record PublicLunchItem(Long id, String name, String description, BigDecimal priceEur,
                       boolean available, String imageUrl) {
    static PublicLunchItem from(LunchMenuItem item, ContentTranslations.Text text) {
        return new PublicLunchItem(item.getId(), text.first(), text.description(), item.getPriceEur(),
                item.isAvailable(), item.getImageUrl());
    }
}

record PublicLunchDay(DayOfWeek dayOfWeek, List<PublicLunchItem> items) {}
record PublicLunchMenuResponse(List<PublicLunchDay> days) {}
