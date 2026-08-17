package com.example.logmonitor.auth.api;

import com.example.logmonitor.auth.application.AuthenticationService;
import com.example.logmonitor.auth.application.JwtService;
import com.example.logmonitor.auth.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    static final String REFRESH_COOKIE = "lm_refresh";

    private final AuthenticationService authenticationService;
    private final AuthProperties properties;

    public AuthController(
        AuthenticationService authenticationService,
        AuthProperties properties
    ) {
        this.authenticationService = authenticationService;
        this.properties = properties;
    }

    public record LoginRequest(
        @Email @Size(max = 254) String email,
        @Size(max = 100) String username,
        @NotBlank @Size(max = 128) String password
    ) {
    }

    public record AuthResponse(
        String accessToken,
        long expiresInSeconds,
        Instant refreshExpiresAt,
        AuthenticationService.UserView user
    ) {
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest servletRequest
    ) {
        String identity = request.email() == null || request.email().isBlank()
            ? request.username()
            : request.email();
        AuthenticationService.AuthResult result = authenticationService.login(
            identity, request.password(), servletRequest.getRemoteAddr());
        return withRefreshCookie(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
        @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken
    ) {
        return withRefreshCookie(authenticationService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        authenticationService.logout(bearerToken(request));
        response.addHeader(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthenticationService.UserView me(Authentication authentication) {
        JwtService.UserPrincipal principal = authentication != null
            && authentication.getPrincipal() instanceof JwtService.UserPrincipal authenticatedPrincipal
            ? authenticatedPrincipal
            : null;
        return authenticationService.currentUser(principal);
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthenticationService.AuthResult result) {
        Duration remaining = Duration.between(Instant.now(), result.refreshExpiresAt());
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, result.refreshToken())
            .httpOnly(true)
            .secure(properties.isRefreshCookieSecure())
            .sameSite(properties.getRefreshCookieSameSite())
            .path("/api/v1/auth")
            .maxAge(remaining.isNegative() ? Duration.ZERO : remaining)
            .build();
        AuthResponse response = new AuthResponse(
            result.accessToken(),
            result.accessExpiresInSeconds(),
            result.refreshExpiresAt(),
            result.user()
        );
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(response);
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
            .httpOnly(true)
            .secure(properties.isRefreshCookieSecure())
            .sameSite(properties.getRefreshCookieSameSite())
            .path("/api/v1/auth")
            .maxAge(Duration.ZERO)
            .build();
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
