package com.kebabshop.backend.promotion;

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
@Table(name = "promotion")
class Promotion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank @Size(max = 160)
    @Column(nullable = false, length = 160)
    private String title;
    @Size(max = 500)
    @Column(length = 500)
    private String description;
    @Column(nullable = false)
    private boolean active;
    private Instant startsAt;
    private Instant endsAt;
    @Column(nullable = false)
    private int displayOrder;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    protected Promotion() {}

    Promotion(String title, String description, boolean active, Instant startsAt, Instant endsAt, int displayOrder) {
        replace(title, description, active, startsAt, endsAt, displayOrder);
    }

    void replace(String title, String description, boolean active,
                 Instant startsAt, Instant endsAt, int displayOrder) {
        if (title == null || title.isBlank() || title.length() > 160
                || description != null && (description.isBlank() || description.length() > 500)
                || displayOrder < 0 || startsAt != null && endsAt != null && endsAt.isBefore(startsAt)) {
            throw new IllegalArgumentException("Invalid promotion");
        }
        this.title = title;
        this.description = description;
        this.active = active;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.displayOrder = displayOrder;
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
    String getTitle() { return title; }
    String getDescription() { return description; }
    boolean isActive() { return active; }
    Instant getStartsAt() { return startsAt; }
    Instant getEndsAt() { return endsAt; }
    int getDisplayOrder() { return displayOrder; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
}
