package com.fabricmanagement.notification.infrastructure.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Platform Fallback Configuration
 * 
 * Default notification credentials used when tenant has no custom config.
 * All values are environment-driven (ZERO HARDCODED VALUES).
 * 
 * Usage:
 * 1. Check tenant config in database
 * 2. If NOT found → Use platform fallback
 * 3. Send notification with platform credentials
 * 
 * Platform Credentials:
 * - Email: info@storeandsale.shop (Gmail SMTP)
 * - WhatsApp: +447553838399
 * - SMS: +447553838399 (future)
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Slf4j
@Configuration
@Getter
public class PlatformFallbackConfig {
    
    // ========== Email Configuration ==========
    
    @Value("${notification.platform.email.from:info@storeandsale.shop}")
    private String platformEmailFrom;
    
    @Value("${notification.platform.email.fromName:Store and Sale}")
    private String platformEmailFromName;
    
    @Value("${spring.mail.host:smtp.gmail.com}")
    private String platformSmtpHost;
    
    @Value("${spring.mail.port:587}")
    private Integer platformSmtpPort;
    
    @Value("${spring.mail.username:info@storeandsale.shop}")
    private String platformSmtpUsername;
    
    @Value("${spring.mail.password:}")
    private String platformSmtpPassword;
    
    // ========== SMS Configuration ==========
    
    @Value("${notification.platform.sms.enabled:false}")
    private Boolean platformSmsEnabled;
    
    @Value("${notification.platform.sms.provider:}")
    private String platformSmsProvider;
    
    @Value("${notification.platform.sms.apiKey:}")
    private String platformSmsApiKey;
    
    @Value("${notification.platform.sms.fromNumber:+447553838399}")
    private String platformSmsFromNumber;
    
    // ========== WhatsApp Configuration ==========
    
    @Value("${notification.platform.whatsapp.enabled:true}")
    private Boolean platformWhatsappEnabled;
    
    @Value("${notification.platform.whatsapp.provider:META}")
    private String platformWhatsappProvider;
    
    @Value("${notification.platform.whatsapp.accessToken:}")
    private String platformWhatsappAccessToken;
    
    @Value("${notification.platform.whatsapp.phoneNumberId:}")
    private String platformWhatsappPhoneNumberId;
    
    @Value("${notification.platform.whatsapp.wabaId:}")
    private String platformWhatsappWabaId;
    
    @Value("${notification.platform.whatsapp.fromNumber:+447553838399}")
    private String platformWhatsappFromNumber;
    
    @Value("${notification.platform.whatsapp.testNumber:}")
    private String platformWhatsappTestNumber;
    
    // ========== Retry Configuration ==========
    
    @Value("${notification.retry.maxAttempts:3}")
    private Integer maxRetryAttempts;
    
    @Value("${notification.retry.backoffMs:1000}")
    private Long retryBackoffMs;
    
    // ========== Verification Configuration ==========
    
    @Value("${notification.verification.codeLength:6}")
    private Integer verificationCodeLength;
    
    @Value("${notification.verification.expirationMinutes:15}")
    private Integer verificationCodeExpirationMinutes;
    
    /**
     * Log configuration on startup
     */
    @PostConstruct
    public void logConfiguration() {
        log.info("📧 Platform Fallback Configuration loaded:");
        log.info("   Email: {} <{}>", platformEmailFromName, platformEmailFrom);
        log.info("   SMTP: {}:{} (username: {})", platformSmtpHost, platformSmtpPort, platformSmtpUsername);
        log.info("   SMS: {} (enabled: {})", platformSmsFromNumber, platformSmsEnabled);
        log.info("   WhatsApp: {} (enabled: {})", platformWhatsappFromNumber, platformWhatsappEnabled);
        log.info("   Retry: {} attempts, {} ms backoff", maxRetryAttempts, retryBackoffMs);
        log.info("   Verification: {} digits, {} min expiry", verificationCodeLength, verificationCodeExpirationMinutes);
        
        // Security warnings
        if (platformSmtpPassword == null || platformSmtpPassword.isEmpty()) {
            log.warn("⚠️ PLATFORM_SMTP_PASSWORD not set - email notifications will FAIL in production!");
        }
        
        if (!platformWhatsappEnabled && !platformSmsEnabled) {
            log.warn("⚠️ Both SMS and WhatsApp disabled - only email available!");
        }
    }
    
    /**
     * Check if platform email is configured
     */
    public boolean isEmailConfigured() {
        return platformSmtpPassword != null && !platformSmtpPassword.isEmpty();
    }
    
    /**
     * Check if platform WhatsApp is configured
     */
    public boolean isWhatsAppConfigured() {
        return platformWhatsappEnabled && 
               platformWhatsappAccessToken != null && 
               !platformWhatsappAccessToken.isEmpty() &&
               platformWhatsappPhoneNumberId != null &&
               !platformWhatsappPhoneNumberId.isEmpty();
    }
    
    /**
     * Check if platform SMS is configured
     */
    public boolean isSmsConfigured() {
        return platformSmsEnabled && 
               platformSmsApiKey != null && 
               !platformSmsApiKey.isEmpty();
    }
}

