package com.sosid.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="emergency_contact_otp_challenges") @Getter @Setter @NoArgsConstructor public class EmergencyContactOtpChallenge { @Id private UUID id; private UUID accessRequestId; private UUID emergencyContactId; private String otpVerifier; private Instant expiresAt; private int attemptCount; private int maxAttempts; private Instant consumedAt; private Instant createdAt; }
