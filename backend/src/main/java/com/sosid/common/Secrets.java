package com.sosid.common;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class Secrets {
  private final SecureRandom random=new SecureRandom();
  private final PasswordEncoder encoder;

  public Secrets(PasswordEncoder encoder) {
    this.encoder=encoder; }

  public String opaqueToken() {
    byte[] b=new byte[32]; random.nextBytes(b); return Base64.getUrlEncoder()
            .withoutPadding().encodeToString(b); }

  public String otp() {
    return String.format("%06d", random.nextInt(1_000_000)); }

  public String hash(String secret) {
    return encoder.encode(secret); }

  public boolean matches(String raw,String hash) {
    return encoder.matches(raw,hash); }
}
