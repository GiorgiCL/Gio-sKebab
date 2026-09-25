package com.kebabshop.backend.restaurant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.net.URI;

@Entity
@Table(name = "restaurant_profile")
public class RestaurantProfile {

    @Id
    private Short id = 1;

    @NotBlank @Size(max = 160)
    @Column(nullable = false, length = 160)
    private String displayName;

    @NotBlank @Size(max = 1000)
    @Column(nullable = false, length = 1000)
    private String description;

    @NotBlank @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String address;

    @NotBlank @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String phone;

    @Email @Size(max = 254)
    @Column(length = 254)
    private String email;

    @NotBlank @Size(max = 2048)
    @Column(nullable = false, length = 2048)
    private String googleMapsUrl;

    @Size(max = 2048)
    @Column(length = 2048)
    private String woltUrl;

    @Size(max = 2048)
    @Column(length = 2048)
    private String boltFoodUrl;

    @Size(max = 2048)
    @Column(length = 2048)
    private String instagramUrl;

    @Size(max = 2048)
    @Column(length = 2048)
    private String facebookUrl;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected RestaurantProfile() {}

    public RestaurantProfile(String displayName, String description, String address, String phone,
                             String email, String googleMapsUrl, String woltUrl, String boltFoodUrl,
                             String instagramUrl, String facebookUrl) {
        this.displayName = displayName;
        this.description = description;
        this.address = address;
        this.phone = phone;
        this.email = email;
        validateHttpsUrl(googleMapsUrl, true);
        validateHttpsUrl(woltUrl, false);
        validateHttpsUrl(boltFoodUrl, false);
        validateHttpsUrl(instagramUrl, false);
        validateHttpsUrl(facebookUrl, false);
        this.googleMapsUrl = googleMapsUrl;
        this.woltUrl = woltUrl;
        this.boltFoodUrl = boltFoodUrl;
        this.instagramUrl = instagramUrl;
        this.facebookUrl = facebookUrl;
    }

    private static void validateHttpsUrl(String value, boolean required) {
        if (value == null && !required) return;
        if (value == null) throw new IllegalArgumentException("Google Maps URL is required");
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getHost().isBlank() || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("External URLs must be HTTPS links with a host");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("External URLs must be HTTPS links with a host", ex);
        }
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

    public Short getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getGoogleMapsUrl() { return googleMapsUrl; }
    public String getWoltUrl() { return woltUrl; }
    public String getBoltFoodUrl() { return boltFoodUrl; }
    public String getInstagramUrl() { return instagramUrl; }
    public String getFacebookUrl() { return facebookUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
