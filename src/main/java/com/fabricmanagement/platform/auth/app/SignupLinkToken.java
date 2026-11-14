package com.fabricmanagement.platform.auth.app;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * The secret in a signup link. The pending signup row keeps only its SHA-256 hash, so a leaked
 * {@code pending_signup} table cannot be used to complete somebody's signup. The raw value still
 * lives in the mail: in the published event and in the mail outbox until they are cleaned up. It
 * works once and for 24 hours, the same exposure the setup link of a self-service signup always
 * had.
 */
public final class SignupLinkToken {

  private static final SecureRandom RANDOM = new SecureRandom();

  private SignupLinkToken() {}

  /** A freshly issued token: {@code raw} for the mail, {@code hash} for the database. */
  public record Issued(String raw, String hash) {}

  public static Issued issue() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    return new Issued(raw, hash(raw));
  }

  public static String hash(String raw) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
