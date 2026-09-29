package com.sosid.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "owner_otp_challenges")
@Getter
@Setter
@NoArgsConstructor
public class OwnerOtpChallenge {
    @Id private UUID id;
    private String phoneNumber;
    private String otpVerifier;
    private Instant expiresAt;
    private int attemptCount;
    private int maxAttempts;
    private Instant consumedAt;
    private Instant createdAt;
}
