package com.fabricmanagement.common.platform.communication.domain;

/**
 * Contact type enumeration for all communication channels.
 *
 * <p>Supports modern communication channels including WhatsApp, phone extensions,
 * and traditional email/phone. Used by both User and Company entities.</p>
 *
 * <h2>Usage:</h2>
 * <ul>
 *   <li><b>EMAIL:</b> Email address (e.g., "john.doe@acme.com")</li>
 *   <li><b>PHONE:</b> Phone number in E.164 format (e.g., "+905551234567")</li>
 *   <li><b>PHONE_EXTENSION:</b> Extension number linked to parent phone (e.g., "101", "102")</li>
 *   <li><b>FAX:</b> Fax number</li>
 *   <li><b>WEBSITE:</b> Website URL</li>
 *   <li><b>WHATSAPP:</b> WhatsApp Business number (can be same as PHONE or separate)</li>
 *   <li><b>SOCIAL_MEDIA:</b> Social media handle (Twitter, LinkedIn, etc.)</li>
 * </ul>
 *
 * <h2>Special Cases:</h2>
 * <ul>
 *   <li><b>PHONE_EXTENSION:</b> Must have parentContactId pointing to a PHONE contact</li>
 *   <li><b>WHATSAPP:</b> Used for verification and notifications (Priority 1 channel)</li>
 * </ul>
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
     * Phone number (mobile or landline)
     * <p>Format: E.164 (+905551234567)</p>
     * <p>Verification: WhatsApp → SMS</p>
     * <p>Used for: Authentication, notifications, voice calls</p>
     */
    PHONE,

    /**
     * Phone extension (internal)
     * <p>Format: Extension number (e.g., "101", "102")</p>
     * <p>Links to parent phone via parentContactId</p>
     * <p>Used for: Company phone extensions (e.g., "+90-212-123-4567 ext. 101")</p>
     * <p><b>CRITICAL:</b> parentContactId must reference a PHONE contact</p>
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
     * WhatsApp Business number
     * <p>Format: E.164 (+905551234567)</p>
     * <p>Verification: WhatsApp Business API</p>
     * <p>Used for: Priority 1 verification channel, instant notifications</p>
     * <p><b>NOTE:</b> Can be same as PHONE or a separate number</p>
     */
    WHATSAPP,

    /**
     * Social media handle
     * <p>Format: @username or platform-specific identifier</p>
     * <p>Used for: LinkedIn, Twitter, Instagram, etc.</p>
     */
    SOCIAL_MEDIA
}

