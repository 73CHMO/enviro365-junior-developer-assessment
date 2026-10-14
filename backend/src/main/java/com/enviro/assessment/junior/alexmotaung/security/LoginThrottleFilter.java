package com.enviro.assessment.junior.alexmotaung.security;

import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;

public class LoginThrottleFilter extends OncePerRequestFilter {
    private record Window(long minute, AtomicInteger count) {}
    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int limit;

    public LoginThrottleFilter(Clock clock, int limit) { this.clock = clock; this.limit = limit; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getMethod().equals("POST")
                || !(request.getRequestURI().equals("/api/auth/login")
                || request.getRequestURI().equals("/api/auth/register"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long minute = clock.instant().getEpochSecond() / 60;
        // Never trust a caller-supplied X-Forwarded-For header.
        String key = request.getRemoteAddr();
        if (attempts.size() > 10000) attempts.entrySet().removeIf(entry -> entry.getValue().minute() < minute);
        if (attempts.size() > 10000 && !attempts.containsKey(key)) {
            SecurityErrors.write(response, 429, "Too many sign-in attempts; try again shortly", clock);
            return;
        }
        var window = attempts.compute(key, (ignored, existing) ->
                existing == null || existing.minute() != minute ? new Window(minute, new AtomicInteger()) : existing);
        if (window.count().incrementAndGet() > limit) {
            response.setHeader("Retry-After", "60");
            SecurityErrors.write(response, 429, "Too many sign-in attempts; try again shortly", clock);
            return;
        }
        chain.doFilter(request, response);
    }
}
