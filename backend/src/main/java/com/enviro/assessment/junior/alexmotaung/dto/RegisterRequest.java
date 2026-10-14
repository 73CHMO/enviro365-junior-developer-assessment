package com.enviro.assessment.junior.alexmotaung.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Pattern(regexp = "[0-9]{13}", message = "Enter a 13-digit South African ID number") String idNumber,
        @NotNull LocalDate dateOfBirth,
        @Size(max = 32) String cardNumber) {
    @Override public String toString() { return "RegisterRequest[redacted]"; }
}
