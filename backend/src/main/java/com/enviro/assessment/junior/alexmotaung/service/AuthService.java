package com.enviro.assessment.junior.alexmotaung.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import com.enviro.assessment.junior.alexmotaung.dto.*;
import com.enviro.assessment.junior.alexmotaung.exception.*;
import com.enviro.assessment.junior.alexmotaung.model.*;
import com.enviro.assessment.junior.alexmotaung.repository.*;
import com.enviro.assessment.junior.alexmotaung.security.IdentityValidation;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    public record Session(AuthResponse response, String refreshToken) {
        @Override public String toString() { return "Session[redacted]"; }
    }
    private final UserAccountRepository accounts;
    private final InvestorRepository investors;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final IdentityValidation identity;
    private final Clock clock;
    private final String dummyHash;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserAccountRepository accounts, InvestorRepository investors,
                       RefreshTokenRepository refreshTokens, PasswordEncoder passwords,
                       JwtEncoder encoder, IdentityValidation identity, Clock clock) {
        this.accounts = accounts;
        this.investors = investors;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.encoder = encoder;
        this.identity = identity;
        this.clock = clock;
        dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public Session register(RegisterRequest request) {
        checkPassword(request.password());
        identity.validateIdentity(request.idNumber(), request.dateOfBirth());
        IdentityValidation.validateCard(request.cardNumber());
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        String fingerprint = identity.fingerprint(request.idNumber());
        if (accounts.existsByEmail(email) || accounts.existsByIdentityFingerprint(fingerprint)) {
            throw new BusinessException("Unable to register with those details");
        }
        var investor = investors.save(new Investor(request.firstName().strip(), request.lastName().strip(),
                email, Period.between(request.dateOfBirth(), LocalDate.now(clock)).getYears()));
        investor.setDateOfBirth(request.dateOfBirth());
        var account = new UserAccount(email, passwords.encode(request.password()), investor,
                fingerprint, request.idNumber().substring(9), request.dateOfBirth());
        if (request.cardNumber() != null && !request.cardNumber().isBlank()) {
            String card = request.cardNumber().replace(" ", "").replace("-", "");
            account.setCardLastFour(card.substring(card.length() - 4));
        }
        accounts.save(account);
        return issue(account);
    }

    @Transactional
    public Session login(LoginRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        var account = accounts.findByEmail(email).orElse(null);
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72 || request.password().indexOf(0) >= 0) {
            throw new AuthenticationFailure("Email or password is incorrect");
        }
        boolean matched = passwords.matches(request.password(), account == null ? dummyHash : account.getPasswordHash());
        if (!matched || account == null) throw new AuthenticationFailure("Email or password is incorrect");
        account = accounts.findForUpdate(account.getId()).orElseThrow();
        return issue(account);
    }

    @Transactional(noRollbackFor = AuthenticationFailure.class)
    public Session refresh(String rawToken) {
        if (rawToken == null || rawToken.length() > 100) throw new AuthenticationFailure("Please sign in again");
        String hash = hash(rawToken);
        Long accountId = refreshTokens.findAccountIdByTokenHash(hash)
                .orElseThrow(() -> new AuthenticationFailure("Please sign in again"));
        var account = accounts.findForUpdate(accountId).orElseThrow();
        var token = refreshTokens.findForUpdate(hash).orElseThrow();
        if (token.isRevoked() || !token.getExpiresAt().isAfter(clock.instant())) {
            throw new AuthenticationFailure("Please sign in again");
        }
        if (token.isConsumed()) {
            // A replay revokes all account credentials. This exception deliberately does not roll back revocation.
            revoke(account);
            throw new AuthenticationFailure("Your sign-in was revoked; please sign in again");
        }
        token.consume();
        return issue(account);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.length() > 100) return;
        refreshTokens.findAccountIdByTokenHash(hash(rawToken)).ifPresent(accountId ->
                accounts.findForUpdate(accountId).ifPresent(this::revoke));
    }

    @Transactional(readOnly = true)
    public AccountResponse me(String subject) {
        var account = accounts.findById(Long.parseLong(subject))
                .orElseThrow(() -> new AuthenticationFailure("Please sign in again"));
        return response(account);
    }

    private void revoke(UserAccount account) {
        account.revokeAccessTokens();
        refreshTokens.revokeForAccount(account.getId());
    }

    private Session issue(UserAccount account) {
        var now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer("enviro365").subject(account.getId().toString())
                .audience(List.of("enviro365-frontend")).issuedAt(now).notBefore(now)
                .expiresAt(now.plusSeconds(300)).id(UUID.randomUUID().toString())
                .claim("ver", account.getTokenVersion()).build();
        String jwt = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(hash(refresh), account, now.plus(Duration.ofDays(7))));
        return new Session(new AuthResponse(jwt, 300, response(account)), refresh);
    }

    private static AccountResponse response(UserAccount account) {
        var investor = account.getInvestor();
        String email = account.getEmail();
        int at = email.indexOf('@');
        String maskedEmail = email.substring(0, 1) + "***" + email.substring(at);
        return new AccountResponse(investor.getId(), investor.getFirstName(), investor.getLastName(), maskedEmail,
                account.getIdentityLastFour() == null ? null : "*********" + account.getIdentityLastFour(),
                account.getCardLastFour() == null ? null : "•••• •••• •••• " + account.getCardLastFour());
    }

    private static void checkPassword(String password) {
        if (password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72 || password.indexOf(0) >= 0) {
            throw new BusinessException("Use a password of at least 12 characters and at most 72 UTF-8 bytes");
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Token hashing failed", exception);
        }
    }
}
