package com.fabricmanagement.common.platform.communication.domain;

/**
 * Contact type enumeration for all communication channels.
 *
 * <p>Supports modern communication channels including phone extensions,
 * and traditional email/phone. Used by both User and Company entities.</p>
 *
 * <p><b>WhatsApp Support:</b> WhatsApp capability is indicated via {@code isWhatsApp} flag
 * on {@link #MOBILE} contacts, not as a separate contact type. This simplifies the model since
 * WhatsApp uses the same phone number as regular phone calls.</p>
 */
public enum ContactType {

    /**
     * Email address
     * <p>Format: user@example.com</p>
     * <p>Verification: Email with code</p>
     * <p>Used for: Authentication, notifications, business communication</p>
     */
    EMAIL,

    /**
     * Mobile phone number
     * <p>Format: E.164 (+905551234567)</p>
     * <p>Verification: WhatsApp → SMS</p>
     * <p>Used for: Authentication, notifications, voice calls</p>
     */
    MOBILE,

    /**
     * Landline phone number
     * <p>Format: Country/area specific (normalized to E.164 where possible)</p>
     * <p>Used for: Office phones, switchboards, fax-over-voice gateways</p>
     */
    LANDLINE,

    /**
     * Phone extension (internal)
     * <p>Format: Extension number (e.g., "101", "102")</p>
     * <p>Links to parent landline via parentContactId</p>
     */
    PHONE_EXTENSION,

    /**
     * Fax number
     * <p>Format: E.164 or local format</p>
     * <p>Used for: Business documents, official communications</p>
     */
    FAX,

    /**
     * Website URL
     * <p>Format: https://www.example.com</p>
     * <p>Used for: Company websites, user portfolios</p>
     */
    WEBSITE,

    /**
     * Social media handle
     * <p>Format: @username or platform-specific identifier</p>
     * <p>Used for: LinkedIn, Twitter, Instagram, etc.</p>
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

