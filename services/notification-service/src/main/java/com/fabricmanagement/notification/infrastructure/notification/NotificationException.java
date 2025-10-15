package com.fabricmanagement.notification.infrastructure.notification;

/**
 * Notification Exception
 * 
 * Thrown when notification delivery fails.
 * Captures error details for logging and retry logic.
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
public class NotificationException extends Exception {
    
    private final String channel;
    private final String recipient;
    private final boolean retryable;
    
    public NotificationException(String message, String channel, String recipient, boolean retryable) {
        super(message);
        this.channel = channel;
        this.recipient = recipient;
        this.retryable = retryable;
    }
    
    public NotificationException(String message, Throwable cause, String channel, String recipient, boolean retryable) {
        super(message, cause);
        this.channel = channel;
        this.recipient = recipient;
        this.retryable = retryable;
    }
    
    public String getChannel() {
        return channel;
    }
    
    public String getRecipient() {
        return recipient;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
}

