package com.fabricmanagement.common.platform.communication.domain;

/**
 * Notification channel types.
 *
 * <p>Priority order: WHATSAPP (1) → EMAIL (2) → SMS (3)</p>
 */
public enum NotificationChannel {

    /**
     * WhatsApp Business API - Priority 1
     * <p>Fastest delivery, highest open rate, cost-effective</p>
     */
    WHATSAPP,

    /**
     * Email - Priority 2
     * <p>Universal support, professional, no extra cost</p>
     */
    EMAIL,

    /**
     * SMS/AWS SNS - Priority 3
     * <p>Fallback option, reliable delivery, cost per message</p>
     */
    SMS
}

