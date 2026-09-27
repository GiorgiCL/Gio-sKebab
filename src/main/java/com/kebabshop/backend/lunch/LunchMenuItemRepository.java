package com.kebabshop.backend.lunch;

import org.springframework.data.jpa.repository.JpaRepository;

interface LunchMenuItemRepository extends JpaRepository<LunchMenuItem, Long> {}
