package com.fieldward.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
class AuthController {
    private final AccountMapper accounts;
    private final PasswordEncoder passwords;
    private final AuthenticationManager authenticator;
    private final SessionAuthenticationStrategy sessions;
    private final HttpSessionSecurityContextRepository contexts;

    AuthController(AccountMapper accounts, PasswordEncoder passwords, AuthenticationManager authenticator,
                   SessionAuthenticationStrategy sessions, HttpSessionSecurityContextRepository contexts) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.authenticator = authenticator;
        this.sessions = sessions;
        this.contexts = contexts;
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    void register(@RequestBody @Valid Registration input) {
        verifyPasswordLength(input.password());
        String email = normalizeEmail(input.email());
        if (accounts.insert(email, input.displayName().strip(), passwords.encode(input.password()), Account.Role.STUDENT) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该邮箱已注册");
        }
    }

    @PostMapping("/login")
    AccountView login(@RequestBody @Valid Login input, HttpServletRequest request, HttpServletResponse response) {
        verifyPasswordLength(input.password());
        try {
            var authentication = authenticator.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    normalizeEmail(input.email()), input.password()));
            var principal = (AccountPrincipal) authentication.getPrincipal();
            Account account = accounts.findById(principal.accountId());
            if (!principal.isCurrent(account)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "邮箱或密码错误，或账号已停用");
            }
            sessions.onAuthentication(authentication, request, response);
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, request, response);
            return AccountView.from(account);
        } catch (AuthenticationException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "邮箱或密码错误，或账号已停用");
        }
    }

    @GetMapping("/me")
    AccountView current(@RequestAttribute("fieldward.account") Account account) {
        return AccountView.from(account);
    }

    private static void verifyPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码最多 72 字节");
        }
    }

    static String normalizeEmail(String email) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "邮箱最多 254 个字符");
        }
        return normalized;
    }

    record Registration(@NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(max = 60) String displayName,
                        @NotBlank @Size(min = 8, max = 72) String password) {}

    record Login(@NotBlank @Email @Size(max = 254) String email,
                 @NotBlank @Size(max = 72) String password) {}

    record CsrfResponse(String headerName, String token) {}
}
