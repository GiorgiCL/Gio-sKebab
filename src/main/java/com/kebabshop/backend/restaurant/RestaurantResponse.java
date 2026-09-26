package com.kebabshop.backend.restaurant;

import java.time.Instant;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.kebabshop.backend.ContentTranslations;

public record RestaurantResponse(String displayName, String description, String address, String phone,
                                 String email, String googleMapsUrl, String woltUrl, String boltFoodUrl,
                                 String instagramUrl, String facebookUrl, Instant createdAt, Instant updatedAt,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, ProfileTranslation> translations) {
    static RestaurantResponse from(RestaurantProfile profile) {
        return from(profile, profile.getDisplayName(), profile.getDescription(), null);
    }

    static RestaurantResponse from(RestaurantProfile profile, String displayName, String description,
                                   Map<String, ProfileTranslation> translations) {
        return new RestaurantResponse(displayName, description, profile.getAddress(),
                profile.getPhone(), profile.getEmail(), profile.getGoogleMapsUrl(), profile.getWoltUrl(),
                profile.getBoltFoodUrl(), profile.getInstagramUrl(), profile.getFacebookUrl(),
                profile.getCreatedAt(), profile.getUpdatedAt(), translations);
    }
}
