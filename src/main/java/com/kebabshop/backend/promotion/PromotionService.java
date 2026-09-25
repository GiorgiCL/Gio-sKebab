package com.kebabshop.backend.promotion;

import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
class PromotionService {
    private static final Comparator<Promotion> ORDER = Comparator
            .comparingInt(Promotion::getDisplayOrder).thenComparing(Promotion::getId);

    private final PromotionRepository promotions;
    private final EntityManager entityManager;
    private final Clock clock;
    private final ZoneId zone;

    PromotionService(PromotionRepository promotions, EntityManager entityManager, Clock clock, ZoneId restaurantZone) {
        this.promotions = promotions;
        this.entityManager = entityManager;
        this.clock = clock;
        this.zone = restaurantZone;
    }

    @Transactional(readOnly = true)
    PublicPromotionsResponse publicPromotions() {
        Instant now = clock.instant();
        var visible = promotions.findAll().stream().filter(Promotion::isActive)
                .filter(promotion -> promotion.getStartsAt() == null || !now.isBefore(promotion.getStartsAt()))
                .filter(promotion -> promotion.getEndsAt() == null || now.isBefore(promotion.getEndsAt()))
                .sorted(ORDER).map(promotion -> PublicPromotion.from(promotion, zone)).toList();
        return new PublicPromotionsResponse(zone.getId(), visible);
    }

    @Transactional(readOnly = true)
    List<PromotionResponse> list() {
        return promotions.findAll().stream().sorted(ORDER).map(promotion -> PromotionResponse.from(promotion, zone)).toList();
    }

    @Transactional(readOnly = true)
    PromotionResponse get(Long id) {
        return PromotionResponse.from(requirePromotion(id), zone);
    }

    @Transactional
    PromotionResponse create(PromotionRequest request) {
        var promotion = new Promotion(request.title(), request.description(), request.active(),
                toInstant(request.startsAt()), toInstant(request.endsAt()), request.displayOrder());
        entityManager.persist(promotion);
        entityManager.flush();
        entityManager.refresh(promotion);
        return PromotionResponse.from(promotion, zone);
    }

    @Transactional
    PromotionResponse replace(Long id, PromotionRequest request) {
        var promotion = requirePromotion(id);
        promotion.replace(request.title(), request.description(), request.active(),
                toInstant(request.startsAt()), toInstant(request.endsAt()), request.displayOrder());
        entityManager.flush();
        entityManager.refresh(promotion);
        return PromotionResponse.from(promotion, zone);
    }

    @Transactional
    void delete(Long id) {
        promotions.delete(requirePromotion(id));
        entityManager.flush();
    }

    private Instant toInstant(LocalDateTime localDateTime) {
        if (localDateTime == null) return null;
        try {
            var offsets = zone.getRules().getValidOffsets(localDateTime);
            if (offsets.size() != 1) {
                throw new IllegalArgumentException("Promotion time is ambiguous or invalid in restaurant.time-zone");
            }
            return localDateTime.atOffset(offsets.getFirst()).toInstant();
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid promotion date/time", exception);
        }
    }

    private Promotion requirePromotion(Long id) {
        return promotions.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion does not exist"));
    }
}
