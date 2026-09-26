package com.kebabshop.backend.menu;

import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import com.kebabshop.backend.ContentTranslations;
import com.kebabshop.backend.ContentTranslations.Kind;
import com.kebabshop.backend.ContentTranslations.Text;

@Service
class MenuService {
    private static final Comparator<MenuCategory> CATEGORY_ORDER = Comparator
            .comparingInt(MenuCategory::getDisplayOrder).thenComparing(MenuCategory::getId);
    private static final Comparator<MenuItem> ITEM_ORDER = Comparator
            .comparingInt(MenuItem::getDisplayOrder).thenComparing(MenuItem::getId);

    private final MenuCategoryRepository categories;
    private final MenuItemRepository items;
    private final EntityManager entityManager;
    private final ContentTranslations translations;

    MenuService(MenuCategoryRepository categories, MenuItemRepository items, EntityManager entityManager,
                ContentTranslations translations) {
        this.categories = categories;
        this.items = items;
        this.entityManager = entityManager;
        this.translations = translations;
    }

    @Transactional(readOnly = true)
    PublicMenuResponse publicMenu(String lang) {
        lang = ContentTranslations.locale(lang);
        var categoryTexts = translations.all(Kind.CATEGORY);
        var itemTexts = translations.all(Kind.ITEM);
        final String locale = lang;
        List<MenuItem> visibleItems = items.findAll().stream().filter(MenuItem::isActive)
                .sorted(ITEM_ORDER).toList();
        var visibleCategories = categories.findAll().stream().filter(MenuCategory::isActive)
                .sorted(CATEGORY_ORDER).map(category -> {
                    var categoryItems = visibleItems.stream()
                            .filter(item -> item.getCategoryId().equals(category.getId()))
                            .map(item -> PublicItem.from(item, ContentTranslations.resolve(locale,
                                    new Text(item.getName(), item.getDescription()),
                                    itemTexts.getOrDefault(item.getId(), Map.of())))).toList();
                    var text = ContentTranslations.resolve(locale, new Text(category.getName(), null),
                            categoryTexts.getOrDefault(category.getId(), Map.of()));
                    return new PublicCategory(category.getId(), text.first(), categoryItems);
                }).filter(category -> !category.items().isEmpty()).toList();
        return new PublicMenuResponse(visibleCategories);
    }

    @Transactional(readOnly = true)
    List<CategoryResponse> categories() {
        var all = translations.all(Kind.CATEGORY);
        return categories.findAll().stream().sorted(CATEGORY_ORDER)
                .map(category -> CategoryResponse.from(category, all.getOrDefault(category.getId(), Map.of()))).toList();
    }

    @Transactional(readOnly = true)
    CategoryResponse category(Long id) {
        return CategoryResponse.from(requireCategory(id), translations.forId(Kind.CATEGORY, id));
    }

    @Transactional
    CategoryResponse createCategory(CategoryRequest request) {
        var category = new MenuCategory(request.name(), request.displayOrder(), request.active());
        entityManager.persist(category);
        entityManager.flush();
        translations.replace(Kind.CATEGORY, category.getId(), categoryTexts(request.translations()), new Text(category.getName(), null));
        entityManager.refresh(category);
        return CategoryResponse.from(category, translations.forId(Kind.CATEGORY, category.getId()));
    }

    @Transactional
    CategoryResponse replaceCategory(Long id, CategoryRequest request) {
        var category = requireCategory(id);
        category.replace(request.name(), request.displayOrder(), request.active());
        entityManager.flush();
        translations.replace(Kind.CATEGORY, id, categoryTexts(request.translations()), new Text(category.getName(), null));
        entityManager.refresh(category);
        return CategoryResponse.from(category, translations.forId(Kind.CATEGORY, id));
    }

    @Transactional
    void deleteCategory(Long id) {
        var category = requireCategory(id);
        if (items.existsByCategoryId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category still contains items");
        }
        categories.delete(category);
        entityManager.flush();
    }

    @Transactional(readOnly = true)
    List<ItemResponse> items() {
        var all = translations.all(Kind.ITEM);
        return items.findAll().stream().sorted(Comparator.comparing(MenuItem::getCategoryId)
                .thenComparing(ITEM_ORDER)).map(item -> ItemResponse.from(item, all.getOrDefault(item.getId(), Map.of()))).toList();
    }

    @Transactional(readOnly = true)
    ItemResponse item(Long id) {
        return ItemResponse.from(requireItem(id), translations.forId(Kind.ITEM, id));
    }

    @Transactional
    ItemResponse createItem(ItemRequest request) {
        requireCategory(request.categoryId());
        var item = new MenuItem(request.categoryId(), request.name(), request.description(), request.priceEur(),
                request.active(), request.available(), request.featured(), request.imageUrl(), request.displayOrder());
        entityManager.persist(item);
        entityManager.flush();
        translations.replace(Kind.ITEM, item.getId(), itemTexts(request.translations()), new Text(item.getName(), item.getDescription()));
        entityManager.refresh(item);
        return ItemResponse.from(item, translations.forId(Kind.ITEM, item.getId()));
    }

    @Transactional
    ItemResponse replaceItem(Long id, ItemRequest request) {
        var item = requireItem(id);
        requireCategory(request.categoryId());
        item.replace(request.categoryId(), request.name(), request.description(), request.priceEur(),
                request.active(), request.available(), request.featured(), request.imageUrl(), request.displayOrder());
        entityManager.flush();
        translations.replace(Kind.ITEM, id, itemTexts(request.translations()), new Text(item.getName(), item.getDescription()));
        entityManager.refresh(item);
        return ItemResponse.from(item, translations.forId(Kind.ITEM, id));
    }

    @Transactional
    void deleteItem(Long id) {
        items.delete(requireItem(id));
        entityManager.flush();
    }

    private MenuCategory requireCategory(Long id) {
        return categories.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu category does not exist"));
    }

    private MenuItem requireItem(Long id) {
        return items.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Menu item does not exist"));
    }

    private Map<String, Text> categoryTexts(Map<String, CategoryTranslation> input) {
        if (input == null) return null;
        var result = new LinkedHashMap<String, Text>();
        input.forEach((locale, value) -> {
            if (value == null) throw new IllegalArgumentException("Translation must be an object");
            result.put(locale, new Text(value.name(), null));
        });
        return result;
    }

    private Map<String, Text> itemTexts(Map<String, ItemTranslation> input) {
        if (input == null) return null;
        var result = new LinkedHashMap<String, Text>();
        input.forEach((locale, value) -> {
            if (value == null) throw new IllegalArgumentException("Translation must be an object");
            result.put(locale, new Text(value.name(), value.description()));
        });
        return result;
    }
}
