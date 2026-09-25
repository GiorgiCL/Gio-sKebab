package com.kebabshop.backend.restaurant;

import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

@Service
class RestaurantAdminService {
    private final RestaurantProfileRepository profiles;
    private final WeeklyOpeningHoursRepository weekly;
    private final SpecialOpeningHoursRepository special;
    private final EntityManager entityManager;

    RestaurantAdminService(RestaurantProfileRepository profiles, WeeklyOpeningHoursRepository weekly,
                           SpecialOpeningHoursRepository special, EntityManager entityManager) {
        this.profiles = profiles;
        this.weekly = weekly;
        this.special = special;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    RestaurantResponse profile() {
        return RestaurantResponse.from(profiles.findById((short) 1).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant profile is not configured")));
    }

    @Transactional
    ProfileResult replaceProfile(ProfileRequest request) {
        if (request.email() != null && request.email().isBlank()) {
            throw new IllegalArgumentException("Optional email must be omitted or nonblank");
        }
        var existing = profiles.findById((short) 1);
        RestaurantProfile profile;
        if (existing.isPresent()) {
            profile = existing.get();
            profile.replace(request.displayName(), request.description(), request.address(), request.phone(),
                    request.email(), request.googleMapsUrl(), request.woltUrl(), request.boltFoodUrl(),
                    request.instagramUrl(), request.facebookUrl());
        } else {
            profile = new RestaurantProfile(request.displayName(), request.description(), request.address(),
                    request.phone(), request.email(), request.googleMapsUrl(), request.woltUrl(),
                    request.boltFoodUrl(), request.instagramUrl(), request.facebookUrl());
            entityManager.persist(profile);
        }
        entityManager.flush();
        entityManager.refresh(profile);
        return new ProfileResult(RestaurantResponse.from(profile), existing.isEmpty());
    }

    @Transactional(readOnly = true)
    List<OpeningHoursResponse.WeeklyDay> weeklySchedule() {
        return weekly.findAll().stream().sorted(Comparator.comparing(WeeklyOpeningHours::getDayOfWeek))
                .map(OpeningHoursResponse.WeeklyDay::from).toList();
    }

    @Transactional
    List<OpeningHoursResponse.WeeklyDay> replaceWeekly(WeeklyScheduleRequest request) {
        if (request.days() == null || request.days().size() != DayOfWeek.values().length) {
            throw new IllegalArgumentException("Exactly seven weekdays are required");
        }
        var seen = EnumSet.noneOf(DayOfWeek.class);
        var replacement = new ArrayList<WeeklyOpeningHours>();
        for (WeeklyDayRequest day : request.days()) {
            if (day == null || day.dayOfWeek() == null || day.open() == null
                    || !seen.add(day.dayOfWeek())) {
                throw new IllegalArgumentException("Each weekday must appear exactly once");
            }
            replacement.add(new WeeklyOpeningHours(day.dayOfWeek(), day.open(),
                    day.openingTime(), day.closingTime()));
        }
        weekly.deleteAllInBatch();
        weekly.saveAllAndFlush(replacement);
        return replacement.stream().sorted(Comparator.comparing(WeeklyOpeningHours::getDayOfWeek))
                .map(OpeningHoursResponse.WeeklyDay::from).toList();
    }

    @Transactional(readOnly = true)
    List<OpeningHoursResponse.SpecialDate> specialDates() {
        return special.findAll().stream().sorted(Comparator.comparing(SpecialOpeningHours::getSpecialDate))
                .map(OpeningHoursResponse.SpecialDate::from).toList();
    }

    @Transactional
    OpeningHoursResponse.SpecialDate createSpecial(SpecialDateRequest request) {
        if (request.date() == null || request.open() == null) {
            throw new IllegalArgumentException("Date and open state are required");
        }
        if (special.existsById(request.date())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Special-date override already exists");
        }
        var hours = new SpecialOpeningHours(request.date(), request.open(),
                request.openingTime(), request.closingTime());
        entityManager.persist(hours);
        entityManager.flush();
        return OpeningHoursResponse.SpecialDate.from(hours);
    }

    @Transactional
    OpeningHoursResponse.SpecialDate replaceSpecial(LocalDate date, SpecialDateReplacementRequest request) {
        if (request.open() == null) {
            throw new IllegalArgumentException("Open state is required");
        }
        var hours = special.findById(date).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Special-date override does not exist"));
        hours.replace(request.open(), request.openingTime(), request.closingTime());
        entityManager.flush();
        return OpeningHoursResponse.SpecialDate.from(hours);
    }

    @Transactional
    void deleteSpecial(LocalDate date) {
        var hours = special.findById(date).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Special-date override does not exist"));
        special.delete(hours);
        entityManager.flush();
    }

    record ProfileResult(RestaurantResponse response, boolean created) {}
}
