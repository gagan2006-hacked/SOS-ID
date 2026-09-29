package com.sosid.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtService {
    private final Algorithm algorithm;
    private final long minutes;

    public JwtService(@Value("${sosid.security.jwt-secret}") String secret, @Value("${sosid.security.access-token-minutes}") long minutes) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.minutes = minutes;
    }

    public String issue(UUID userId, String email) {
        Instant now = Instant.now();
        return JWT.create().withSubject(userId.toString()).withClaim("email", email).withClaim("role", "OWNER").withIssuedAt(Date.from(now)).withExpiresAt(Date.from(now.plus(Duration.ofMinutes(minutes)))).sign(algorithm);
    }

    public UUID verify(String token) {
        return UUID.fromString(JWT.require(algorithm).build().verify(token).getSubject());
    }
}
