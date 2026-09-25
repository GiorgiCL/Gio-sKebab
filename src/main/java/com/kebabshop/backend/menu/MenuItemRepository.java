package com.kebabshop.backend.menu;

import org.springframework.data.jpa.repository.JpaRepository;

interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    boolean existsByCategoryId(Long categoryId);
}
