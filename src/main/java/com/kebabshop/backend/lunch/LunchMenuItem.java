package com.kebabshop.backend.lunch;

import com.kebabshop.backend.menu.MenuItemImageUrls;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;

@Entity
@Table(name = "lunch_menu_item")
class LunchMenuItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 9)
    private DayOfWeek dayOfWeek;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(length = 1000)
    private String description;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priceEur;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private boolean available;
    @Column(nullable = false)
    private int displayOrder;
    @Column(length = 2048)
    private String imageUrl;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected LunchMenuItem() {}

    LunchMenuItem(DayOfWeek dayOfWeek, String name, String description, BigDecimal priceEur,
                  boolean active, boolean available, int displayOrder, String imageUrl) {
        replace(dayOfWeek, name, description, priceEur, active, available, displayOrder, imageUrl);
    }

    void replace(DayOfWeek dayOfWeek, String name, String description, BigDecimal priceEur,
                 boolean active, boolean available, int displayOrder, String imageUrl) {
        if (description != null && description.isBlank()) description = null;
        if (dayOfWeek == null || name == null || name.isBlank() || name.length() > 160
                || description != null && description.length() > 1000 || priceEur == null
                || priceEur.signum() <= 0 || priceEur.precision() - priceEur.scale() > 8
                || priceEur.scale() > 2 || displayOrder < 0) throw new IllegalArgumentException("Invalid lunch item");
        this.dayOfWeek = dayOfWeek;
        this.name = name;
        this.description = description;
        this.priceEur = priceEur;
        this.active = active;
        this.available = available;
        this.displayOrder = displayOrder;
        this.imageUrl = MenuItemImageUrls.normalize(imageUrl);
    }

    @PrePersist void onCreate() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    Long getId() { return id; }
    DayOfWeek getDayOfWeek() { return dayOfWeek; }
    String getName() { return name; }
    String getDescription() { return description; }
    BigDecimal getPriceEur() { return priceEur; }
    boolean isActive() { return active; }
    boolean isAvailable() { return available; }
    int getDisplayOrder() { return displayOrder; }
    String getImageUrl() { return imageUrl; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
