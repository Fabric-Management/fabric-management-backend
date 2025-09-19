package com.fabricmanagement.contact.infrastructure.integration.user;

import feign.Logger;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for UserServiceClient Feign client.
 * Provides timeout, retry, and error handling configuration.
 */
@Configuration
public class UserServiceClientConfig {

    @Value("${services.user.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${services.user.read-timeout:10000}")
    private int readTimeout;

    @Value("${services.user.max-attempts:3}")
    private int maxAttempts;

    @Value("${services.user.retry-period:1000}")
    private long retryPeriod;

    @Value("${services.user.max-retry-period:3000}")
    private long maxRetryPeriod;

    /**
     * Configures request options for the Feign client.
     */
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
            connectTimeout, TimeUnit.MILLISECONDS,
            readTimeout, TimeUnit.MILLISECONDS,
            true
        );
    }

    /**
     * Configures retry mechanism for the Feign client.
     */
    @Bean
    public Retryer retryer() {
        return new Retryer.Default(retryPeriod, maxRetryPeriod, maxAttempts);
    }

    /**
     * Configures logging level for the Feign client.
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * Custom error decoder for handling user-service errors.
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return new UserServiceErrorDecoder();
    }

    /**
     * Custom error decoder implementation for user-service.
     */
    public static class UserServiceErrorDecoder implements ErrorDecoder {

        private final ErrorDecoder defaultErrorDecoder = new Default();

        @Override
        public Exception decode(String methodKey, feign.Response response) {
            switch (response.status()) {
                case 404:
                    return new UserNotFoundException("User not found");
                case 403:
                    return new UserAccessDeniedException("Access denied to user resource");
                case 500:
                case 502:
                case 503:
                case 504:
                    return new UserServiceUnavailableException("User service is temporarily unavailable");
                default:
                    return defaultErrorDecoder.decode(methodKey, response);
            }
        }
    }

    /**
     * Exception thrown when a user is not found.
     */
    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when access to user resource is denied.
     */
    public static class UserAccessDeniedException extends RuntimeException {
        public UserAccessDeniedException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when user service is unavailable.
     */
    public static class UserServiceUnavailableException extends RuntimeException {
        public UserServiceUnavailableException(String message) {
            super(message);
        }
    }
}