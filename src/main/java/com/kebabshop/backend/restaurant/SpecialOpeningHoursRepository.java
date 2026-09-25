package com.kebabshop.backend.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

interface SpecialOpeningHoursRepository extends JpaRepository<SpecialOpeningHours, LocalDate> {
    List<SpecialOpeningHours> findBySpecialDateGreaterThanEqualOrderBySpecialDate(LocalDate date);
}
