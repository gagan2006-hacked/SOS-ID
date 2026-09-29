package com.sosid.core;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
class AuditService {
    private final AuditRepository audits;

    AuditService(AuditRepository audits) {
        this.audits = audits;
    }

    void record(String event, String outcome, UUID actorUserId, UUID profileId, UUID qrId, UUID sessionId, UUID documentId, UUID requestId, UUID authorizationId, String correlationId) {
        AuditEvent a = new AuditEvent();
        a.setId(UUID.randomUUID());
        a.setEventType(event);
        a.setOutcome(outcome);
        a.setActorUserId(actorUserId);
        a.setProfileId(profileId);
        a.setQrCredentialId(qrId);
        a.setEmergencySessionId(sessionId);
        a.setMedicalDocumentId(documentId);
        a.setDocumentAccessRequestId(requestId);
        a.setTemporaryDocumentAuthorizationId(authorizationId);
        a.setCorrelationId(correlationId);
        a.setMetadata("{}");
        a.setOccurredAt(Instant.now());
        audits.save(a);
    }
}
