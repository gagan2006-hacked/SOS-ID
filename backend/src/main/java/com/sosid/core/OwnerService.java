package com.sosid.core;

import com.sosid.common.ApiSupport;
import com.sosid.common.Secrets;
import com.sosid.config.JwtService;
import com.sosid.entity.*;
import com.sosid.entity.enums.DomainEnums;
import com.sosid.entity.enums.DomainEnums.AccountStatus;
import com.sosid.entity.enums.DomainEnums.CredentialStatus;
import com.sosid.entity.enums.DomainEnums.ProfileStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class OwnerService {
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ContactRepository contactRepository;
    private final AllergyRepository allergyRepository;
    private final ConditionRepository conditionRepository;
    private final MedicationRepository medicationRepository;
    private final QrCredentialRepository qrCredentialRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Secrets secrets;
    private final AuditService auditService;
    @org.springframework.beans.factory.annotation.Value("${sosid.security.refresh-token-days:30}")
    private long refreshTokenDays;

    @Transactional
    public AuthResult register(String email, String password, String correlation) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(normalized).isPresent()) throw new ApiSupport.InvalidRequestException("email");
        UserAccount u = new UserAccount();
        Instant now = Instant.now();
        u.setId(UUID.randomUUID());
        u.setEmail(normalized);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setStatus(DomainEnums.AccountStatus.ACTIVE);
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        userRepository.save(u);
        auditService.record("ACCOUNT_CREATED", "SUCCESS", u.getId(), null, null, null, null, null, null, correlation);
        return issueTokens(u);
    }

    public AuthResult login(String email, String password, String correlation) {
        Optional<UserAccount> found = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT));
        if (found.isEmpty() || found.get().getStatus() != DomainEnums.AccountStatus.ACTIVE || !passwordEncoder.matches(password, found.get().getPasswordHash())) {
            auditService.record("AUTHENTICATION", "DENIED", null, null, null, null, null, null, null, correlation);
            throw new ApiSupport.ForbiddenException();
        }
        UserAccount u = found.get();
        auditService.record("AUTHENTICATION", "SUCCESS", u.getId(), null, null, null, null, null, null, correlation);
        return issueTokens(u);
    }

    @Transactional
    public AuthResult refresh(String refreshToken, String correlation) {
        RefreshToken stored = requireActiveRefreshToken(refreshToken);
        UserAccount user = current(stored.getUserId());
        stored.setReplacedAt(Instant.now());
        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);
        auditService.record("REFRESH_TOKEN_ROTATED", "SUCCESS", user.getId(), null, null, null, null, null, null, correlation);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken, String correlation) {
        try {
            RefreshToken stored = requireActiveRefreshToken(refreshToken);
            stored.setRevokedAt(Instant.now());
            refreshTokenRepository.save(stored);
            auditService.record("REFRESH_TOKEN_REVOKED", "SUCCESS", stored.getUserId(), null, null, null, null, null, null, correlation);
        } catch (ApiSupport.ForbiddenException ignored) {
            auditService.record("REFRESH_TOKEN_REVOKED", "DENIED", null, null, null, null, null, null, null, correlation);
        }
    }

    private AuthResult issueTokens(UserAccount user) {
        String secret = secrets.opaqueToken();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(refreshTokenDays));
        RefreshToken refresh = new RefreshToken();
        refresh.setId(UUID.randomUUID());
        refresh.setUserId(user.getId());
        refresh.setTokenVerifier(secrets.hash(secret));
        refresh.setCreatedAt(Instant.now());
        refresh.setExpiresAt(expiresAt);
        refreshTokenRepository.save(refresh);
        return new AuthResult(jwtService.issue(user.getId(), user.getEmail()), refresh.getId() + "." + secret, expiresAt);
    }

    private RefreshToken requireActiveRefreshToken(String rawToken) {
        try {
            String[] parts = rawToken.split("\\.", 2);
            if (parts.length != 2) throw new ApiSupport.ForbiddenException();
            RefreshToken token = refreshTokenRepository.findById(UUID.fromString(parts[0])).orElseThrow(ApiSupport.ForbiddenException::new);
            if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(Instant.now()) || !secrets.matches(parts[1], token.getTokenVerifier())) {
                throw new ApiSupport.ForbiddenException();
            }
            return token;
        } catch (IllegalArgumentException exception) {
            throw new ApiSupport.ForbiddenException();
        }
    }

    UserAccount current(UUID id) {
        return userRepository.findById(id).filter(u -> u.getStatus() == AccountStatus.ACTIVE).orElseThrow(ApiSupport.ForbiddenException::new);
    }

    @Transactional
    public ProfileView putProfile(UUID userId, ProfileInput in, String correlation) {
        current(userId);
        Instant now = Instant.now();
        EmergencyProfile p = profileRepository.findByOwnerUserId(userId).orElseGet(EmergencyProfile::new);
        if (p.getId() == null) {
            p.setId(UUID.randomUUID());
            p.setOwnerUserId(userId);
            p.setCreatedAt(now);
            p.setStatus(ProfileStatus.ACTIVE);
        }
        p.setDisplayName(in.displayName());
        p.setDateOfBirth(in.dateOfBirth());
        p.setBloodGroup(in.bloodGroup());
        p.setCriticalInstructions(in.criticalInstructions());
        p.setUpdatedAt(now);
        profileRepository.save(p);
        contactRepository.deleteByProfileId(p.getId());
        allergyRepository.deleteByProfileId(p.getId());
        conditionRepository.deleteByProfileId(p.getId());
        medicationRepository.deleteByProfileId(p.getId());
        int i = 0;
        for (ContactInput c : in.contacts()) {
            EmergencyContact e = new EmergencyContact();
            e.setId(UUID.randomUUID());
            e.setProfileId(p.getId());
            e.setName(c.name());
            e.setRelationship(c.relationship());
            e.setPhoneNumber(c.phoneNumber());
            e.setDisplayOrder(i++);
            e.setActive(true);
            contactRepository.save(e);
        }
        i = 0;
        for (String s : in.allergies()) allergyRepository.save(entry(new ProfileAllergy(), p.getId(), s, i++));
        i = 0;
        for (String s : in.conditions()) conditionRepository.save(entry(new ProfileCondition(), p.getId(), s, i++));
        i = 0;
        for (MedicationInput m : in.medications()) {
            ProfileMedication x = new ProfileMedication();
            x.setId(UUID.randomUUID());
            x.setProfileId(p.getId());
            x.setLabel(m.label());
            x.setDoseInstruction(m.doseInstruction());
            x.setDisplayOrder(i++);
            medicationRepository.save(x);
        }
        auditService.record("PROFILE_UPDATED", "SUCCESS", userId, p.getId(), null, null, null, null, null, correlation);
        return view(p);
    }

    private <T> T entry(T object, UUID profileId, String label, int order) {
        if (object instanceof ProfileAllergy x) {
            x.setId(UUID.randomUUID());
            x.setProfileId(profileId);
            x.setLabel(label);
            x.setDisplayOrder(order);
        }
        if (object instanceof ProfileCondition x) {
            x.setId(UUID.randomUUID());
            x.setProfileId(profileId);
            x.setLabel(label);
            x.setDisplayOrder(order);
        }
        return object;
    }

    ProfileView getProfile(UUID userId) {
        return view(profileRepository.findByOwnerUserId(userId).orElseThrow(ApiSupport.NotFoundException::new));
    }

    EmergencyProfileView getProfileById(UUID profileId) {
        EmergencyProfile p = profileRepository.findById(profileId).orElseThrow(ApiSupport.ForbiddenException::new);
        return new EmergencyProfileView(p.getDisplayName(), p.getDateOfBirth(), p.getBloodGroup(), p.getCriticalInstructions(), contactRepository.findByProfileIdOrderByDisplayOrder(p.getId()).stream().filter(EmergencyContact::isActive).map(c -> new EmergencyContactHandle(c.getId(), c.getName(), c.getRelationship(), c.getPhoneNumber())).toList(), allergyRepository.findByProfileIdOrderByDisplayOrder(p.getId()).stream().map(ProfileAllergy::getLabel).toList(), conditionRepository.findByProfileIdOrderByDisplayOrder(p.getId()).stream().map(ProfileCondition::getLabel).toList(), medicationRepository.findByProfileIdOrderByDisplayOrder(p.getId()).stream().map(m -> new MedicationInput(m.getLabel(), m.getDoseInstruction())).toList());
    }

    private ProfileView view(EmergencyProfile p) {
        return new ProfileView(p.getId(), p.getDisplayName(), p.getDateOfBirth(), p.getBloodGroup(), p.getCriticalInstructions(), contactRepository.findByProfileIdOrderByDisplayOrder(p.getId()).stream().map(c -> new ContactView(c.getId(), c.getName(), c.getRelationship(), c.getPhoneNumber())).toList(), allergyRepository.findByProfileIdOrderByDisplayOrder(p.getId()).stream().map(ProfileAllergy::getLabel).toList(), conditionRepository.findByProfileIdOrderByDisplayOrder(p.getId()).stream().map(ProfileCondition::getLabel).toList(), medicationRepository.findByProfileIdOrderByDisplayOrder(p.getId()).stream().map(m -> new MedicationInput(m.getLabel(), m.getDoseInstruction())).toList());
    }

    @Transactional
    public QrResult createQr(UUID userId, String correlation) {
        EmergencyProfile p = profileRepository.findByOwnerUserId(userId).filter(x -> x.getStatus() == ProfileStatus.ACTIVE).orElseThrow(ApiSupport.NotFoundException::new);
        String secret = secrets.opaqueToken();
        QrCredential q = new QrCredential();
        q.setId(UUID.randomUUID());
        q.setProfileId(p.getId());
        q.setTokenVerifier(secrets.hash(secret));
        q.setStatus(CredentialStatus.ACTIVE);
        q.setIssuedAt(Instant.now());
        qrCredentialRepository.save(q);
        auditService.record("QR_ISSUED", "SUCCESS", userId, p.getId(), q.getId(), null, null, null, null, correlation);
        return new QrResult(q.getId(), q.getId() + "." + secret);
    }

    List<QrView> listQrs(UUID userId) {
        EmergencyProfile p = profileRepository.findByOwnerUserId(userId).orElseThrow(ApiSupport.NotFoundException::new);
        return qrCredentialRepository.findByProfileId(p.getId()).stream().map(q -> new QrView(q.getId(), q.getStatus(), q.getIssuedAt(), q.getRevokedAt())).toList();
    }

    @Transactional
    public void revokeQr(UUID userId, UUID id, String correlation) {
        EmergencyProfile p = profileRepository.findByOwnerUserId(userId).orElseThrow(ApiSupport.NotFoundException::new);
        QrCredential q = qrCredentialRepository.findById(id).filter(x -> x.getProfileId().equals(p.getId())).orElseThrow(ApiSupport.NotFoundException::new);
        q.setStatus(CredentialStatus.REVOKED);
        q.setRevokedAt(Instant.now());
        qrCredentialRepository.save(q);
        auditService.record("QR_REVOKED", "SUCCESS", userId, p.getId(), q.getId(), null, null, null, null, correlation);
    }

    record AuthResult(String accessToken, String refreshToken, Instant refreshTokenExpiresAt) {
    }

    record ContactInput(String name, String relationship, String phoneNumber) {
    }

    record ContactView(UUID id, String name, String relationship, String phoneNumber) {
    }

    record EmergencyContactHandle(UUID contactHandle, String name, String relationship, String phoneNumber) {
    }

    record MedicationInput(String label, String doseInstruction) {
    }

    record ProfileInput(String displayName, LocalDate dateOfBirth, String bloodGroup, String criticalInstructions,
                        List<ContactInput> contacts, List<String> allergies, List<String> conditions,
                        List<MedicationInput> medications) {
        ProfileInput {
            contacts = contacts == null ? List.of() : contacts;
            allergies = allergies == null ? List.of() : allergies;
            conditions = conditions == null ? List.of() : conditions;
            medications = medications == null ? List.of() : medications;
        }
    }

    record ProfileView(UUID id, String displayName, LocalDate dateOfBirth, String bloodGroup,
                       String criticalInstructions, List<ContactView> contacts, List<String> allergies,
                       List<String> conditions, List<MedicationInput> medications) {
    }

    record EmergencyProfileView(String displayName, LocalDate dateOfBirth, String bloodGroup,
                                String criticalInstructions, List<EmergencyContactHandle> emergencyContacts,
                                List<String> allergies, List<String> conditions, List<MedicationInput> medications) {
    }

    record QrResult(UUID credentialId, String qrToken) {
    }

    record QrView(UUID id, CredentialStatus status, Instant issuedAt, Instant revokedAt) {
    }
}
