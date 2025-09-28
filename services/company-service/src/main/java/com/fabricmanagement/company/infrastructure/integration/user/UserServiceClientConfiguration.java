package com.fabricmanagement.company.infrastructure.integration.user;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for UserServiceClient with resilience patterns.
 */
@Configuration
@Slf4j
public class UserServiceClientConfiguration {

    @Bean
    public Logger.Level userServiceLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public Request.Options userServiceRequestOptions() {
        return new Request.Options(
            10L, TimeUnit.SECONDS,  // Connect timeout
            30L, TimeUnit.SECONDS,  // Read timeout
            true                    // Follow redirects
        );
    }

    @Bean
    public Retryer userServiceRetryer() {
        return new Retryer.Default(
            1000,   // Initial interval (1 second)
            3000,   // Max interval (3 seconds)
            3       // Max attempts
        );
    }

    @Bean
    public ErrorDecoder userServiceErrorDecoder() {
        return new UserServiceErrorDecoder();
    }

    private static class UserServiceErrorDecoder implements ErrorDecoder {
        private final ErrorDecoder defaultErrorDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            log.warn("User service error - Method: {}, Status: {}, Reason: {}",
                methodKey, response.status(), response.reason());

            if (response.status() == 404) {
                return new UserServiceNotFoundException("User not found");
            }
            if (response.status() >= 500) {
                return new UserServiceUnavailableException("User service unavailable");
            }

            return defaultErrorDecoder.decode(methodKey, response);
        }
    }

    public static class UserServiceNotFoundException extends RuntimeException {
        public UserServiceNotFoundException(String message) {
            super(message);
        }
    }

    public static class UserServiceUnavailableException extends RuntimeException {
        public UserServiceUnavailableException(String message) {
            super(message);
        }
    }
}