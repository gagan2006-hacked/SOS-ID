package com.sosid.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A one-way-verifiable, rotating owner refresh credential; raw token material is never persisted. */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {
    @Id
    private UUID id;
    private UUID userId;
    private String tokenVerifier;
    private Instant expiresAt;
    private Instant revokedAt;
    private Instant replacedAt;
    private Instant createdAt;
}
