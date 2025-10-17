package com.fabricmanagement.shared.infrastructure.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Message Resolver Service
 * 
 * Centralized message resolution with i18n support.
 * 
 * Usage:
 * <pre>
 * String message = messageResolver.getMessage(AuthMessageKeys.EMAIL_NOT_REGISTERED);
 * String message = messageResolver.getMessage(AuthMessageKeys.ACCOUNT_LOCKED, "15"); // with parameter
 * </pre>
 * 
 * Benefits:
 * - i18n support (EN/TR automatic based on Accept-Language header)
 * - No hardcoded messages
 * - Easy to change messages (just edit .properties)
 * - Type-safe message keys
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageResolver {
    
    private final MessageSource messageSource;
    
    /**
     * Get message with default locale
     */
    public String getMessage(String key) {
        return getMessage(key, (Object[]) null);
    }
    
    /**
     * Get message with parameters
     */
    public String getMessage(String key, Object... params) {
        return getMessage(key, LocaleContextHolder.getLocale(), params);
    }
    
    /**
     * Get message with specific locale
     */
    public String getMessage(String key, Locale locale) {
        return getMessage(key, locale, (Object[]) null);
    }
    
    /**
     * Get message with specific locale and parameters
     */
    public String getMessage(String key, Locale locale, Object... params) {
        try {
            return messageSource.getMessage(key, params, locale);
        } catch (Exception e) {
            log.warn("Message key not found: {} for locale: {}. Returning key as fallback.", key, locale);
            return key; // Fallback to key itself
        }
    }
    
}

