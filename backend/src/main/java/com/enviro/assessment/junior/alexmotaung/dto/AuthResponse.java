package com.enviro.assessment.junior.alexmotaung.dto;

public record AuthResponse(String accessToken, long expiresIn, AccountResponse user) {
    @Override public String toString() { return "AuthResponse[redacted]"; }
}
