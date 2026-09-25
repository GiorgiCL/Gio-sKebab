package com.kebabshop.backend.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.nio.charset.StandardCharsets;

@Service
public class AdminProvisioningService {
    private final AdminAccountRepository accounts;
    private final PasswordEncoder encoder;

    public AdminProvisioningService(AdminAccountRepository accounts, PasswordEncoder encoder) {
        this.accounts = accounts;
        this.encoder = encoder;
    }

    @Transactional
    public void createFirstAccount(String email, String password) {
        if (accounts.existsById((short) 1)) {
            throw new IllegalStateException("Admin account already exists");
        }
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 254 || !normalized.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("Invalid admin email");
        }
        if (password == null || password.length() < 12 ||
                password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Admin password must contain 12 to 72 UTF-8 bytes");
        }
        accounts.saveAndFlush(new AdminAccount(normalized, encoder.encode(password)));
    }
}
