package com.sosid.core;

import com.sosid.common.ApiSupport;
import com.sosid.common.Secrets;
import com.sosid.entity.*;
import com.sosid.entity.enums.DomainEnums;
import com.sosid.entity.enums.DomainEnums.*;
import com.sosid.notification.OtpNotifier;
import com.sosid.storage.S3DocumentStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
class DocumentService {
    private final ProfileRepository profiles;
    private final MedicalDocumentRepository documents;
    private final ContactRepository contacts;
    private final AccessRequestRepository requests;
    private final OtpChallengeRepository challenges;
    private final TemporaryAuthorizationRepository authorizations;
    private final EmergencyService emergency;
    private final Secrets secrets;
    private final OtpNotifier notifier;
    private final S3DocumentStore store;
    private final AuditService audit;
    private final long otpMinutes, authorizationMinutes;
    private final int maxAttempts;

    DocumentService(ProfileRepository profiles, MedicalDocumentRepository documents, ContactRepository contacts, AccessRequestRepository requests, OtpChallengeRepository challenges, TemporaryAuthorizationRepository authorizations, EmergencyService emergency, Secrets secrets, OtpNotifier notifier, S3DocumentStore store, AuditService audit, @Value("${sosid.security.otp-minutes}") long otpMinutes, @Value("${sosid.security.document-authorization-minutes}") long authorizationMinutes, @Value("${sosid.security.max-otp-attempts}") int maxAttempts) {
        this.profiles = profiles;
        this.documents = documents;
        this.contacts = contacts;
        this.requests = requests;
        this.challenges = challenges;
        this.authorizations = authorizations;
        this.emergency = emergency;
        this.secrets = secrets;
        this.notifier = notifier;
        this.store = store;
        this.audit = audit;
        this.otpMinutes = otpMinutes;
        this.authorizationMinutes = authorizationMinutes;
        this.maxAttempts = maxAttempts;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    UploadTarget uploadTarget(UUID userId, String name, String contentType) {
        EmergencyProfile p = ownerProfile(userId);
        String key = "profiles/" + p.getId() + "/documents/" + UUID.randomUUID() + "/" + safeName(name);
        return new UploadTarget(key, store.uploadUrl(key, contentType));
    }

    @Transactional
    public DocumentView register(UUID userId, DocumentInput input, String correlation) {
        EmergencyProfile p = ownerProfile(userId);
        if (!input.storageReference().startsWith("profiles/" + p.getId() + "/documents/"))
            throw new ApiSupport.InvalidRequestException("storage reference");
        MedicalDocument d = new MedicalDocument();
        Instant now = Instant.now();
        d.setId(UUID.randomUUID());
        d.setProfileId(p.getId());
        d.setStorageReference(input.storageReference());
        d.setDocumentName(input.documentName());
        d.setDocumentType(input.documentType());
        d.setAccessPolicy(input.accessPolicy());
        d.setProcessingStatus(DomainEnums.ProcessingStatus.PENDING);
        d.setLifecycleStatus(DomainEnums.DocumentLifecycleStatus.ACTIVE);
        d.setCreatedAt(now);
        d.setUpdatedAt(now);
        documents.save(d);
        audit.record("DOCUMENT_CREATED", "SUCCESS", userId, p.getId(), null, null, d.getId(), null, null, correlation);
        return ownerView(d);
    }

    public List<DocumentView> listOwner(UUID userId) {
        EmergencyProfile p = ownerProfile(userId);
        return documents.findByProfileIdAndLifecycleStatus(p.getId(), DocumentLifecycleStatus.ACTIVE).stream().map(this::ownerView).toList();
    }

    DocumentView getOwner(UUID userId, UUID documentId) {
        return ownerView(requireOwnerDocument(userId, documentId));
    }

    @Transactional
    public DocumentView update(UUID userId, UUID documentId, DocumentUpdate input, String correlation) {
        MedicalDocument d = requireOwnerDocument(userId, documentId);
        DocumentAccessPolicy old = d.getAccessPolicy();
        if (input.documentName() != null) d.setDocumentName(input.documentName());
        if (input.accessPolicy() != null) d.setAccessPolicy(input.accessPolicy());
        d.setUpdatedAt(Instant.now());
        documents.save(d);
        if (old != d.getAccessPolicy()) revokeDependent(d, correlation);
        audit.record("DOCUMENT_POLICY_CHANGED", "SUCCESS", userId, d.getProfileId(), null, null, d.getId(), null, null, correlation);
        return ownerView(d);
    }

    @Transactional
    public void archive(UUID userId, UUID documentId, String correlation) {
        MedicalDocument d = requireOwnerDocument(userId, documentId);
        d.setLifecycleStatus(DocumentLifecycleStatus.ARCHIVED);
        d.setUpdatedAt(Instant.now());
        documents.save(d);
        revokeDependent(d, correlation);
        audit.record("DOCUMENT_ARCHIVED", "SUCCESS", userId, d.getProfileId(), null, null, d.getId(), null, null, correlation);
    }

    @Transactional
    public void delete(UUID userId, UUID documentId, String correlation) {
        MedicalDocument d = requireOwnerDocument(userId, documentId);
        d.setLifecycleStatus(DocumentLifecycleStatus.DELETED);
        d.setUpdatedAt(Instant.now());
        documents.save(d);
        revokeDependent(d, correlation);
        audit.record("DOCUMENT_DELETED", "SUCCESS", userId, d.getProfileId(), null, null, d.getId(), null, null, correlation);
    }

    public List<EmergencyDocumentView> listEmergency(String handle, String correlation) {
        EmergencySession s = emergency.require(handle, correlation);
        List<EmergencyDocumentView> out = documents.findByProfileIdAndLifecycleStatus(s.getProfileId(), DocumentLifecycleStatus.ACTIVE).stream().filter(d -> d.getProcessingStatus() == ProcessingStatus.READY).map(d -> new EmergencyDocumentView(d.getId(), d.getDocumentName(), d.getDocumentType(), d.getAccessPolicy(), d.getAccessPolicy() == DocumentAccessPolicy.PRIVATE)).toList();
        audit.record("DOCUMENTS_LISTED", "SUCCESS", null, s.getProfileId(), s.getQrCredentialId(), s.getId(), null, null, null, correlation);
        return out;
    }

    @Transactional
    public AccessRequestView requestPrivate(String handle, UUID documentId, UUID contactId, String correlation) {
        EmergencySession s = emergency.require(handle, correlation);
        MedicalDocument d = requireEmergencyDocument(s, documentId);
        if (d.getAccessPolicy() != DocumentAccessPolicy.PRIVATE) throw new ApiSupport.ForbiddenException();
        EmergencyContact c = contacts.findByIdAndProfileIdAndActiveTrue(contactId, s.getProfileId()).orElseThrow(ApiSupport.ForbiddenException::new);
        DocumentAccessRequest r = new DocumentAccessRequest();
        r.setId(UUID.randomUUID());
        r.setDocumentId(d.getId());
        r.setEmergencySessionId(s.getId());
        r.setEmergencyContactId(c.getId());
        r.setStatus(AccessRequestStatus.PENDING);
        r.setCreatedAt(Instant.now());
        r.setExpiresAt(min(s.getExpiresAt(), Instant.now().plus(Duration.ofMinutes(otpMinutes))));
        requests.save(r);
        audit.record("PRIVATE_DOCUMENT_REQUESTED", "SUCCESS", null, s.getProfileId(), s.getQrCredentialId(), s.getId(), d.getId(), r.getId(), null, correlation);
        return new AccessRequestView(r.getId(), r.getExpiresAt());
    }

    @Transactional
    public void issueOtp(String handle, UUID requestId, String correlation) {
        EmergencySession s = emergency.require(handle, correlation);
        DocumentAccessRequest r = requireRequest(s, requestId);
        if (r.getStatus() != AccessRequestStatus.PENDING || !r.getExpiresAt().isAfter(Instant.now()))
            throw new ApiSupport.ForbiddenException();
        MedicalDocument d = requireEmergencyDocument(s, r.getDocumentId());
        EmergencyContact contact = contacts.findByIdAndProfileIdAndActiveTrue(r.getEmergencyContactId(), s.getProfileId()).orElseThrow(ApiSupport.ForbiddenException::new);
        String raw = secrets.otp();
        EmergencyContactOtpChallenge c = new EmergencyContactOtpChallenge();
        c.setId(UUID.randomUUID());
        c.setAccessRequestId(r.getId());
        c.setEmergencyContactId(contact.getId());
        c.setOtpVerifier(secrets.hash(raw));
        c.setCreatedAt(Instant.now());
        c.setExpiresAt(min(r.getExpiresAt(), Instant.now().plus(Duration.ofMinutes(otpMinutes))));
        c.setMaxAttempts(maxAttempts);
        c.setAttemptCount(0);
        challenges.save(c);
        notifier.deliver(contact.getPhoneNumber(), raw);
        audit.record("OTP_ISSUED", "SUCCESS", null, s.getProfileId(), s.getQrCredentialId(), s.getId(), d.getId(), r.getId(), null, correlation);
    }

    @Transactional
    public AuthorizationView verifyOtp(String handle, UUID requestId, String otp, String correlation) {
        EmergencySession s = emergency.require(handle, correlation);
        DocumentAccessRequest r = requireRequest(s, requestId);
        EmergencyContactOtpChallenge c = challenges.findTopByAccessRequestIdOrderByCreatedAtDesc(r.getId()).orElseThrow(ApiSupport.ForbiddenException::new);
        if (c.getConsumedAt() != null || !c.getExpiresAt().isAfter(Instant.now()) || c.getAttemptCount() >= c.getMaxAttempts() || r.getStatus() != AccessRequestStatus.PENDING) {
            audit.record("OTP_VERIFIED", "DENIED", null, s.getProfileId(), s.getQrCredentialId(), s.getId(), r.getDocumentId(), r.getId(), null, correlation);
            throw new ApiSupport.ForbiddenException();
        }
        c.setAttemptCount(c.getAttemptCount() + 1);
        if (!secrets.matches(otp, c.getOtpVerifier())) {
            if (c.getAttemptCount() >= c.getMaxAttempts()) c.setConsumedAt(Instant.now());
            challenges.save(c);
            audit.record("OTP_VERIFIED", "DENIED", null, s.getProfileId(), s.getQrCredentialId(), s.getId(), r.getDocumentId(), r.getId(), null, correlation);
            throw new ApiSupport.ForbiddenException();
        }
        MedicalDocument d = requireEmergencyDocument(s, r.getDocumentId());
        if (d.getAccessPolicy() != DocumentAccessPolicy.PRIVATE) throw new ApiSupport.ForbiddenException();
        c.setConsumedAt(Instant.now());
        challenges.save(c);
        r.setStatus(AccessRequestStatus.AUTHORIZED);
        r.setCompletedAt(Instant.now());
        requests.save(r);
        TemporaryDocumentAuthorization a = new TemporaryDocumentAuthorization();
        a.setId(UUID.randomUUID());
        a.setDocumentId(d.getId());
        a.setEmergencySessionId(s.getId());
        a.setAccessRequestId(r.getId());
        a.setAuthorizedContactId(c.getEmergencyContactId());
        a.setIssuedAt(Instant.now());
        a.setExpiresAt(min(s.getExpiresAt(), Instant.now().plus(Duration.ofMinutes(authorizationMinutes))));
        authorizations.save(a);
        audit.record("TEMPORARY_DOCUMENT_AUTHORIZATION_GRANTED", "SUCCESS", null, s.getProfileId(), s.getQrCredentialId(), s.getId(), d.getId(), r.getId(), a.getId(), correlation);
        return new AuthorizationView(a.getId(), a.getExpiresAt());
    }

    public String accessUrl(String handle, UUID documentId, String correlation) {
        EmergencySession s = emergency.require(handle, correlation);
        MedicalDocument d = requireEmergencyDocument(s, documentId);
        if (d.getAccessPolicy() == DocumentAccessPolicy.PRIVATE) {
            boolean allowed = requests.findByDocumentIdAndEmergencySessionId(d.getId(), s.getId()).stream().anyMatch(r -> authorizations.findByDocumentIdAndEmergencySessionIdAndAccessRequestId(d.getId(), s.getId(), r.getId()).filter(a -> a.getRevokedAt() == null && a.getExpiresAt().isAfter(Instant.now())).isPresent());
            if (!allowed) throw new ApiSupport.ForbiddenException();
            audit.record("PRIVATE_DOCUMENT_ACCESSED", "SUCCESS", null, s.getProfileId(), s.getQrCredentialId(), s.getId(), d.getId(), null, null, correlation);
        } else
            audit.record("PUBLIC_DOCUMENT_ACCESSED", "SUCCESS", null, s.getProfileId(), s.getQrCredentialId(), s.getId(), d.getId(), null, null, correlation);
        return store.downloadUrl(d.getStorageReference());
    }

    private DocumentAccessRequest requireRequest(EmergencySession s, UUID id) {
        return requests.findByIdAndEmergencySessionId(id, s.getId()).orElseThrow(ApiSupport.ForbiddenException::new);
    }

    private MedicalDocument requireEmergencyDocument(EmergencySession s, UUID id) {
        return documents.findByIdAndProfileId(id, s.getProfileId()).filter(d -> d.getLifecycleStatus() == DocumentLifecycleStatus.ACTIVE && d.getProcessingStatus() == ProcessingStatus.READY).orElseThrow(ApiSupport.ForbiddenException::new);
    }

    private EmergencyProfile ownerProfile(UUID userId) {
        return profiles.findByOwnerUserId(userId).filter(p -> p.getStatus() == ProfileStatus.ACTIVE).orElseThrow(ApiSupport.NotFoundException::new);
    }

    private MedicalDocument requireOwnerDocument(UUID userId, UUID id) {
        EmergencyProfile p = ownerProfile(userId);
        return documents.findByIdAndProfileId(id, p.getId()).orElseThrow(ApiSupport.NotFoundException::new);
    }

    private void revokeDependent(MedicalDocument d, String correlation) {
        for (DocumentAccessRequest r : requests.findByDocumentId(d.getId())) {
            r.setStatus(AccessRequestStatus.REVOKED);
            requests.save(r);
        }
        for (TemporaryDocumentAuthorization a : authorizations.findByDocumentId(d.getId())) {
            a.setRevokedAt(Instant.now());
            authorizations.save(a);
        }
    }

    private DocumentView ownerView(MedicalDocument d) {
        return new DocumentView(d.getId(), d.getDocumentName(), d.getDocumentType(), d.getAccessPolicy(), d.getProcessingStatus(), d.getLifecycleStatus(), d.getCreatedAt(), d.getUpdatedAt());
    }

    record UploadTarget(String storageReference, String uploadUrl) {
    }

    record DocumentInput(String storageReference, String documentName, String documentType,
                         DocumentAccessPolicy accessPolicy) {
    }

    record DocumentUpdate(String documentName, DocumentAccessPolicy accessPolicy) {
    }

    record DocumentView(UUID id, String documentName, String documentType, DocumentAccessPolicy accessPolicy,
                        ProcessingStatus processingStatus, DocumentLifecycleStatus lifecycleStatus, Instant createdAt,
                        Instant updatedAt) {
    }

    record EmergencyDocumentView(UUID documentHandle, String documentName, String documentType,
                                 DocumentAccessPolicy accessPolicy, boolean contactAuthorizationRequired) {
    }

    record AccessRequestView(UUID requestHandle, Instant expiresAt) {
    }

    record AuthorizationView(UUID authorizationId, Instant expiresAt) {
    }
}
