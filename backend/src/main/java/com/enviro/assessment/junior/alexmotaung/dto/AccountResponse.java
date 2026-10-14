package com.enviro.assessment.junior.alexmotaung.dto;

public record AccountResponse(Long investorId, String firstName, String lastName,
                              String maskedEmail, String maskedIdNumber, String maskedCardNumber) {
}
