package com.enviro.assessment.junior.alexmotaung.security;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import jakarta.servlet.http.HttpServletResponse;

public final class SecurityErrors {
    private SecurityErrors() {}
    public static void write(HttpServletResponse response, int status, String message, Clock clock) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"timestamp\":\"" + LocalDateTime.now(clock) + "\",\"status\":"
                + status + ",\"error\":\"" + message + "\",\"details\":{}}");
    }
}
