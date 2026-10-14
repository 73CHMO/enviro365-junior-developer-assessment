package com.enviro.assessment.junior.alexmotaung;

import java.time.*;
import com.enviro.assessment.junior.alexmotaung.security.*;
import com.enviro.assessment.junior.alexmotaung.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityValidationTest {
    IdentityValidation validation() {
        var secrets = mock(SecuritySecrets.class);
        when(secrets.identityKey()).thenReturn(new byte[64]);
        return new IdentityValidation(Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneId.of("Africa/Johannesburg")), secrets);
    }

    @Test void validSouthAfricanIdentityRequiresMatchingBirthDate() {
        validation().validateIdentity("8001015009087", LocalDate.of(1980, 1, 1));
        assertThatThrownBy(() -> validation().validateIdentity("8001015009087", LocalDate.of(1980, 1, 2)))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"8001015009088", "0000000000000", "123", "8002315009087"})
    void invalidIdentitiesRejected(String id) {
        assertThatThrownBy(() -> validation().validateIdentity(id, LocalDate.of(1980, 1, 1)))
                .isInstanceOf(BusinessException.class);
    }

    @Test void cardCheckAcceptsValidTestNumberAndRejectsBadChecksum() {
        IdentityValidation.validateCard("4242 4242 4242 4242");
        IdentityValidation.validateCard(null);
        assertThatThrownBy(() -> IdentityValidation.validateCard("4242424242424241"))
                .isInstanceOf(BusinessException.class);
        assertThat(IdentityValidation.luhn("0000000000000000")).isFalse();
    }

    @Test void fingerprintIsKeyedAndDoesNotContainIdentity() {
        var validator = validation();
        var fingerprint = validator.fingerprint("8001015009087");
        assertThat(fingerprint).hasSize(64).doesNotContain("8001015009087");
        assertThat(validator.fingerprint("8001015009087")).isEqualTo(fingerprint);
    }
}
