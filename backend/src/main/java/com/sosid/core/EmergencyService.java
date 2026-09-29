package com.sosid.core;

import com.sosid.common.ApiSupport;
import com.sosid.common.Secrets;
import com.sosid.entity.EmergencySession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
class EmergencyService {
    private final QrCredentialRepository credentials;
    private final EmergencySessionRepository sessions;
    private final ProfileRepository profiles;
    private final UserRepository users;
    private final Secrets secrets;
    private final AuditService audit;
    private final long minutes;

    EmergencyService(QrCredentialRepository credentials, EmergencySessionRepository sessions, ProfileRepository profiles, UserRepository users, Secrets secrets, AuditService audit, @Value("${sosid.security.emergency-session-minutes}") long minutes) {
        this.credentials = credentials;
        this.sessions = sessions;
        this.profiles = profiles;
        this.users = users;
        this.secrets = secrets;
        this.audit = audit;
        this.minutes = minutes;
    }

    @Transactional
    SessionResult create(String qrToken, String correlation) {
        try {
            String[] parts = qrToken.split("\\.", 2);
            QrCredential q = credentials.findById(UUID.fromString(parts[0])).orElseThrow(ApiSupport.ForbiddenException::new);
            if (parts.length != 2 || !secrets.matches(parts[1], q.getTokenVerifier()) || q.getStatus() != CredentialStatus.ACTIVE || (q.getExpiresAt() != null && q.getExpiresAt().isBefore(Instant.now())))
                throw new ApiSupport.ForbiddenException();
            EmergencyProfile p = activeProfile(q.getProfileId());
            String secret = secrets.opaqueToken();
            EmergencySession s = new EmergencySession();
            s.setId(UUID.randomUUID());
            s.setHandleVerifier(secrets.hash(secret));
            s.setQrCredentialId(q.getId());
            s.setProfileId(p.getId());
            s.setIssuedAt(Instant.now());
            s.setExpiresAt(Instant.now().plus(Duration.ofMinutes(minutes)));
            sessions.save(s);
            q.setLastUsedAt(Instant.now());
            credentials.save(q);
            audit.record("SESSION_CREATED", "SUCCESS", null, p.getId(), q.getId(), s.getId(), null, null, null, correlation);
            return new SessionResult(s.getId() + "." + secret, s.getExpiresAt());
        } catch (IllegalArgumentException ex) {
            throw new ApiSupport.ForbiddenException();
        }
    }

    EmergencySession require(String handle, String correlation) {
        try {
            String[] parts = handle.split("\\.", 2);
            EmergencySession s = sessions.findById(UUID.fromString(parts[0])).orElseThrow(ApiSupport.ForbiddenException::new);
            if (parts.length != 2 || !secrets.matches(parts[1], s.getHandleVerifier()) || s.getRevokedAt() != null || !s.getExpiresAt().isAfter(Instant.now()))
                throw new ApiSupport.ForbiddenException();
            QrCredential q = credentials.findById(s.getQrCredentialId()).orElseThrow(ApiSupport.ForbiddenException::new);
            if (q.getStatus() != CredentialStatus.ACTIVE) throw new ApiSupport.ForbiddenException();
            activeProfile(s.getProfileId());
            return s;
        } catch (IllegalArgumentException ex) {
            throw new ApiSupport.ForbiddenException();
        }
    }

    EmergencyProfile activeProfile(UUID id) {
        EmergencyProfile p = profiles.findById(id).filter(x -> x.getStatus() == ProfileStatus.ACTIVE).orElseThrow(ApiSupport.ForbiddenException::new);
        UserAccount u = users.findById(p.getOwnerUserId()).filter(x -> x.getStatus() == AccountStatus.ACTIVE).orElseThrow(ApiSupport.ForbiddenException::new);
        return p;
    }

    @Transactional
    void close(String handle, String correlation) {
        EmergencySession s = require(handle, correlation);
        s.setRevokedAt(Instant.now());
        sessions.save(s);
        audit.record("SESSION_CLOSED", "SUCCESS", null, s.getProfileId(), s.getQrCredentialId(), s.getId(), null, null, null, correlation);
    }

    void recordDisclosure(EmergencySession s, String correlation) {
        audit.record("PROFILE_DISCLOSED", "SUCCESS", null, s.getProfileId(), s.getQrCredentialId(), s.getId(), null, null, null, correlation);
    }

    record SessionResult(String sessionHandle, Instant expiresAt) {
    }
}
