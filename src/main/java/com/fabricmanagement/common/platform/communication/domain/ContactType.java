package com.fabricmanagement.common.platform.communication.domain;

/**
 * Contact type enumeration for all communication channels.
 *
 * <p>Supports modern communication channels including phone extensions, and traditional
 * email/phone. Used by both User and Company entities.
 *
 * <p><b>WhatsApp Support:</b> WhatsApp capability is indicated via {@code isWhatsApp} flag on
 * {@link #MOBILE} contacts, not as a separate contact type. This simplifies the model since
 * WhatsApp uses the same phone number as regular phone calls.
 */
public enum ContactType {

  /**
   * Email address
   *
   * <p>Format: user@example.com
   *
   * <p>Verification: Email with code
   *
   * <p>Used for: Authentication, notifications, business communication
   */
  EMAIL,

  /**
   * Mobile phone number
   *
   * <p>Format: E.164 (+905551234567)
   *
   * <p>Verification: WhatsApp → SMS
   *
   * <p>Used for: Authentication, notifications, voice calls
   */
  MOBILE,

  /**
   * Landline phone number
   *
   * <p>Format: Country/area specific (normalized to E.164 where possible)
   *
   * <p>Used for: Office phones, switchboards, fax-over-voice gateways
   */
  LANDLINE,

  /**
   * Phone extension (internal)
   *
   * <p>Format: Extension number (e.g., "101", "102")
   *
   * <p>Links to parent landline via parentContactId
   */
  PHONE_EXTENSION,

  /**
   * Fax number
   *
   * <p>Format: E.164 or local format
   *
   * <p>Used for: Business documents, official communications
   */
  FAX,

  /**
   * Website URL
   *
   * <p>Format: https://www.example.com
   *
   * <p>Used for: Company websites, user portfolios
   */
  WEBSITE,

  /**
   * Social media handle
   *
   * <p>Format: @username or platform-specific identifier
   *
   * <p>Used for: LinkedIn, Twitter, Instagram, etc.
   */
  SOCIAL_MEDIA;

  /**
   * @return true if this type represents a phone number (mobile or landline).
   */
  public boolean isPhone() {
    return this == MOBILE || this == LANDLINE;
  }

  /**
   * @return true if this type represents a mobile number.
   */
  public boolean isMobile() {
    return this == MOBILE;
  }

  /**
   * @return true if this type represents a landline number.
   */
  public boolean isLandline() {
    return this == LANDLINE;
  }
}
