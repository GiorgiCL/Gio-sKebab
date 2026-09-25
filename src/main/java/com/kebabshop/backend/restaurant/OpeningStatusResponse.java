package com.kebabshop.backend.restaurant;

import java.time.LocalDate;
import java.time.LocalTime;

public record OpeningStatusResponse(boolean openNow, boolean closedToday, LocalDate localDate, LocalTime localTime,
                                    String timeZone, Source source, LocalTime openingTime, LocalTime closingTime) {
    public enum Source { WEEKLY, SPECIAL }
}
