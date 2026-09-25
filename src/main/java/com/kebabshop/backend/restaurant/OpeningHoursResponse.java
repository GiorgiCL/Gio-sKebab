package com.kebabshop.backend.restaurant;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record OpeningHoursResponse(String timeZone, List<WeeklyDay> weekly, List<SpecialDate> specialDates) {
    public record WeeklyDay(DayOfWeek dayOfWeek, boolean open, LocalTime openingTime, LocalTime closingTime) {
        static WeeklyDay from(WeeklyOpeningHours hours) {
            return new WeeklyDay(hours.getDayOfWeek(), hours.isOpen(), hours.getOpeningTime(), hours.getClosingTime());
        }
    }

    public record SpecialDate(LocalDate date, boolean open, LocalTime openingTime, LocalTime closingTime) {
        static SpecialDate from(SpecialOpeningHours hours) {
            return new SpecialDate(hours.getSpecialDate(), hours.isOpen(), hours.getOpeningTime(), hours.getClosingTime());
        }
    }
}
