package com.sosid.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

@Service
public class OtpNotifier {
    private static final Logger log = LoggerFactory.getLogger(OtpNotifier.class);
    private final boolean enabled;
    private final boolean developmentLogEnabled;
    private final Region region;

    public OtpNotifier(@Value("${sosid.otp.delivery-enabled}") boolean enabled,
                       @Value("${sosid.otp.contact-development-log-enabled:true}") boolean developmentLogEnabled,
                       @Value("${sosid.storage.region}") String region) {
        this.enabled = enabled;
        this.developmentLogEnabled = developmentLogEnabled;
        this.region = Region.of(region);
    }

    public void deliver(String phoneNumber, String otp) {
        if (!enabled) {
            if (developmentLogEnabled) {
                log.warn("Development-only emergency-contact OTP issued for {}: {}. Disable sosid.otp.contact-development-log-enabled outside development.", phoneNumber, otp);
                return;
            }
            throw new IllegalStateException("OTP delivery is not configured");
        }
        try (SnsClient sns = SnsClient.builder().region(region).build()) {
            sns.publish(PublishRequest.builder().phoneNumber(phoneNumber).message("Your SOS-ID emergency document verification code is: " + otp).build());
        }
    }
}
