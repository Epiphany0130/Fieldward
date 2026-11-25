package com.fieldward.account;

import java.io.Serial;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

final class AccountPrincipal extends User {
    @Serial
    private static final long serialVersionUID = 1L;

    private final long accountId;
    private final long sessionVersion;

    AccountPrincipal(Account account) {
        super(account.email(), account.passwordHash(), account.enabled(), true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name())));
        this.accountId = account.id();
        this.sessionVersion = account.sessionVersion();
    }

    long accountId() {
        return accountId;
    }

    boolean isCurrent(Account account) {
        return account != null && account.enabled() && account.sessionVersion() == sessionVersion;
    }
}
