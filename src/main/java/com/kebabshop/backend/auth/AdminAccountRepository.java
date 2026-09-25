package com.kebabshop.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Short> {
    Optional<AdminAccount> findByEmail(String email);
}
