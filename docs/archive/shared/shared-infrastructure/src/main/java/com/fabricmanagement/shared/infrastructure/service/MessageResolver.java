package com.fabricmanagement.shared.infrastructure.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Message Resolver
 * 
 * Resolves i18n messages using MessageSource
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ MESSAGE RESOLVER
 * ✅ i18n SUPPORT
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageResolver {
    
    private final MessageSource messageSource;
    
    /**
     * Resolve message by key
     */
    public String resolve(String key) {
        try {
            Locale locale = LocaleContextHolder.getLocale();
            return messageSource.getMessage(key, null, locale);
        } catch (Exception e) {
            log.warn("❌ Failed to resolve message key: {}", key, e);
            return key; // Return key as fallback
        }
    }
    
    /**
     * Resolve message by key with arguments
     */
    public String resolve(String key, Object... args) {
        try {
            Locale locale = LocaleContextHolder.getLocale();
            return messageSource.getMessage(key, args, locale);
        } catch (Exception e) {
            log.warn("❌ Failed to resolve message key: {} with args: {}", key, args, e);
            return key; // Return key as fallback
        }
    }
    
    /**
     * Resolve message by key with default message
     */
    public String resolve(String key, String defaultMessage) {
        try {
            Locale locale = LocaleContextHolder.getLocale();
            return messageSource.getMessage(key, null, defaultMessage, locale);
        } catch (Exception e) {
            log.warn("❌ Failed to resolve message key: {} with default: {}", key, defaultMessage, e);
            return defaultMessage;
        }
    }
    
    /**
     * Resolve message by key with arguments and default message
     */
    public String resolve(String key, String defaultMessage, Object... args) {
        try {
            Locale locale = LocaleContextHolder.getLocale();
            return messageSource.getMessage(key, args, defaultMessage, locale);
        } catch (Exception e) {
            log.warn("❌ Failed to resolve message key: {} with default: {} and args: {}", 
                    key, defaultMessage, args, e);
            return defaultMessage;
        }
    }
    
    /**
     * Resolve message by key for specific locale
     */
    public String resolve(String key, Locale locale) {
        try {
            return messageSource.getMessage(key, null, locale);
        } catch (Exception e) {
            log.warn("❌ Failed to resolve message key: {} for locale: {}", key, locale, e);
            return key; // Return key as fallback
        }
    }
    
    /**
     * Resolve message by key with arguments for specific locale
     */
    public String resolve(String key, Locale locale, Object... args) {
        try {
            return messageSource.getMessage(key, args, locale);
        } catch (Exception e) {
            log.warn("❌ Failed to resolve message key: {} for locale: {} with args: {}", 
                    key, locale, args, e);
            return key; // Return key as fallback
        }
    }
    
    /**
     * Check if message key exists
     */
    public boolean exists(String key) {
        try {
            Locale locale = LocaleContextHolder.getLocale();
            String message = messageSource.getMessage(key, null, locale);
            return !message.equals(key); // If resolved message equals key, it doesn't exist
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if message key exists for specific locale
     */
    public boolean exists(String key, Locale locale) {
        try {
            String message = messageSource.getMessage(key, null, locale);
            return !message.equals(key); // If resolved message equals key, it doesn't exist
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get current locale
     */
    public Locale getCurrentLocale() {
        return LocaleContextHolder.getLocale();
    }
    
    /**
     * Set locale
     */
    public void setLocale(Locale locale) {
        LocaleContextHolder.setLocale(locale);
    }
}
