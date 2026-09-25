package com.kebabshop.backend.restaurant;

import java.time.LocalTime;

final class HoursRule {
    private HoursRule() {}

    static void validate(boolean open, LocalTime opening, LocalTime closing) {
        if (open && (opening == null || closing == null || !closing.isAfter(opening))) {
            throw new IllegalArgumentException("Open days require opening and closing times on the same date");
        }
        if (!open && (opening != null || closing != null)) {
            throw new IllegalArgumentException("Closed days must not have opening or closing times");
        }
    }
}
