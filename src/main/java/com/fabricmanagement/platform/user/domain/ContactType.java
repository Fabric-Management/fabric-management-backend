package com.fabricmanagement.platform.user.domain;

/**
 * Simplified contact type for user creation API.
 *
 * <p>This is an <b>API-facing</b> enum used in user creation DTOs. It provides a simplified view
 * (EMAIL/PHONE) that the frontend uses. The system maps this to the full {@link
 * com.fabricmanagement.platform.communication.domain.ContactType} enum internally:
 *
 * <ul>
 *   <li>{@code EMAIL} → {@code ContactType.EMAIL}
 *   <li>{@code PHONE} → {@code ContactType.MOBILE} (default) or {@code ContactType.LANDLINE} (via
 *       phoneType field in {@link com.fabricmanagement.platform.user.dto.ContactData})
 * </ul>
 *
 * <p><b>Note:</b> For domain-level operations, use {@link
 * com.fabricmanagement.platform.communication.domain.ContactType} directly.
 */
public enum ContactType {
  /** Email address. Maps to Communication module's ContactType.EMAIL. */
  EMAIL,

  /**
   * Phone number. Maps to Communication module's ContactType.MOBILE by default, or LANDLINE when
   * phoneType="LANDLINE" is specified.
   */
  PHONE
}
