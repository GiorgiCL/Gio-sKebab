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

import java.time.Instant;

@Entity
@Table(name = "menu_category")
class MenuCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank @Size(max = 160)
    @Column(nullable = false, length = 160)
    private String name;
    @Column(nullable = false)
    private int displayOrder;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected MenuCategory() {}

    MenuCategory(String name, int displayOrder, boolean active) {
        replace(name, displayOrder, active);
    }

    void replace(String name, int displayOrder, boolean active) {
        if (name == null || name.isBlank() || name.length() > 160 || displayOrder < 0) {
            throw new IllegalArgumentException("Invalid menu category");
        }
        this.name = name;
        this.displayOrder = displayOrder;
        this.active = active;
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
    String getName() { return name; }
    int getDisplayOrder() { return displayOrder; }
    boolean isActive() { return active; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
