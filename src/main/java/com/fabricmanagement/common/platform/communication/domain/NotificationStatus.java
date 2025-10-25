package com.fabricmanagement.common.platform.communication.domain;

/**
 * Notification delivery status.
 */
public enum NotificationStatus {

    /**
     * Waiting to be sent
     */
    PENDING,

    /**
     * Currently being sent
     */
    SENDING,

    /**
     * Successfully sent
     */
    SENT,

    /**
     * Failed to send
     */
    FAILED,

    /**
     * Retrying after failure
     */
    RETRYING
}

