package com.kebabshop.backend.lunch;

import com.kebabshop.backend.ContentTranslations;
import com.kebabshop.backend.ContentTranslations.Kind;
import com.kebabshop.backend.ContentTranslations.Text;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
class LunchService {
    private static final Comparator<LunchMenuItem> ORDER = Comparator
            .comparing(LunchMenuItem::getDayOfWeek)
            .thenComparingInt(LunchMenuItem::getDisplayOrder).thenComparing(LunchMenuItem::getId);
    private final LunchMenuItemRepository items;
    private final EntityManager entityManager;
    private final ContentTranslations translations;

    LunchService(LunchMenuItemRepository items, EntityManager entityManager, ContentTranslations translations) {
        this.items = items;
        this.entityManager = entityManager;
        this.translations = translations;
    }

    @Transactional(readOnly = true)
    PublicLunchMenuResponse publicMenu(String lang) {
        String locale = ContentTranslations.locale(lang);
        var all = translations.all(Kind.LUNCH_ITEM);
        var active = items.findAll().stream().filter(LunchMenuItem::isActive).sorted(ORDER).toList();
        return new PublicLunchMenuResponse(java.util.Arrays.stream(DayOfWeek.values()).map(day ->
                new PublicLunchDay(day, active.stream().filter(item -> item.getDayOfWeek() == day)
                        .map(item -> PublicLunchItem.from(item, ContentTranslations.resolve(locale,
                                new Text(item.getName(), item.getDescription()),
                                all.getOrDefault(item.getId(), Map.of())))).toList())).toList());
    }

    @Transactional(readOnly = true)
    List<LunchResponse> items() {
        var all = translations.all(Kind.LUNCH_ITEM);
        return items.findAll().stream().sorted(ORDER)
                .map(item -> LunchResponse.from(item, all.getOrDefault(item.getId(), Map.of()))).toList();
    }

    @Transactional(readOnly = true)
    LunchResponse item(Long id) {
        return LunchResponse.from(require(id), translations.forId(Kind.LUNCH_ITEM, id));
    }

    @Transactional
    com.kebabshop.backend.image.ImageUploadCoordinator.ImageChange<LunchResponse> replaceManagedImage(Long id, String url, String publicId) {
        var item = require(id);
        String oldPublicId = item.replaceManagedImage(url, publicId);
        entityManager.flush();
        return new com.kebabshop.backend.image.ImageUploadCoordinator.ImageChange<>(
                LunchResponse.from(item, translations.forId(Kind.LUNCH_ITEM, id)), oldPublicId);
    }

    @Transactional
    com.kebabshop.backend.image.ImageUploadCoordinator.ImageChange<LunchResponse> removeManagedImage(Long id) {
        var item = require(id);
        String oldPublicId = item.replaceManagedImage(null, null);
        entityManager.flush();
        return new com.kebabshop.backend.image.ImageUploadCoordinator.ImageChange<>(
                LunchResponse.from(item, translations.forId(Kind.LUNCH_ITEM, id)), oldPublicId);
    }

    @Transactional
    LunchResponse create(LunchRequest request) {
        var item = new LunchMenuItem(request.dayOfWeek(), request.name(), request.description(), request.priceEur(),
                request.active(), request.available(), request.displayOrder(), request.imageUrl());
        entityManager.persist(item);
        entityManager.flush();
        translations.replace(Kind.LUNCH_ITEM, item.getId(), texts(request.translations()),
                new Text(item.getName(), item.getDescription()));
        entityManager.refresh(item);
        return item(item.getId());
    }

    @Transactional
    com.kebabshop.backend.image.ImageUploadCoordinator.ImageChange<LunchResponse> replace(Long id, LunchRequest request) {
        var item = require(id);
        String oldImageUrl = item.getImageUrl();
        String oldPublicId = item.getImagePublicId();
        item.replace(request.dayOfWeek(), request.name(), request.description(), request.priceEur(),
                request.active(), request.available(), request.displayOrder(), request.imageUrl());
        entityManager.flush();
        translations.replace(Kind.LUNCH_ITEM, id, texts(request.translations()),
                new Text(item.getName(), item.getDescription()));
        entityManager.refresh(item);
        return new com.kebabshop.backend.image.ImageUploadCoordinator.ImageChange<>(
                item(id), java.util.Objects.equals(oldImageUrl, item.getImageUrl()) ? null : oldPublicId);
    }

    @Transactional
    com.kebabshop.backend.image.ImageUploadCoordinator.ImageChange<Void> delete(Long id) {
        var item = require(id);
        String oldPublicId = item.getImagePublicId();
        items.delete(item);
        entityManager.flush();
        return new com.kebabshop.backend.image.ImageUploadCoordinator.ImageChange<>(null, oldPublicId);
    }

    private LunchMenuItem require(Long id) {
        return items.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Lunch item does not exist"));
    }

    private Map<String, Text> texts(Map<String, LunchTranslation> input) {
        if (input == null) return null;
        var result = new LinkedHashMap<String, Text>();
        input.forEach((locale, value) -> {
            if (value == null) throw new IllegalArgumentException("Translation must be an object");
            result.put(locale, new Text(value.name(), value.description()));
        });
        return result;
    }
}
