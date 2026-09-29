package com.sosid.entity;
import com.sosid.entity.enums.DomainEnums.CredentialStatus; import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="qr_credentials") @Getter @Setter @NoArgsConstructor public class QrCredential { @Id private UUID id; private UUID profileId; private String tokenVerifier; @Enumerated(EnumType.STRING) private CredentialStatus status; private Instant issuedAt; private Instant revokedAt; private Instant expiresAt; private Instant lastUsedAt; }
