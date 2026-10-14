package com.enviro.assessment.junior.alexmotaung.security;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class SecuritySecrets {
    private final byte[] signingKey;
    private final byte[] identityKey;

    public SecuritySecrets(@Value("${app.security.jwt-secret}") String jwtSecret,
                           @Value("${app.security.identity-key}") String identitySecret,
                           Environment environment) throws IOException {
        boolean production = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (production && Boolean.parseBoolean(environment.getProperty("app.seed-data", "true"))) {
            throw new IllegalStateException("Demo seeding must be disabled in production");
        }
        if (production && (jwtSecret.isBlank() || identitySecret.isBlank())) {
            throw new IllegalStateException("Production requires separate JWT_SECRET and IDENTITY_HASH_KEY secrets");
        }
        Properties local = new Properties();
        Path path = Path.of("data", ".security-keys.properties");
        if (!production && (jwtSecret.isBlank() || identitySecret.isBlank())) {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                try (var input = Files.newInputStream(path)) {
                    local.load(input);
                }
            } else {
                local.setProperty("jwt", randomKey());
                local.setProperty("identity", randomKey());
                try (var output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
                    local.store(output, "Local development secrets. Never commit or publish this file.");
                }
                try {
                    Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
                } catch (UnsupportedOperationException ignored) {
                    // Windows uses the user's directory ACL; production keys are supplied externally.
                }
            }
        }
        signingKey = decode(jwtSecret.isBlank() ? local.getProperty("jwt") : jwtSecret);
        identityKey = decode(identitySecret.isBlank() ? local.getProperty("identity") : identitySecret);
        if (Arrays.equals(signingKey, identityKey)) {
            throw new IllegalStateException("Signing and identity-hash keys must be different");
        }
    }

    public byte[] signingKey() { return signingKey.clone(); }
    public byte[] identityKey() { return identityKey.clone(); }

    private static String randomKey() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] decode(String text) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(text); }
        catch (IllegalArgumentException exception) { throw new IllegalStateException("Security keys must be valid Base64"); }
        if (bytes.length < 32) throw new IllegalStateException("Security keys require at least 32 random bytes");
        return bytes;
    }
}
