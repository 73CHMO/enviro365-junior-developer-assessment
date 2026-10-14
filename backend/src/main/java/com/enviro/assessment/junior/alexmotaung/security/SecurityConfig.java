package com.enviro.assessment.junior.alexmotaung.security;

import java.time.Clock;
import java.time.Duration;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.enviro.assessment.junior.alexmotaung.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    JwtEncoder jwtEncoder(SecuritySecrets secrets) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(
                new SecretKeySpec(secrets.signingKey(), "HmacSHA256")));
    }

    @Bean
    JwtDecoder jwtDecoder(SecuritySecrets secrets, UserAccountRepository accounts, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secrets.signingKey(), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        var time = new JwtTimestampValidator(Duration.ZERO);
        time.setClock(clock);
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains("enviro365-frontend")
                ? OAuth2TokenValidatorResult.success() : invalidToken();
        OAuth2TokenValidator<Jwt> revoked = jwt -> {
            try {
                Number version = jwt.getClaim("ver");
                if (version == null) return invalidToken();
                return accounts.findById(Long.parseLong(jwt.getSubject()))
                        .filter(account -> account.getTokenVersion() == version.longValue())
                        .map(account -> OAuth2TokenValidatorResult.success()).orElseGet(SecurityConfig::invalidToken);
            } catch (RuntimeException ignored) {
                return invalidToken();
            }
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(time,
                new JwtIssuerValidator("enviro365"), audience, revoked));
        return decoder;
    }

    private static OAuth2TokenValidatorResult invalidToken() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token is invalid or revoked", null));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder, Clock clock,
            @Value("${app.security.secure-cookies}") boolean secureCookies,
            @Value("${app.security.login-limit:20}") int loginLimit) throws Exception {
        var csrf = new CookieCsrfTokenRepository();
        csrf.setCookieName(secureCookies ? "__Host-enviro-xsrf" : "enviro-xsrf");
        csrf.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(secureCookies).sameSite("Strict").path("/"));
        var requestHandler = new CsrfTokenRequestAttributeHandler();
        return http
                .cors(cors -> {})
                .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(requestHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/csrf", "/api/auth/login", "/api/auth/register",
                                "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                SecurityErrors.write(response, 401, "Please sign in to continue", clock))
                        .accessDeniedHandler((request, response, exception) ->
                                SecurityErrors.write(response, 403, "Access denied or request security token missing", clock)))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.decoder(decoder))
                        .authenticationEntryPoint((request, response, exception) ->
                                SecurityErrors.write(response, 401, "Authentication token is invalid or expired", clock))
                        .accessDeniedHandler((request, response, exception) ->
                                SecurityErrors.write(response, 403, "You cannot access this portfolio", clock)))
                .addFilterBefore(new LoginThrottleFilter(clock, loginLimit), CsrfFilter.class)
                .build();
    }
}
