package com.kebabshop.backend.restaurant;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

@Entity
@Table(name = "special_opening_hours")
public class SpecialOpeningHours {
    @Id
    private LocalDate specialDate;
    @Column(nullable = false)
    private boolean isOpen;
    private LocalTime openingTime;
    private LocalTime closingTime;

    protected SpecialOpeningHours() {}

    public SpecialOpeningHours(LocalDate specialDate, boolean isOpen, LocalTime openingTime, LocalTime closingTime) {
        this.specialDate = Objects.requireNonNull(specialDate);
        HoursRule.validate(isOpen, openingTime, closingTime);
        this.isOpen = isOpen;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
    }

    public void replace(boolean isOpen, LocalTime openingTime, LocalTime closingTime) {
        HoursRule.validate(isOpen, openingTime, closingTime);
        this.isOpen = isOpen;
        this.openingTime = openingTime;
        this.closingTime = closingTime;
    }

    public LocalDate getSpecialDate() { return specialDate; }
    public boolean isOpen() { return isOpen; }
    public LocalTime getOpeningTime() { return openingTime; }
    public LocalTime getClosingTime() { return closingTime; }
}
