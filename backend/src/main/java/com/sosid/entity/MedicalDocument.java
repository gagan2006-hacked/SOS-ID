package com.sosid.entity;

import com.sosid.entity.enums.DomainEnums.DocumentAccessPolicy;
import com.sosid.entity.enums.DomainEnums.DocumentLifecycleStatus;
import com.sosid.entity.enums.DomainEnums.ProcessingStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "medical_documents")
@Getter
@Setter
@NoArgsConstructor
public class MedicalDocument {
    @Id private UUID id;
    private UUID profileId;
    private String storageReference;
    private String documentName;
    private String documentType;
    @Enumerated(EnumType.STRING) private DocumentAccessPolicy accessPolicy;
    @Enumerated(EnumType.STRING) private ProcessingStatus processingStatus;
    @Enumerated(EnumType.STRING) private DocumentLifecycleStatus lifecycleStatus;
    private Instant createdAt;
    private Instant updatedAt;
}
