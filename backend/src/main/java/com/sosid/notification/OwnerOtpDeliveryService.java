package com.sosid.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/** Isolates OTP transport. Development-only logging must be disabled in production. */
@Service
public class OwnerOtpDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(OwnerOtpDeliveryService.class);
    private final boolean developmentLogEnabled;
    private final boolean snsEnabled;
    private final Region region;

    public OwnerOtpDeliveryService(
            @Value("${sosid.otp.owner-development-log-enabled:true}") boolean developmentLogEnabled,
            @Value("${sosid.otp.owner-sns-enabled:false}") boolean snsEnabled,
            @Value("${sosid.storage.region}") String region) {
        this.developmentLogEnabled = developmentLogEnabled;
        this.snsEnabled = snsEnabled;
        this.region = Region.of(region);
    }

    public void deliver(String phoneNumber, String otp) {
        if (snsEnabled) {
            try (SnsClient sns = SnsClient.builder().region(region).build()) {
                sns.publish(PublishRequest.builder().phoneNumber(phoneNumber)
                        .message("Your SOS-ID sign-in verification code is: " + otp).build());
            }
            return;
        }
        if (developmentLogEnabled) {
            log.warn("Development-only owner OTP issued for {}: {}. Disable sosid.otp.owner-development-log-enabled outside development.", phoneNumber, otp);
            return;
        }
        throw new IllegalStateException("Owner OTP delivery is not configured");
    }
}
