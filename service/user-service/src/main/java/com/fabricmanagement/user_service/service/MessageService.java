package com.fabricmanagement.user_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Service for resolving internationalized messages
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageSource messageSource;

    /**
     * Get message using current locale
     */
    public String getMessage(String key) {
        return getMessage(key, null, LocaleContextHolder.getLocale());
    }

    /**
     * Get message with parameters using current locale
     */
    public String getMessage(String key, Object... args) {
        return getMessage(key, args, LocaleContextHolder.getLocale());
    }

    /**
     * Get message with specific locale
     */
    public String getMessage(String key, Locale locale) {
        return getMessage(key, null, locale);
    }

    /**
     * Get message with parameters and specific locale
     */
    public String getMessage(String key, Object[] args, Locale locale) {
        return messageSource.getMessage(key, args, key, locale);
    }

    /**
     * Format message with parameters
     */
    public String format(String key, Object... args) {
        return getMessage(key, args);
    }

    /**
     * Check if message exists
     */
    public boolean hasMessage(String key) {
        try {
            getMessage(key);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get message or default value
     */
    public String getMessageOrDefault(String key, String defaultValue) {
        try {
            return getMessage(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}