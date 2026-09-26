package com.kebabshop.backend.restaurant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

record ProfileTranslation(String displayName, String description) {}

record ProfileRequest(
        @NotBlank @Size(max = 160) String displayName,
        @NotBlank @Size(max = 1000) String description,
        @NotBlank @Size(max = 500) String address,
        @NotBlank @Size(max = 50) String phone,
        @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 2048) String googleMapsUrl,
        @Size(max = 2048) String woltUrl,
        @Size(max = 2048) String boltFoodUrl,
        @Size(max = 2048) String instagramUrl,
        @Size(max = 2048) String facebookUrl,
        Map<String, ProfileTranslation> translations) {}

record WeeklyScheduleRequest(@NotNull @Size(min = 7, max = 7) List<@NotNull @Valid WeeklyDayRequest> days) {}

record WeeklyDayRequest(@NotNull DayOfWeek dayOfWeek, @NotNull Boolean open,
                        LocalTime openingTime, LocalTime closingTime) {}

record SpecialDateRequest(@NotNull LocalDate date, @NotNull Boolean open,
                          LocalTime openingTime, LocalTime closingTime) {}

record SpecialDateReplacementRequest(@NotNull Boolean open, LocalTime openingTime, LocalTime closingTime) {}
