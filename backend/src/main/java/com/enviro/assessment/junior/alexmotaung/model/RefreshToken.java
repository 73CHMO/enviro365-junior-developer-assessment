package com.enviro.assessment.junior.alexmotaung.model;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_account", columnList = "account_id"))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private UserAccount account;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(nullable = false)
    private boolean consumed;
    @Column(nullable = false)
    private boolean revoked;

    public RefreshToken(String tokenHash, UserAccount account, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.account = account;
        this.expiresAt = expiresAt;
    }

    public void consume() {
        consumed = true;
    }
}
