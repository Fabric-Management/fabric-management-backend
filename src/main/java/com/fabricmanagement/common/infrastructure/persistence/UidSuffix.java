package com.fabricmanagement.common.infrastructure.persistence;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * The random tail of an entity UID: {@code {TENANT_UID}-{MODULE}-{SUFFIX}}.
 *
 * <p>Every place that builds such a UID (the JPA base entities, {@link UIDGenerator}, and the
 * native-SQL writers that bypass the entity callbacks) takes its suffix from here, so the length is
 * decided once.
 *
 * <p>The suffix is 12 hexadecimal characters, 48 random bits. Eight characters (32 bits) were not
 * enough: rows created without a tenant UID in the thread context share the {@code SYS-000} prefix,
 * so all of them draw from one namespace, and the {@code uid} column is unique across the table.
 * Around a hundred thousand such rows an 8-character suffix collides now and then, and a start-up
 * repair job once failed on exactly that. With 12 characters the same collision is about 65,000
 * times rarer. The longest UID this produces is a 50-character tenant UID, an 11-character module
 * code and the suffix, 75 characters in all, inside the 100-character {@code uid} columns. Older
 * rows keep their 8-character suffixes; nothing reads the suffix's length.
 */
public final class UidSuffix {

  /** Number of hexadecimal characters in a suffix. */
  public static final int LENGTH = 12;

  private static final SecureRandom RANDOM = new SecureRandom();

  private UidSuffix() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * A new random suffix.
   *
   * @return {@link #LENGTH} upper-case hexadecimal characters
   */
  public static String random() {
    byte[] bytes = new byte[(LENGTH + 1) / 2];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().withUpperCase().formatHex(bytes).substring(0, LENGTH);
  }
}
