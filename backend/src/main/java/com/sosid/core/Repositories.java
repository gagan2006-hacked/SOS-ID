package com.sosid.core;

import com.sosid.entity.*;
import com.sosid.entity.enums.DomainEnums;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface UserRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByPhoneNumber(String phoneNumber);
}

interface OwnerOtpChallengeRepository extends JpaRepository<OwnerOtpChallenge, UUID> {
    Optional<OwnerOtpChallenge> findTopByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);

    long countByPhoneNumberAndCreatedAtAfter(String phoneNumber, java.time.Instant after);
}

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findById(UUID id);
}

interface ProfileRepository extends JpaRepository<EmergencyProfile, UUID> {
    Optional<EmergencyProfile> findByOwnerUserId(UUID ownerUserId);
}

interface ContactRepository extends JpaRepository<EmergencyContact, UUID> {
    List<EmergencyContact> findByProfileIdOrderByDisplayOrder(UUID profileId);

    Optional<EmergencyContact> findByIdAndProfileIdAndActiveTrue(UUID id, UUID profileId);

    void deleteByProfileId(UUID profileId);
}

interface AllergyRepository extends JpaRepository<ProfileAllergy, UUID> {
    List<ProfileAllergy> findByProfileIdOrderByDisplayOrder(UUID profileId);

    void deleteByProfileId(UUID profileId);
}

interface ConditionRepository extends JpaRepository<ProfileCondition, UUID> {
    List<ProfileCondition> findByProfileIdOrderByDisplayOrder(UUID profileId);

    void deleteByProfileId(UUID profileId);
}

interface MedicationRepository extends JpaRepository<ProfileMedication, UUID> {
    List<ProfileMedication> findByProfileIdOrderByDisplayOrder(UUID profileId);

    void deleteByProfileId(UUID profileId);
}

interface QrCredentialRepository extends JpaRepository<QrCredential, UUID> {
    Optional<QrCredential> findByTokenVerifier(String verifier);

    List<QrCredential> findByProfileId(UUID profileId);
}

interface EmergencySessionRepository extends JpaRepository<EmergencySession, UUID> {
    Optional<EmergencySession> findByHandleVerifier(String verifier);
}

interface MedicalDocumentRepository extends JpaRepository<MedicalDocument, UUID> {
    List<MedicalDocument> findByProfileIdAndLifecycleStatus(UUID profileId, DomainEnums.DocumentLifecycleStatus status);

    Optional<MedicalDocument> findByIdAndProfileId(UUID id, UUID profileId);
}

interface AccessRequestRepository extends JpaRepository<DocumentAccessRequest, UUID> {
    Optional<DocumentAccessRequest> findByIdAndEmergencySessionId(UUID id, UUID sessionId);

    List<DocumentAccessRequest> findByDocumentId(UUID documentId);

    List<DocumentAccessRequest> findByDocumentIdAndEmergencySessionId(UUID documentId, UUID sessionId);
}

interface OtpChallengeRepository extends JpaRepository<EmergencyContactOtpChallenge, UUID> {
    Optional<EmergencyContactOtpChallenge> findTopByAccessRequestIdOrderByCreatedAtDesc(UUID requestId);
}

interface TemporaryAuthorizationRepository extends JpaRepository<TemporaryDocumentAuthorization, UUID> {
    Optional<TemporaryDocumentAuthorization> findByDocumentIdAndEmergencySessionIdAndAccessRequestId(UUID documentId, UUID sessionId, UUID requestId);

    List<TemporaryDocumentAuthorization> findByDocumentId(UUID documentId);
}

interface AuditRepository extends JpaRepository<AuditEvent, UUID> {
}
