package com.kebabshop.backend.restaurant;

import java.time.Instant;

public record RestaurantResponse(String displayName, String description, String address, String phone,
                                 String email, String googleMapsUrl, String woltUrl, String boltFoodUrl,
                                 String instagramUrl, String facebookUrl, Instant createdAt, Instant updatedAt) {
    static RestaurantResponse from(RestaurantProfile profile) {
        return new RestaurantResponse(profile.getDisplayName(), profile.getDescription(), profile.getAddress(),
                profile.getPhone(), profile.getEmail(), profile.getGoogleMapsUrl(), profile.getWoltUrl(),
                profile.getBoltFoodUrl(), profile.getInstagramUrl(), profile.getFacebookUrl(),
                profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
