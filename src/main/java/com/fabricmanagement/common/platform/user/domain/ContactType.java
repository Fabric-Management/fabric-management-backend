package com.fabricmanagement.common.platform.user.domain;

/**
 * Contact type for user identification.
 *
 * <p>Users are identified by their contact value (email or phone), NOT by username. This simplifies
 * authentication and verification flows.
 *
 * <h2>CRITICAL RULE:</h2>
 *
 * <p>❌ NO separate username field!
 *
 * <p>✅ Use contactValue (email or phone) as the identifier
 */
public enum ContactType {

  /**
   * Email address
   *
   * <p>Format: user@example.com
   *
   * <p>Verification: Email with code
   */
  EMAIL,

  /**
   * Phone number
   *
   * <p>Format: E.164 (+905551234567)
   *
   * <p>Verification: WhatsApp → SMS
   */
  PHONE
}
