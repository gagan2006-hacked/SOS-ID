package com.sosid.entity;
import com.sosid.entity.enums.DomainEnums.AccessRequestStatus; import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="document_access_requests") @Getter @Setter @NoArgsConstructor public class DocumentAccessRequest { @Id private UUID id; private UUID documentId; private UUID emergencySessionId; private UUID emergencyContactId; @Enumerated(EnumType.STRING) private AccessRequestStatus status; private Instant createdAt; private Instant expiresAt; private Instant completedAt; }
