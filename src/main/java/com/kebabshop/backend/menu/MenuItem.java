package com.kebabshop.backend.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.net.URI;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "menu_item")
class MenuItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long categoryId;
    @NotBlank @Size(max = 160)
    @Column(nullable = false, length = 160)
    private String name;
    @NotBlank @Size(max = 1000)
    @Column(nullable = false, length = 1000)
    private String description;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priceEur;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private boolean available;
    @Column(nullable = false)
    private boolean featured;
    @Size(max = 2048)
    @Column(length = 2048)
    private String imageUrl;
    @Column(nullable = false)
    private int displayOrder;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected MenuItem() {}

    MenuItem(Long categoryId, String name, String description, BigDecimal priceEur,
             boolean active, boolean available, boolean featured, String imageUrl, int displayOrder) {
        replace(categoryId, name, description, priceEur, active, available, featured, imageUrl, displayOrder);
    }

    void replace(Long categoryId, String name, String description, BigDecimal priceEur,
                 boolean active, boolean available, boolean featured, String imageUrl, int displayOrder) {
        if (categoryId == null || categoryId <= 0 || name == null || name.isBlank() || name.length() > 160
                || description == null || description.isBlank() || description.length() > 1000
                || priceEur == null || priceEur.signum() <= 0 || priceEur.precision() - priceEur.scale() > 8
                || priceEur.scale() > 2 || displayOrder < 0) {
            throw new IllegalArgumentException("Invalid menu item");
        }
        imageUrl = normalizeImageUrl(imageUrl);
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.priceEur = priceEur;
        this.active = active;
        this.available = available;
        this.featured = featured;
        this.imageUrl = imageUrl;
        this.displayOrder = displayOrder;
    }

    static String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String normalized = imageUrl.trim();
        if (normalized.length() > 2048) throw new IllegalArgumentException("Invalid menu item image URL");
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Invalid menu item image URL");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid menu item image URL", exception);
        }
        return normalized;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    Long getId() { return id; }
    Long getCategoryId() { return categoryId; }
    String getName() { return name; }
    String getDescription() { return description; }
    BigDecimal getPriceEur() { return priceEur; }
    boolean isActive() { return active; }
    boolean isAvailable() { return available; }
    boolean isFeatured() { return featured; }
    String getImageUrl() { return imageUrl; }
    int getDisplayOrder() { return displayOrder; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
