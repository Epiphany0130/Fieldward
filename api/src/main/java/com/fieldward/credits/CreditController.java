package com.fieldward.credits;

import com.fieldward.account.AccountPrincipal;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/credits")
class CreditController {
    private final CreditService credits;
    CreditController(CreditService credits) { this.credits = credits; }

    @GetMapping
    Map<String, Object> view(@AuthenticationPrincipal AccountPrincipal principal) {
        return credits.view(principal.accountId());
    }
}
