package com.enviro.assessment.junior.alexmotaung.controller;

import java.time.Duration;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import com.enviro.assessment.junior.alexmotaung.dto.*;
import com.enviro.assessment.junior.alexmotaung.exception.AuthenticationFailure;
import com.enviro.assessment.junior.alexmotaung.exception.ErrorResponse;
import com.enviro.assessment.junior.alexmotaung.service.AuthService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final boolean secure;
    private final Clock clock;
    public AuthController(AuthService auth, @Value("${app.security.secure-cookies}") boolean secure, Clock clock) {
        this.auth = auth;
        this.secure = secure;
        this.clock = clock;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return session(auth.register(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return session(auth.login(request), HttpStatus.OK);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        try {
            return session(auth.refresh(refreshCookie(request)), HttpStatus.OK);
        } catch (AuthenticationFailure exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                    .body(new ErrorResponse(LocalDateTime.now(clock), 401, exception.getMessage(), Map.of()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        auth.logout(refreshCookie(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }

    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(jwt.getSubject());
    }

    private ResponseEntity<AuthResponse> session(AuthService.Session session, HttpStatus status) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(session.refreshToken(), Duration.ofDays(7)).toString())
                .body(session.response());
    }

    private ResponseCookie cookie(String value, Duration age) {
        return ResponseCookie.from(secure ? "__Host-enviro-refresh" : "enviro-refresh", value)
                .httpOnly(true).secure(secure).sameSite("Strict").path(secure ? "/" : "/api/auth").maxAge(age).build();
    }

    private String refreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        String name = secure ? "__Host-enviro-refresh" : "enviro-refresh";
        for (var cookie : request.getCookies()) if (cookie.getName().equals(name)) return cookie.getValue();
        return null;
    }
}
