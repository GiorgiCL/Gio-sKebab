package com.kebabshop.backend.restaurant;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
class RestaurantAdminController {
    private final RestaurantAdminService service;

    RestaurantAdminController(RestaurantAdminService service) {
        this.service = service;
    }

    @GetMapping("/restaurant")
    RestaurantResponse profile() {
        return service.profile();
    }

    @PutMapping("/restaurant")
    ResponseEntity<RestaurantResponse> replaceProfile(@Valid @RequestBody ProfileRequest request) {
        var result = service.replaceProfile(request);
        return result.created()
                ? ResponseEntity.created(URI.create("/api/admin/restaurant")).body(result.response())
                : ResponseEntity.ok(result.response());
    }

    @GetMapping("/opening-hours/weekly")
    List<OpeningHoursResponse.WeeklyDay> weeklySchedule() {
        return service.weeklySchedule();
    }

    @PutMapping("/opening-hours/weekly")
    List<OpeningHoursResponse.WeeklyDay> replaceWeekly(@Valid @RequestBody WeeklyScheduleRequest request) {
        return service.replaceWeekly(request);
    }

    @GetMapping("/opening-hours/special-dates")
    List<OpeningHoursResponse.SpecialDate> specialDates() {
        return service.specialDates();
    }

    @PostMapping("/opening-hours/special-dates")
    ResponseEntity<OpeningHoursResponse.SpecialDate> createSpecial(@Valid @RequestBody SpecialDateRequest request) {
        var result = service.createSpecial(request);
        return ResponseEntity.created(URI.create("/api/admin/opening-hours/special-dates/" + result.date()))
                .body(result);
    }

    @PutMapping("/opening-hours/special-dates/{date}")
    OpeningHoursResponse.SpecialDate replaceSpecial(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody SpecialDateReplacementRequest request) {
        return service.replaceSpecial(date, request);
    }

    @DeleteMapping("/opening-hours/special-dates/{date}")
    ResponseEntity<Void> deleteSpecial(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.deleteSpecial(date);
        return ResponseEntity.noContent().build();
    }
}
