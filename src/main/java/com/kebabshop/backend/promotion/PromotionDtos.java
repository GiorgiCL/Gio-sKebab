package com.kebabshop.backend.promotion;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

record PromotionRequest(@NotBlank @Size(max = 160) String title,
                        @Size(max = 500) String description,
                        @NotNull Boolean active,
                        LocalDateTime startsAt,
                        LocalDateTime endsAt,
                        @NotNull @Min(0) Integer displayOrder) {}

record PromotionResponse(Long id, String title, String description, boolean active,
                         LocalDateTime startsAt, LocalDateTime endsAt, String timeZone,
                         int displayOrder, Instant createdAt, Instant updatedAt) {
    static PromotionResponse from(Promotion promotion, ZoneId zone) {
        return new PromotionResponse(promotion.getId(), promotion.getTitle(), promotion.getDescription(),
                promotion.isActive(), local(promotion.getStartsAt(), zone), local(promotion.getEndsAt(), zone),
                zone.getId(), promotion.getDisplayOrder(), promotion.getCreatedAt(), promotion.getUpdatedAt());
    }

    private static LocalDateTime local(Instant instant, ZoneId zone) {
        return instant == null ? null : instant.atZone(zone).toLocalDateTime();
    }
}

record PublicPromotionsResponse(String timeZone, List<PublicPromotion> promotions) {}
record PublicPromotion(Long id, String title, String description, LocalDateTime startsAt, LocalDateTime endsAt) {
    static PublicPromotion from(Promotion promotion, ZoneId zone) {
        return new PublicPromotion(promotion.getId(), promotion.getTitle(), promotion.getDescription(),
                local(promotion.getStartsAt(), zone), local(promotion.getEndsAt(), zone));
    }

    private static LocalDateTime local(Instant instant, ZoneId zone) {
        return instant == null ? null : instant.atZone(zone).toLocalDateTime();
    }
}
