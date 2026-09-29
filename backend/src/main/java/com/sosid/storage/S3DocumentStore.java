package com.sosid.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

@Service
public class S3DocumentStore {
    private final String bucket;
    private final Region region;
    private final long uploadMinutes, downloadMinutes;

    public S3DocumentStore(@Value("${sosid.storage.bucket}") String bucket, @Value("${sosid.storage.region}") String region, @Value("${sosid.storage.upload-minutes}") long uploadMinutes, @Value("${sosid.storage.download-minutes}") long downloadMinutes) {
        this.bucket = bucket;
        this.region = Region.of(region);
        this.uploadMinutes = uploadMinutes;
        this.downloadMinutes = downloadMinutes;
    }

    public String uploadUrl(String key, String contentType) {
        requireBucket();
        try (S3Presigner p = S3Presigner.builder().region(region).build()) {
            return p.presignPutObject(b -> b.signatureDuration(Duration.ofMinutes(uploadMinutes)).putObjectRequest(r -> r.bucket(bucket).key(key).contentType(contentType))).url().toString();
        }
    }

    public String downloadUrl(String key) {
        requireBucket();
        try (S3Presigner p = S3Presigner.builder().region(region).build()) {
            return p.presignGetObject(b -> b.signatureDuration(Duration.ofMinutes(downloadMinutes)).getObjectRequest(r -> r.bucket(bucket).key(key))).url().toString();
        }
    }

    private void requireBucket() {
        if (bucket == null || bucket.isBlank()) throw new IllegalStateException("S3 bucket is not configured");
    }
}
