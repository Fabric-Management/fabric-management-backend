package com.fabricmanagement.common.platform.user.domain;

/**
 * Contact type for user identification.
 *
 * <p>Users are identified by their contact value (email or phone), NOT by username.
 * This simplifies authentication and verification flows.</p>
 *
 * <h2>CRITICAL RULE:</h2>
 * <p>❌ NO separate username field!</p>
 * <p>✅ Use contactValue (email or phone) as the identifier</p>
 */
public enum ContactType {

    /**
     * Email address
     * <p>Format: user@example.com</p>
     * <p>Verification: Email with code</p>
     */
    EMAIL,

    /**
     * Phone number
     * <p>Format: E.164 (+905551234567)</p>
     * <p>Verification: WhatsApp → SMS</p>
     */
    PHONE
}

