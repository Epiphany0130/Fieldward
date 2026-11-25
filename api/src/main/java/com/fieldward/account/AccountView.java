package com.fieldward.account;

import java.time.OffsetDateTime;

public record AccountView(String id, String email, String displayName, String role,
                          boolean enabled, OffsetDateTime createdAt) {
    static AccountView from(Account account) {
        return new AccountView(Long.toString(account.id()), account.email(), account.displayName(),
                account.role().name(), account.enabled(), account.createdAt());
    }
}
