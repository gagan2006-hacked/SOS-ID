package com.sosid.core;

import com.sosid.common.ApiSupport;
import com.sosid.common.Secrets;
import com.sosid.entity.OwnerOtpChallenge;
import com.sosid.entity.UserAccount;
import com.sosid.entity.enums.DomainEnums.AccountStatus;
import com.sosid.notification.OwnerOtpDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OwnerOtpAuthenticationServiceTest {
    @Mock private OwnerOtpChallengeRepository challenges;
    @Mock private UserRepository users;
    @Mock private OwnerService owners;
    @Mock private OwnerOtpDeliveryService delivery;
    @Mock private AuditService audit;
    @Captor private ArgumentCaptor<OwnerOtpChallenge> challengeCaptor;
    private OwnerOtpAuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new OwnerOtpAuthenticationService(challenges, users, owners,
                new Secrets(new BCryptPasswordEncoder()), delivery, audit, 5, 3, 3);
    }

    @Test
    void requestStoresVerifierAndDeliversOtpWithoutPersistingPlaintext() {
        when(challenges.countByPhoneNumberAndCreatedAtAfter(anyString(), any())).thenReturn(0L);

        service.request("+91 98765-43210", "correlation");

        verify(challenges).save(challengeCaptor.capture());
        OwnerOtpChallenge stored = challengeCaptor.getValue();
        ArgumentCaptor<String> deliveredOtp = ArgumentCaptor.forClass(String.class);
        verify(delivery).deliver(eq("+919876543210"), deliveredOtp.capture());
        assertNotEquals(deliveredOtp.getValue(), stored.getOtpVerifier());
        assertTrue(new Secrets(new BCryptPasswordEncoder()).matches(deliveredOtp.getValue(), stored.getOtpVerifier()));
        assertNull(stored.getConsumedAt());
    }

    @Test
    void requestIsThrottledBeforeAnotherOtpIsDelivered() {
        when(challenges.countByPhoneNumberAndCreatedAtAfter(anyString(), any())).thenReturn(3L);

        service.request("+919876543210", "correlation");

        verify(delivery, never()).deliver(anyString(), anyString());
        verify(challenges, never()).save(any());
    }

    @Test
    void successfulVerificationConsumesChallengeAndRejectsReplay() {
        Secrets secrets = new Secrets(new BCryptPasswordEncoder());
        OwnerOtpChallenge challenge = new OwnerOtpChallenge();
        challenge.setId(UUID.randomUUID()); challenge.setPhoneNumber("+919876543210");
        challenge.setOtpVerifier(secrets.hash("123456")); challenge.setCreatedAt(Instant.now());
        challenge.setExpiresAt(Instant.now().plusSeconds(120)); challenge.setMaxAttempts(3);
        UserAccount owner = new UserAccount(); owner.setId(UUID.randomUUID()); owner.setPhoneNumber("+919876543210"); owner.setStatus(AccountStatus.ACTIVE);
        when(challenges.findTopByPhoneNumberOrderByCreatedAtDesc("+919876543210")).thenReturn(Optional.of(challenge));
        when(users.findByPhoneNumber("+919876543210")).thenReturn(Optional.of(owner));
        when(owners.issueTokensForAuthenticatedUser(owner)).thenReturn(null);

        service.verify("+919876543210", "123456", "correlation");
        assertNotNull(challenge.getConsumedAt());
        assertThrows(ApiSupport.ForbiddenException.class, () -> service.verify("+919876543210", "123456", "correlation"));
    }
}
