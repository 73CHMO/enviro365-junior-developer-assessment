package com.enviro.assessment.junior.alexmotaung;

import java.time.*;
import com.enviro.assessment.junior.alexmotaung.security.LoginThrottleFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.assertj.core.api.Assertions.*;

class LoginThrottleTest {
    @Test void rateLimitUsesActualPeerAndIgnoresForwardedAddressSpoofing() throws Exception {
        var clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);
        var filter = new LoginThrottleFilter(clock, 2);
        for (int attempt = 0; attempt < 3; attempt++) {
            var request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", "198.51.100." + attempt);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> {});
            assertThat(response.getStatus()).isEqualTo(attempt < 2 ? 200 : 429);
        }
    }
}
