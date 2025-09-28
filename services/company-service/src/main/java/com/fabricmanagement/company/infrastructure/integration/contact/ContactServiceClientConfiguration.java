package com.fabricmanagement.company.infrastructure.integration.contact;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for ContactServiceClient with basic resilience patterns.
 * Note: Resilience4j dependencies are not available, using basic Feign configuration.
 */
@Configuration
@Slf4j
public class ContactServiceClientConfiguration {

    @Bean
    public Logger.Level contactServiceLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Request.Options contactServiceRequestOptions() {
        return new Request.Options(
            10L, TimeUnit.SECONDS,  // Connect timeout
            30L, TimeUnit.SECONDS,  // Read timeout
            true                    // Follow redirects
        );
    }

    @Bean
    public Retryer contactServiceRetryer() {
        return new Retryer.Default(
            1000,   // Initial interval (1 second)
            3000,   // Max interval (3 seconds)
            3       // Max attempts
        );
    }

    @Bean
    public ErrorDecoder contactServiceErrorDecoder() {
        return new ContactServiceErrorDecoder();
    }

    private static class ContactServiceErrorDecoder implements ErrorDecoder {
        private final ErrorDecoder defaultErrorDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            log.warn("Contact service error - Method: {}, Status: {}, Reason: {}",
                methodKey, response.status(), response.reason());

            if (response.status() == 404) {
                return new ContactServiceNotFoundException("Contact not found");
            }
            if (response.status() >= 500) {
                return new ContactServiceUnavailableException("Contact service unavailable");
            }

            return defaultErrorDecoder.decode(methodKey, response);
        }
    }

    public static class ContactServiceNotFoundException extends RuntimeException {
        public ContactServiceNotFoundException(String message) {
            super(message);
        }
    }

    public static class ContactServiceUnavailableException extends RuntimeException {
        public ContactServiceUnavailableException(String message) {
            super(message);
        }
    }
}