package com.sosid.core;

import com.sosid.common.ApiSupport;
import com.sosid.common.Secrets;
import com.sosid.entity.OwnerOtpChallenge;
import com.sosid.entity.UserAccount;
import com.sosid.entity.enums.DomainEnums.AccountStatus;
import com.sosid.notification.OwnerOtpDeliveryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class OwnerOtpAuthenticationService {
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private final OwnerOtpChallengeRepository challenges;
    private final UserRepository users;
    private final OwnerService owners;
    private final Secrets secrets;
    private final OwnerOtpDeliveryService delivery;
    private final AuditService audit;
    private final long otpMinutes;
    private final int maxAttempts;
    private final int maxRequestsPerWindow;

    OwnerOtpAuthenticationService(OwnerOtpChallengeRepository challenges, UserRepository users, OwnerService owners,
                                  Secrets secrets, OwnerOtpDeliveryService delivery, AuditService audit,
                                  @Value("${sosid.security.owner-otp-minutes:5}") long otpMinutes,
                                  @Value("${sosid.security.max-otp-attempts:5}") int maxAttempts,
                                  @Value("${sosid.security.owner-otp-max-requests-per-15-minutes:3}") int maxRequestsPerWindow) {
        this.challenges = challenges;
        this.users = users;
        this.owners = owners;
        this.secrets = secrets;
        this.delivery = delivery;
        this.audit = audit;
        this.otpMinutes = otpMinutes;
        this.maxAttempts = maxAttempts;
        this.maxRequestsPerWindow = maxRequestsPerWindow;
    }

    @Transactional
    public void request(String rawPhoneNumber, String correlationId) {
        String phoneNumber = normalize(rawPhoneNumber);
        Instant now = Instant.now();
        if (challenges.countByPhoneNumberAndCreatedAtAfter(phoneNumber, now.minus(Duration.ofMinutes(15))) >= maxRequestsPerWindow) {
            audit.record("OWNER_OTP_REQUESTED", "DENIED", null, null, null, null, null, null, null, correlationId);
            return; // enumeration- and flood-resistant response
        }
        String otp = secrets.otp();
        OwnerOtpChallenge challenge = new OwnerOtpChallenge();
        challenge.setId(UUID.randomUUID());
        challenge.setPhoneNumber(phoneNumber);
        challenge.setOtpVerifier(secrets.hash(otp));
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plus(Duration.ofMinutes(otpMinutes)));
        challenge.setAttemptCount(0);
        challenge.setMaxAttempts(maxAttempts);
        challenges.save(challenge);
        delivery.deliver(phoneNumber, otp);
        audit.record("OWNER_OTP_REQUESTED", "SUCCESS", null, null, null, null, null, null, null, correlationId);
    }

    @Transactional
    public OwnerService.AuthResult verify(String rawPhoneNumber, String otp, String correlationId) {
        String phoneNumber = normalize(rawPhoneNumber);
        OwnerOtpChallenge challenge = challenges.findTopByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .orElseThrow(ApiSupport.ForbiddenException::new);
        Instant now = Instant.now();
        if (challenge.getConsumedAt() != null || !challenge.getExpiresAt().isAfter(now)
                || challenge.getAttemptCount() >= challenge.getMaxAttempts()) {
            audit.record("OWNER_OTP_VERIFICATION_FAILED", "DENIED", null, null, null, null, null, null, null, correlationId);
            throw new ApiSupport.ForbiddenException();
        }
        challenge.setAttemptCount(challenge.getAttemptCount() + 1);
        if (!secrets.matches(otp, challenge.getOtpVerifier())) {
            if (challenge.getAttemptCount() >= challenge.getMaxAttempts()) challenge.setConsumedAt(now);
            challenges.save(challenge);
            audit.record("OWNER_OTP_VERIFICATION_FAILED", "DENIED", null, null, null, null, null, null, null, correlationId);
            throw new ApiSupport.ForbiddenException();
        }
        challenge.setConsumedAt(now);
        challenges.save(challenge);
        UserAccount user = users.findByPhoneNumber(phoneNumber).orElseGet(() -> createOwner(phoneNumber));
        if (user.getStatus() != AccountStatus.ACTIVE) throw new ApiSupport.ForbiddenException();
        audit.record("OWNER_OTP_VERIFICATION_SUCCESS", "SUCCESS", user.getId(), null, null, null, null, null, null, correlationId);
        audit.record("OWNER_LOGIN", "SUCCESS", user.getId(), null, null, null, null, null, null, correlationId);
        return owners.issueTokensForAuthenticatedUser(user);
    }

    private UserAccount createOwner(String phoneNumber) {
        Instant now = Instant.now();
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setPhoneNumber(phoneNumber);
        user.setStatus(AccountStatus.ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return users.save(user);
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim().replace(" ", "").replace("-", "");
        if (!E164.matcher(normalized).matches()) throw new ApiSupport.InvalidRequestException("phoneNumber");
        return normalized;
    }
}
