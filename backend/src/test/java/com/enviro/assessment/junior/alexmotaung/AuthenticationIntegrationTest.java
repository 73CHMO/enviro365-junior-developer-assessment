package com.enviro.assessment.junior.alexmotaung;

import java.time.*;
import java.math.BigDecimal;
import java.util.List;
import com.jayway.jsonpath.JsonPath;
import com.enviro.assessment.junior.alexmotaung.model.*;
import com.enviro.assessment.junior.alexmotaung.repository.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "app.seed-data=false",
    "app.security.login-limit=1000",
    "spring.datasource.url=jdbc:h2:mem:security-tests;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "logging.level.root=WARN"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthenticationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository accounts;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired InvestorRepository investors;
    @Autowired ProductRepository products;
    @Autowired WithdrawalRepository withdrawals;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    String passwordHash;
    Investor owner;
    Investor other;
    UserAccount account;
    Cookie csrfCookie;
    String csrfToken;

    @BeforeAll void hashPassword() { passwordHash = passwords.encode("Correct!Password2026"); }

    @BeforeEach void setup() throws Exception {
        refreshTokens.deleteAllInBatch();
        accounts.deleteAllInBatch();
        withdrawals.deleteAllInBatch();
        products.deleteAllInBatch();
        investors.deleteAllInBatch();
        owner = investors.save(new Investor("Alex", "Motaung", "owner@example.com", 70));
        other = investors.save(new Investor("Other", "Investor", "other@example.com", 70));
        account = accounts.save(new UserAccount("owner@example.com", passwordHash, owner, null, null, null));
        products.save(new Product("Fund", ProductType.SAVINGS, new BigDecimal("100.00"), owner));
        var security = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        csrfToken = JsonPath.read(security.getResponse().getContentAsString(), "$.token");
        csrfCookie = security.getResponse().getCookie("enviro-xsrf");
        assertThat(csrfCookie).isNotNull();
    }

    MockHttpServletRequestBuilder protectedPost(String path) {
        return post(path).cookie(csrfCookie).header("X-XSRF-TOKEN", csrfToken).contentType(MediaType.APPLICATION_JSON);
    }

    MvcResult signIn() throws Exception {
        return mvc.perform(protectedPost("/api/auth/login")
                .content("{\"email\":\"owner@example.com\",\"password\":\"Correct!Password2026\"}"))
                .andExpect(status().isOk()).andReturn();
    }

    String access(MvcResult result) throws Exception { return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"); }
    Cookie refresh(MvcResult result) { return result.getResponse().getCookie("enviro-refresh"); }

    String registration(String email, String card, String identity) {
        return "{\"firstName\":\"New\",\"lastName\":\"Investor\",\"email\":\"" + email
                + "\",\"password\":\"New!Password2026\",\"idNumber\":\"" + identity
                + "\",\"dateOfBirth\":\"1980-01-01\",\"cardNumber\":\"" + card + "\"}";
    }

    @Test void anonymousCannotReadPortfolioOrHistory() throws Exception {
        mvc.perform(get("/api/investors/" + owner.getId() + "/portfolio")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/investors/" + owner.getId() + "/withdrawals/export")).andExpect(status().isUnauthorized());
    }

    @Test void loginHasNoHttpSessionAndRefreshCookieIsProtected() throws Exception {
        var login = signIn();
        assertThat(login.getRequest().getSession(false)).isNull();
        var cookie = refresh(login);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(login.getResponse().getHeaders("Set-Cookie").toString()).contains("SameSite=Strict");
        assertThat(login.getResponse().getHeaders("Set-Cookie").toString()).doesNotContain("JSESSIONID");
        assertThat(login.getResponse().getContentAsString()).doesNotContain("Correct!Password2026", "passwordHash", "refreshToken");
    }

    @Test void ownerCanReadOwnPortfolioButNotAnotherInvestorsRoutes() throws Exception {
        var jwt = access(signIn());
        mvc.perform(get("/api/investors/" + owner.getId() + "/portfolio").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk());
        for (String suffix : List.of("/portfolio", "/withdrawals", "/withdrawals/export")) {
            mvc.perform(get("/api/investors/" + other.getId() + suffix).header("Authorization", "Bearer " + jwt))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void csrfRequiredForCookieBackedLoginAndWithdrawalCannotUseCookieOnlyAuthentication() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@example.com\",\"password\":\"Correct!Password2026\"}"))
                .andExpect(status().isForbidden());
        var login = signIn();
        mvc.perform(post("/api/investors/" + owner.getId() + "/withdrawals")
                .cookie(refresh(login)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"amount\":1.00}")).andExpect(status().isForbidden());
    }

    @Test void logoutRevokesJwtAndRefreshAndClearsCookie() throws Exception {
        var login = signIn();
        var jwt = access(login);
        mvc.perform(protectedPost("/api/auth/logout").cookie(refresh(login)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt)).andExpect(status().isUnauthorized());
        mvc.perform(protectedPost("/api/auth/refresh").cookie(refresh(login))).andExpect(status().isUnauthorized());
    }

    @Test void refreshRotatesAndReplayRevocationCommitsDespite401() throws Exception {
        var login = signIn();
        var originalRefresh = refresh(login);
        var rotated = mvc.perform(protectedPost("/api/auth/refresh").cookie(originalRefresh))
                .andExpect(status().isOk()).andReturn();
        assertThat(refresh(rotated).getValue()).isNotEqualTo(originalRefresh.getValue());
        mvc.perform(protectedPost("/api/auth/refresh").cookie(originalRefresh)).andExpect(status().isUnauthorized());
        assertThat(accounts.findById(account.getId()).orElseThrow().getTokenVersion()).isEqualTo(1);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access(rotated)))
                .andExpect(status().isUnauthorized());
        mvc.perform(protectedPost("/api/auth/refresh").cookie(refresh(rotated))).andExpect(status().isUnauthorized());
        var newLogin = signIn();
        mvc.perform(protectedPost("/api/auth/refresh").cookie(originalRefresh)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access(newLogin)))
                .andExpect(status().isOk());
    }

    @Test void registrationHashesPasswordMasksReferencesAndOwnsAnEmptyPortfolio() throws Exception {
        var registered = mvc.perform(protectedPost("/api/auth/register")
                .content(registration("new@example.com", "4242424242424242", "8001015009087")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.maskedEmail").value("n***@example.com"))
                .andExpect(jsonPath("$.user.maskedIdNumber").value("*********9087"))
                .andExpect(jsonPath("$.user.maskedCardNumber").value("•••• •••• •••• 4242")).andReturn();
        assertThat(registered.getResponse().getContentAsString()).doesNotContain(
                "8001015009087", "4242424242424242", "New!Password2026");
        var stored = accounts.findByEmail("new@example.com").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo("New!Password2026");
        assertThat(passwords.matches("New!Password2026", stored.getPasswordHash())).isTrue();
        assertThat(stored.getIdentityFingerprint()).hasSize(64).doesNotContain("8001015009087");
        assertThat(stored.getCardLastFour()).isEqualTo("4242");
        Long investorId = ((Number) JsonPath.read(registered.getResponse().getContentAsString(), "$.user.investorId")).longValue();
        mvc.perform(get("/api/investors/" + investorId + "/portfolio").header("Authorization", "Bearer " + access(registered)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.products").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"4242424242424241", "0000000000000000", "hello"})
    void invalidCardsAreRejectedWithoutCreatingAccount(String card) throws Exception {
        mvc.perform(protectedPost("/api/auth/register").content(registration("new@example.com", card, "8001015009087")))
                .andExpect(status().isBadRequest());
        assertThat(accounts.existsByEmail("new@example.com")).isFalse();
    }

    @Test void invalidIdAndBirthDateAreRejected() throws Exception {
        mvc.perform(protectedPost("/api/auth/register")
                .content(registration("new@example.com", "", "8001015009088"))).andExpect(status().isBadRequest());
        mvc.perform(protectedPost("/api/auth/register")
                .content(registration("new@example.com", "", "8001015009087").replace("1980-01-01", "1980-01-02")))
                .andExpect(status().isBadRequest());
    }

    @Test void duplicateIdentityCannotRegisterAnotherAccount() throws Exception {
        mvc.perform(protectedPost("/api/auth/register").content(registration("one@example.com", "", "8001015009087")))
                .andExpect(status().isCreated());
        mvc.perform(protectedPost("/api/auth/register").content(registration("two@example.com", "", "8001015009087")))
                .andExpect(status().isBadRequest());
        assertThat(accounts.existsByEmail("two@example.com")).isFalse();
    }

    @Test void incorrectLoginUsesGenericError() throws Exception {
        mvc.perform(protectedPost("/api/auth/login")
                .content("{\"email\":\"owner@example.com\",\"password\":\"Incorrect!Password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Email or password is incorrect"));
        mvc.perform(protectedPost("/api/auth/login")
                .content("{\"email\":\"missing@example.com\",\"password\":\"Incorrect!Password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Email or password is incorrect"));
    }

    @Test void tamperedJwtIsRejected() throws Exception {
        String jwt = access(signIn());
        String tampered = "x" + jwt.substring(1);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tampered)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "wrong-issuer", "wrong-audience"})
    void signedButInvalidClaimsAreRejected(String scenario) throws Exception {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().subject(account.getId().toString()).claim("ver", 0L)
                .issuer(scenario.equals("wrong-issuer") ? "other" : "enviro365")
                .audience(List.of(scenario.equals("wrong-audience") ? "other" : "enviro365-frontend"))
                .issuedAt(now.minusSeconds(600))
                .expiresAt(scenario.equals("expired") ? now.minusSeconds(10) : now.plusSeconds(300)).build();
        String jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwt)).andExpect(status().isUnauthorized());
    }
}
