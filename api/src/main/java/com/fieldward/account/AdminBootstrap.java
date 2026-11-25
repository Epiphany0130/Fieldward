package com.fieldward.account;

import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class AdminBootstrap implements ApplicationRunner {
    private final AccountMapper accounts;
    private final PasswordEncoder passwords;
    private final Validator validator;
    private final String email;
    private final String password;

    AdminBootstrap(AccountMapper accounts, PasswordEncoder passwords, Validator validator,
                   @Value("${fieldward.admin.email}") String email,
                   @Value("${fieldward.admin.password}") String password) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.validator = validator;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isEmpty() && password.isEmpty()) {
            return;
        }

        var registration = new AuthController.Registration(email, "管理员", password);
        if (!validator.validate(registration).isEmpty() || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Invalid Fieldward admin configuration");
        }

        String normalizedEmail = AuthController.normalizeEmail(email);
        Account existing = accounts.findByEmail(normalizedEmail);
        if (existing == null) {
            accounts.insert(normalizedEmail, "管理员", passwords.encode(password), Account.Role.ADMIN);
            existing = accounts.findByEmail(normalizedEmail);
        }
        if (existing == null || existing.role() != Account.Role.ADMIN) {
            throw new IllegalStateException("The configured admin email is already a student account");
        }
    }
}
