package com.fabricmanagement.platform.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Masked contact information for password reset.
 *
 * <p>Used to prevent enumeration attacks - only shows verified contacts in a masked format so users
 * can identify which contact to use without revealing full contact information.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>Email: "jo***@example.com"
 *   <li>Phone: "+90 *** *** 1234"
 * </ul>
 *
 * <p><b>Performance Optimization:</b>
 *
 * <p>Includes authUserId to enable direct lookup without masking operations, significantly
 * improving performance for password reset flow.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaskedContactInfo {

  /**
   * AuthUser ID - Used for direct lookup (performance optimization). Frontend sends this ID back
   * instead of masked value for password reset.
   */
  private java.util.UUID authUserId;

  /**
   * Masked contact value (email or phone). Email format: "jo***@example.com" Phone format: "+90 ***
   * *** 1234"
   */
  private String maskedValue;

  /** Contact type (EMAIL or PHONE) */
  private String type; // "EMAIL" or "PHONE"

  /** Whether this contact is verified. Only verified contacts are returned. */
  private Boolean verified;
}
