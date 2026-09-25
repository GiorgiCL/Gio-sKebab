package com.kebabshop.backend.restaurant;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

@Entity
@Table(name = "weekly_opening_hours")
public class WeeklyOpeningHours {
    @Id @Enumerated(EnumType.STRING)
    @Column(length = 9)
    private DayOfWeek dayOfWeek;
    @Column(nullable = false)
    private boolean isOpen;
    private LocalTime openingTime;
    private LocalTime closingTime;

    protected WeeklyOpeningHours() {}

    public WeeklyOpeningHours(DayOfWeek dayOfWeek, boolean isOpen, LocalTime openingTime, LocalTime closingTime) {
        this.dayOfWeek = Objects.requireNonNull(dayOfWeek);
        HoursRule.validate(isOpen, openingTime, closingTime);
        this.isOpen = isOpen;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
    }

    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public boolean isOpen() { return isOpen; }
    public LocalTime getOpeningTime() { return openingTime; }
    public LocalTime getClosingTime() { return closingTime; }
}
