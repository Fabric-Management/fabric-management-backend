package com.fabricmanagement.notification.domain.aggregate;

import com.fabricmanagement.notification.domain.valueobject.NotificationChannel;
import com.fabricmanagement.notification.domain.valueobject.NotificationProvider;
import com.fabricmanagement.shared.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Notification Config Aggregate
 * 
 * Tenant-specific notification channel configuration.
 * Stores SMTP/SMS/WhatsApp credentials securely.
 * 
 * Fallback Pattern:
 * - If tenant has config → use tenant credentials
 * - If NOT → use platform default (info@storeandsale.shop)
 * 
 * Security:
 * - Passwords and API keys stored encrypted (future: Jasypt/KMS)
 * - Only accessible by tenant owner or SUPER_ADMIN
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Entity
@Table(
    name = "notification_configs",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_tenant_channel_active",
            columnNames = {"tenant_id", "channel", "deleted_at"}
        )
    },
    indexes = {
        @Index(name = "idx_tenant_channel", columnList = "tenant_id, channel"),
        @Index(name = "idx_tenant_enabled", columnList = "tenant_id, is_enabled")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@ToString(callSuper = true)
public class NotificationConfig extends BaseEntity {
    
    /**
     * Tenant ID owning this configuration
     */
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    
    /**
     * Notification channel (EMAIL, SMS, WHATSAPP)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 50)
    private NotificationChannel channel;
    
    /**
     * Provider implementation (SMTP, GMAIL, TWILIO, etc.)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private NotificationProvider provider;
    
    /**
     * Configuration enabled/disabled
     */
    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = true;
    
    // ========== EMAIL (SMTP) Configuration ==========
    
    /**
     * SMTP host (e.g. smtp.gmail.com)
     */
    @Column(name = "smtp_host", length = 255)
    private String smtpHost;
    
    /**
     * SMTP port (e.g. 587 for TLS)
     */
    @Column(name = "smtp_port")
    private Integer smtpPort;
    
    /**
     * SMTP username (usually email address)
     */
    @Column(name = "smtp_username", length = 255)
    private String smtpUsername;
    
    /**
     * SMTP password (encrypted in production)
     */
    @Column(name = "smtp_password", length = 500)
    private String smtpPassword;
    
    /**
     * From email address
     */
    @Column(name = "from_email", length = 255)
    private String fromEmail;
    
    /**
     * From name (display name)
     */
    @Column(name = "from_name", length = 255)
    private String fromName;
    
    // ========== SMS/WhatsApp Configuration ==========
    
    /**
     * API Key for SMS/WhatsApp provider (encrypted)
     */
    @Column(name = "api_key", length = 500)
    private String apiKey;
    
    /**
     * From phone number (E.164 format: +905551234567)
     */
    @Column(name = "from_number", length = 50)
    private String fromNumber;
    
    // ========== Priority & Metadata ==========
    
    /**
     * Channel priority (0=highest, used for fallback order)
     * Default: WhatsApp=0, Email=1, SMS=2
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 1;
    
    // createdBy and updatedBy inherited from BaseEntity (String type)
}

