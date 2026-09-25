package com.kebabshop.backend.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;

interface WeeklyOpeningHoursRepository extends JpaRepository<WeeklyOpeningHours, DayOfWeek> {}
