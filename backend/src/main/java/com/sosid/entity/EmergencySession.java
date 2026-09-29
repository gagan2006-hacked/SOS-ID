package com.sosid.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="emergency_sessions") @Getter @Setter @NoArgsConstructor public class EmergencySession { @Id private UUID id; private String handleVerifier; private UUID qrCredentialId; private UUID profileId; private Instant issuedAt; private Instant expiresAt; private Instant revokedAt; private String requestFingerprint; }
