package com.kebabshop.backend.promotion;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import com.kebabshop.backend.ContentTranslations;

record PromotionTranslation(String title, String description) {}

record PromotionRequest(@NotBlank @Size(max = 160) String title,
                        @Size(max = 500) String description,
                        @NotNull Boolean active,
                        LocalDateTime startsAt,
                        LocalDateTime endsAt,
                        @NotNull @Min(0) Integer displayOrder,
                        Map<String, PromotionTranslation> translations) {}

record PromotionResponse(Long id, String title, String description, boolean active,
                         LocalDateTime startsAt, LocalDateTime endsAt, String timeZone,
                         int displayOrder, Instant createdAt, Instant updatedAt,
                         Map<String, PromotionTranslation> translations) {
    static PromotionResponse from(Promotion promotion, ZoneId zone, Map<String, ContentTranslations.Text> translations) {
        return new PromotionResponse(promotion.getId(), promotion.getTitle(), promotion.getDescription(),
                promotion.isActive(), local(promotion.getStartsAt(), zone), local(promotion.getEndsAt(), zone),
                zone.getId(), promotion.getDisplayOrder(), promotion.getCreatedAt(), promotion.getUpdatedAt(),
                ContentTranslations.withCanonical(new ContentTranslations.Text(promotion.getTitle(), promotion.getDescription()), translations)
                        .entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                                entry -> new PromotionTranslation(entry.getValue().first(), entry.getValue().description()))));
    }

    private static LocalDateTime local(Instant instant, ZoneId zone) {
        return instant == null ? null : instant.atZone(zone).toLocalDateTime();
    }
}

record PublicPromotionsResponse(String timeZone, List<PublicPromotion> promotions) {}
record PublicPromotion(Long id, String title, String description, LocalDateTime startsAt, LocalDateTime endsAt) {
    static PublicPromotion from(Promotion promotion, ZoneId zone, ContentTranslations.Text text) {
        return new PublicPromotion(promotion.getId(), text.first(), text.description(),
                local(promotion.getStartsAt(), zone), local(promotion.getEndsAt(), zone));
    }

    private static LocalDateTime local(Instant instant, ZoneId zone) {
        return instant == null ? null : instant.atZone(zone).toLocalDateTime();
    }
}
