package com.enviro.assessment.junior.alexmotaung;

import java.time.Clock;
import com.enviro.assessment.junior.alexmotaung.controller.AuthController;
import com.enviro.assessment.junior.alexmotaung.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CookiePolicyTest {
    @Test void productionCookieCannotBeReplacedByAParentDomainCookie() {
        var controller = new AuthController(mock(AuthService.class), true, Clock.systemUTC());
        String cookie = controller.logout(new MockHttpServletRequest()).getHeaders().getFirst("Set-Cookie");
        assertThat(cookie).startsWith("__Host-enviro-refresh=").contains("Path=/", "Secure", "HttpOnly", "SameSite=Strict")
                .doesNotContain("Domain=");
    }
}
