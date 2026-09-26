package com.kebabshop.backend.restaurant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import com.kebabshop.backend.ContentTranslations;
import com.kebabshop.backend.ContentTranslations.Kind;
import com.kebabshop.backend.ContentTranslations.Text;

@Service
@Transactional(readOnly = true)
class RestaurantPublicService {
    private final RestaurantProfileRepository profiles;
    private final WeeklyOpeningHoursRepository weeklyHours;
    private final SpecialOpeningHoursRepository specialHours;
    private final Clock clock;
    private final ZoneId zone;
    private final ContentTranslations translations;

    RestaurantPublicService(RestaurantProfileRepository profiles, WeeklyOpeningHoursRepository weeklyHours,
                            SpecialOpeningHoursRepository specialHours, Clock clock, ZoneId restaurantZone,
                            ContentTranslations translations) {
        this.profiles = profiles;
        this.weeklyHours = weeklyHours;
        this.specialHours = specialHours;
        this.clock = clock;
        this.zone = restaurantZone;
        this.translations = translations;
    }

    RestaurantResponse restaurant(String lang) {
        lang = ContentTranslations.locale(lang);
        var profile = requireProfile();
        var text = ContentTranslations.resolve(lang, new Text(profile.getDisplayName(), profile.getDescription()),
                translations.forId(Kind.PROFILE, 1));
        return RestaurantResponse.from(profile, text.first(), text.description(), null);
    }

    OpeningHoursResponse openingHours() {
        requireProfile();
        List<WeeklyOpeningHours> weekly = requireWeeklySchedule();
        LocalDate today = clock.instant().atZone(zone).toLocalDate();
        return new OpeningHoursResponse(zone.getId(),
                weekly.stream().sorted(Comparator.comparing(WeeklyOpeningHours::getDayOfWeek))
                        .map(OpeningHoursResponse.WeeklyDay::from).toList(),
                specialHours.findBySpecialDateGreaterThanEqualOrderBySpecialDate(today).stream()
                        .map(OpeningHoursResponse.SpecialDate::from).toList());
    }

    OpeningStatusResponse openingStatus() {
        requireProfile();
        List<WeeklyOpeningHours> weekly = requireWeeklySchedule();
        var localNow = clock.instant().atZone(zone);
        LocalDate date = localNow.toLocalDate();
        LocalTime time = localNow.toLocalTime();
        var special = specialHours.findById(date);
        if (special.isPresent()) {
            var hours = special.get();
            return status(date, time, OpeningStatusResponse.Source.SPECIAL,
                    hours.isOpen(), hours.getOpeningTime(), hours.getClosingTime());
        }
        DayOfWeek day = date.getDayOfWeek();
        WeeklyOpeningHours hours = weekly.stream().filter(entry -> entry.getDayOfWeek() == day).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Opening hours are not configured"));
        return status(date, time, OpeningStatusResponse.Source.WEEKLY,
                hours.isOpen(), hours.getOpeningTime(), hours.getClosingTime());
    }

    private OpeningStatusResponse status(LocalDate date, LocalTime time, OpeningStatusResponse.Source source,
                                         boolean open, LocalTime opening, LocalTime closing) {
        boolean openNow = open && !time.isBefore(opening) && time.isBefore(closing);
        return new OpeningStatusResponse(openNow, !open, date, time, zone.getId(), source, opening, closing);
    }

    private RestaurantProfile requireProfile() {
        return profiles.findById((short) 1)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant profile is not configured"));
    }

    private List<WeeklyOpeningHours> requireWeeklySchedule() {
        List<WeeklyOpeningHours> weekly = weeklyHours.findAll();
        if (weekly.size() != DayOfWeek.values().length) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Opening hours are not configured");
        }
        return weekly;
    }
}
