package com.sosid.core;

import com.sosid.common.ApiSupport;
import com.sosid.common.Secrets;
import com.sosid.config.JwtService;
import com.sosid.entity.UserAccount;
import com.sosid.entity.enums.DomainEnums;
import com.sosid.entity.enums.DomainEnums.AccountStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
class OwnerService {
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final ContactRepository contacts;
    private final AllergyRepository allergies;
    private final ConditionRepository conditions;
    private final MedicationRepository medications;
    private final QrCredentialRepository credentials;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final Secrets secrets;
    private final AuditService audit;

    OwnerService(UserRepository users, ProfileRepository profiles, ContactRepository contacts, AllergyRepository allergies, ConditionRepository conditions, MedicationRepository medications, QrCredentialRepository credentials, PasswordEncoder passwords, JwtService jwt, Secrets secrets, AuditService audit) {
        this.users = users;
        this.profiles = profiles;
        this.contacts = contacts;
        this.allergies = allergies;
        this.conditions = conditions;
        this.medications = medications;
        this.credentials = credentials;
        this.passwords = passwords;
        this.jwt = jwt;
        this.secrets = secrets;
        this.audit = audit;
    }

    @Transactional
    AuthResult register(String email, String password, String correlation) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.findByEmail(normalized).isPresent()) throw new ApiSupport.InvalidRequestException("email");
        UserAccount u = new UserAccount();
        Instant now = Instant.now();
        u.setId(UUID.randomUUID());
        u.setEmail(normalized);
        u.setPasswordHash(passwords.encode(password));
        u.setStatus(DomainEnums.AccountStatus.ACTIVE);
        u.setCreatedAt(now);
        u.setUpdatedAt(now);
        users.save(u);
        audit.record("ACCOUNT_CREATED", "SUCCESS", u.getId(), null, null, null, null, null, null, correlation);
        return new AuthResult(jwt.issue(u.getId(), u.getEmail()));
    }

    AuthResult login(String email, String password, String correlation) {
        Optional<UserAccount> found = users.findByEmail(email.trim().toLowerCase(Locale.ROOT));
        if (found.isEmpty() || found.get().getStatus() != DomainEnums.AccountStatus.ACTIVE || !passwords.matches(password, found.get().getPasswordHash())) {
            audit.record("AUTHENTICATION", "DENIED", null, null, null, null, null, null, null, correlation);
            throw new ApiSupport.ForbiddenException();
        }
        UserAccount u = found.get();
        audit.record("AUTHENTICATION", "SUCCESS", u.getId(), null, null, null, null, null, null, correlation);
        return new AuthResult(jwt.issue(u.getId(), u.getEmail()));
    }

    UserAccount current(UUID id) {
        return users.findById(id).filter(u -> u.getStatus() == AccountStatus.ACTIVE).orElseThrow(ApiSupport.ForbiddenException::new);
    }

    @Transactional
    ProfileView putProfile(UUID userId, ProfileInput in, String correlation) {
        current(userId);
        Instant now = Instant.now();
        EmergencyProfile p = profiles.findByOwnerUserId(userId).orElseGet(EmergencyProfile::new);
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
        profiles.save(p);
        contacts.deleteByProfileId(p.getId());
        allergies.deleteByProfileId(p.getId());
        conditions.deleteByProfileId(p.getId());
        medications.deleteByProfileId(p.getId());
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
            contacts.save(e);
        }
        i = 0;
        for (String s : in.allergies()) allergies.save(entry(new ProfileAllergy(), p.getId(), s, i++));
        i = 0;
        for (String s : in.conditions()) conditions.save(entry(new ProfileCondition(), p.getId(), s, i++));
        i = 0;
        for (MedicationInput m : in.medications()) {
            ProfileMedication x = new ProfileMedication();
            x.setId(UUID.randomUUID());
            x.setProfileId(p.getId());
            x.setLabel(m.label());
            x.setDoseInstruction(m.doseInstruction());
            x.setDisplayOrder(i++);
            medications.save(x);
        }
        audit.record("PROFILE_UPDATED", "SUCCESS", userId, p.getId(), null, null, null, null, null, correlation);
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
        return view(profiles.findByOwnerUserId(userId).orElseThrow(ApiSupport.NotFoundException::new));
    }

    EmergencyProfileView getProfileById(UUID profileId) {
        EmergencyProfile p = profiles.findById(profileId).orElseThrow(ApiSupport.ForbiddenException::new);
        return new EmergencyProfileView(p.getDisplayName(), p.getDateOfBirth(), p.getBloodGroup(), p.getCriticalInstructions(), contacts.findByProfileIdOrderByDisplayOrder(p.getId()).stream().filter(EmergencyContact::isActive).map(c -> new EmergencyContactHandle(c.getId(), c.getName(), c.getRelationship(), c.getPhoneNumber())).toList(), allergies.findByProfileIdOrderByDisplayOrder(p.getId()).stream().map(ProfileAllergy::getLabel).toList(), conditions.findByProfileIdOrderByDisplayOrder(p.getId()).stream().map(ProfileCondition::getLabel).toList(), medications.findByProfileIdOrderByDisplayOrder(p.getId()).stream().map(m -> new MedicationInput(m.getLabel(), m.getDoseInstruction())).toList());
    }

    private ProfileView view(EmergencyProfile p) {
        return new ProfileView(p.getId(), p.getDisplayName(), p.getDateOfBirth(), p.getBloodGroup(), p.getCriticalInstructions(), contacts.findByProfileIdOrderByDisplayOrder(p.getId()).stream().map(c -> new ContactView(c.getId(), c.getName(), c.getRelationship(), c.getPhoneNumber())).toList(), allergies.findByProfileIdOrderByDisplayOrder(p.getId()).stream().map(ProfileAllergy::getLabel).toList(), conditions.findByProfileIdOrderByDisplayOrder(p.getId()).stream().map(ProfileCondition::getLabel).toList(), medications.findByProfileIdOrderByDisplayOrder(p.getId()).stream().map(m -> new MedicationInput(m.getLabel(), m.getDoseInstruction())).toList());
    }

    @Transactional
    QrResult createQr(UUID userId, String correlation) {
        EmergencyProfile p = profiles.findByOwnerUserId(userId).filter(x -> x.getStatus() == ProfileStatus.ACTIVE).orElseThrow(ApiSupport.NotFoundException::new);
        String secret = secrets.opaqueToken();
        QrCredential q = new QrCredential();
        q.setId(UUID.randomUUID());
        q.setProfileId(p.getId());
        q.setTokenVerifier(secrets.hash(secret));
        q.setStatus(CredentialStatus.ACTIVE);
        q.setIssuedAt(Instant.now());
        credentials.save(q);
        audit.record("QR_ISSUED", "SUCCESS", userId, p.getId(), q.getId(), null, null, null, null, correlation);
        return new QrResult(q.getId(), q.getId() + "." + secret);
    }

    List<QrView> listQrs(UUID userId) {
        EmergencyProfile p = profiles.findByOwnerUserId(userId).orElseThrow(ApiSupport.NotFoundException::new);
        return credentials.findByProfileId(p.getId()).stream().map(q -> new QrView(q.getId(), q.getStatus(), q.getIssuedAt(), q.getRevokedAt())).toList();
    }

    @Transactional
    void revokeQr(UUID userId, UUID id, String correlation) {
        EmergencyProfile p = profiles.findByOwnerUserId(userId).orElseThrow(ApiSupport.NotFoundException::new);
        QrCredential q = credentials.findById(id).filter(x -> x.getProfileId().equals(p.getId())).orElseThrow(ApiSupport.NotFoundException::new);
        q.setStatus(CredentialStatus.REVOKED);
        q.setRevokedAt(Instant.now());
        credentials.save(q);
        audit.record("QR_REVOKED", "SUCCESS", userId, p.getId(), q.getId(), null, null, null, null, correlation);
    }

    record AuthResult(String accessToken) {
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
