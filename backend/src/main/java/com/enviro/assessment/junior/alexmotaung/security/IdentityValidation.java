package com.enviro.assessment.junior.alexmotaung.security;

import java.time.*;
import java.time.format.DateTimeFormatter;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;
import com.enviro.assessment.junior.alexmotaung.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class IdentityValidation {
    private final Clock clock;
    private final SecuritySecrets secrets;

    public IdentityValidation(Clock clock, SecuritySecrets secrets) {
        this.clock = clock;
        this.secrets = secrets;
    }

    public void validateIdentity(String id, LocalDate birthDate) {
        if (id == null || !id.matches("[0-9]{13}") || !luhn(id)
                || (id.charAt(10) != '0' && id.charAt(10) != '1')
                || (id.charAt(11) != '8' && id.charAt(11) != '9')) {
            throw new BusinessException("Enter a valid 13-digit South African ID number");
        }
        var today = LocalDate.now(clock);
        if (birthDate == null || birthDate.isAfter(today) || birthDate.isBefore(today.minusYears(120))
                || !id.startsWith(birthDate.format(DateTimeFormatter.ofPattern("yyMMdd")))) {
            throw new BusinessException("Date of birth must be valid and match the ID number");
        }
    }

    public static boolean luhn(String digits) {
        if (digits == null || !digits.matches("[0-9]+") || digits.chars().allMatch(c -> c == '0')) return false;
        int sum = 0;
        boolean doubleDigit = false;
        for (int index = digits.length() - 1; index >= 0; index--) {
            int digit = digits.charAt(index) - '0';
            if (doubleDigit && (digit *= 2) > 9) digit -= 9;
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    public static void validateCard(String card) {
        if (card == null || card.isBlank()) return;
        if (!card.matches("[0-9 -]{12,32}")) throw new BusinessException("Enter a valid card number");
        String digits = card.replace(" ", "").replace("-", "");
        if (!digits.matches("[0-9]{12,19}") || !luhn(digits)) {
            throw new BusinessException("Card number failed the Luhn check");
        }
    }

    public String fingerprint(String id) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secrets.identityKey(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Identity hashing failed", exception);
        }
    }
}
