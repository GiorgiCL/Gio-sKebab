package com.kebabshop.backend.menu;

import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
class MenuService {
    private static final Comparator<MenuCategory> CATEGORY_ORDER = Comparator
            .comparingInt(MenuCategory::getDisplayOrder).thenComparing(MenuCategory::getId);
    private static final Comparator<MenuItem> ITEM_ORDER = Comparator
            .comparingInt(MenuItem::getDisplayOrder).thenComparing(MenuItem::getId);

    private final MenuCategoryRepository categories;
    private final MenuItemRepository items;
    private final EntityManager entityManager;

    MenuService(MenuCategoryRepository categories, MenuItemRepository items, EntityManager entityManager) {
        this.categories = categories;
        this.items = items;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    PublicMenuResponse publicMenu() {
        List<MenuItem> visibleItems = items.findAll().stream().filter(MenuItem::isActive)
                .sorted(ITEM_ORDER).toList();
        var visibleCategories = categories.findAll().stream().filter(MenuCategory::isActive)
                .sorted(CATEGORY_ORDER).map(category -> {
                    var categoryItems = visibleItems.stream()
                            .filter(item -> item.getCategoryId().equals(category.getId()))
                            .map(PublicItem::from).toList();
                    return new PublicCategory(category.getId(), category.getName(), categoryItems);
                }).filter(category -> !category.items().isEmpty()).toList();
        return new PublicMenuResponse(visibleCategories);
    }

    @Transactional(readOnly = true)
    List<CategoryResponse> categories() {
        return categories.findAll().stream().sorted(CATEGORY_ORDER).map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    CategoryResponse category(Long id) {
        return CategoryResponse.from(requireCategory(id));
    }

    @Transactional
    CategoryResponse createCategory(CategoryRequest request) {
        var category = new MenuCategory(request.name(), request.displayOrder(), request.active());
        entityManager.persist(category);
        entityManager.flush();
        entityManager.refresh(category);
        return CategoryResponse.from(category);
    }

    @Transactional
    CategoryResponse replaceCategory(Long id, CategoryRequest request) {
        var category = requireCategory(id);
        category.replace(request.name(), request.displayOrder(), request.active());
        entityManager.flush();
        entityManager.refresh(category);
        return CategoryResponse.from(category);
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
        return items.findAll().stream().sorted(Comparator.comparing(MenuItem::getCategoryId)
                .thenComparing(ITEM_ORDER)).map(ItemResponse::from).toList();
    }

    @Transactional(readOnly = true)
    ItemResponse item(Long id) {
        return ItemResponse.from(requireItem(id));
    }

    @Transactional
    ItemResponse createItem(ItemRequest request) {
        requireCategory(request.categoryId());
        var item = new MenuItem(request.categoryId(), request.name(), request.description(), request.priceEur(),
                request.active(), request.available(), request.featured(), request.imageUrl(), request.displayOrder());
        entityManager.persist(item);
        entityManager.flush();
        entityManager.refresh(item);
        return ItemResponse.from(item);
    }

    @Transactional
    ItemResponse replaceItem(Long id, ItemRequest request) {
        var item = requireItem(id);
        requireCategory(request.categoryId());
        item.replace(request.categoryId(), request.name(), request.description(), request.priceEur(),
                request.active(), request.available(), request.featured(), request.imageUrl(), request.displayOrder());
        entityManager.flush();
        entityManager.refresh(item);
        return ItemResponse.from(item);
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
}
