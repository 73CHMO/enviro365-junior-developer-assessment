package com.enviro.assessment.junior.alexmotaung.model;

import java.time.LocalDate;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_accounts")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(nullable = false)
    private String passwordHash;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "investor_id", nullable = false, unique = true)
    private Investor investor;
    @Column(unique = true, length = 64)
    private String identityFingerprint;
    @Column(length = 4)
    private String identityLastFour;
    private LocalDate dateOfBirth;
    @Column(length = 4)
    private String cardLastFour;
    @Column(nullable = false)
    private long tokenVersion;

    public UserAccount(String email, String passwordHash, Investor investor,
                       String identityFingerprint, String identityLastFour, LocalDate dateOfBirth) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.investor = investor;
        this.identityFingerprint = identityFingerprint;
        this.identityLastFour = identityLastFour;
        this.dateOfBirth = dateOfBirth;
    }

    public void revokeAccessTokens() {
        tokenVersion++;
    }

    public void setCardLastFour(String lastFour) {
        if (!lastFour.matches("[0-9]{4}")) throw new IllegalArgumentException("Invalid masked card reference");
        cardLastFour = lastFour;
    }
}
