package com.fieldward.account;

import java.time.OffsetDateTime;

record Account(long id, String email, String displayName, String passwordHash, Role role,
               boolean enabled, long sessionVersion, OffsetDateTime createdAt) {
    enum Role {
        STUDENT, ADMIN
    }
}
