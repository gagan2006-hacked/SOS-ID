package com.sosid.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="temporary_document_authorizations") @Getter @Setter @NoArgsConstructor public class TemporaryDocumentAuthorization { @Id private UUID id; private UUID documentId; private UUID emergencySessionId; private UUID accessRequestId; private UUID authorizedContactId; private Instant issuedAt; private Instant expiresAt; private Instant revokedAt; }
