package com.fabricmanagement.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Type-safe configuration properties for API Gateway
 * 
 * Replaces hardcoded values in YAML with structured, validated configuration.
 * 
 * @since 3.1.0 - Gateway Refactor (Oct 13, 2025)
 */
@Data
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {
    
    private Map<String, ServiceConfig> services = new HashMap<>();
    private RateLimitConfig rateLimit = new RateLimitConfig();
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();
    private RetryConfig retry = new RetryConfig();
    
    @Data
    public static class ServiceConfig {
        private String name;
        private String url;
        private String path;
        private boolean enabled = true;
        private Duration timeout = Duration.ofSeconds(15);
    }
    
    @Data
    public static class RateLimitConfig {
        private PublicEndpointsConfig publicEndpoints = new PublicEndpointsConfig();
        private ProtectedEndpointsConfig protectedEndpoints = new ProtectedEndpointsConfig();
        
        @Data
        public static class PublicEndpointsConfig {
            private int loginReplenishRate = 5;
            private int loginBurstCapacity = 10;
            private int onboardingReplenishRate = 5;
            private int onboardingBurstCapacity = 10;
            private int checkContactReplenishRate = 10;
            private int checkContactBurstCapacity = 15;
            private int setupPasswordReplenishRate = 3;
            private int setupPasswordBurstCapacity = 5;
            private int otherAuthReplenishRate = 20;
            private int otherAuthBurstCapacity = 30;
        }
        
        @Data
        public static class ProtectedEndpointsConfig {
            private int standardReplenishRate = 50;
            private int standardBurstCapacity = 100;
            private int internalEndpointReplenishRate = 5;
            private int internalEndpointBurstCapacity = 10;
        }
    }
    
    @Data
    public static class CircuitBreakerConfig {
        private int slidingWindowSize = 100;
        private int minimumNumberOfCalls = 10;
        private int permittedCallsInHalfOpen = 5;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);
        private int failureRateThreshold = 50;
        private int slowCallRateThreshold = 50;
        private Duration slowCallDurationThreshold = Duration.ofSeconds(8);
    }
    
    @Data
    public static class RetryConfig {
        private int maxAttempts = 3;
        private Duration initialBackoff = Duration.ofMillis(50);
        private Duration maxBackoff = Duration.ofMillis(500);
        private double multiplier = 2.0;
        
        // Specific retry configs for different route types
        private Duration publicRoutesInitialBackoff = Duration.ofMillis(100);
        private Duration protectedRoutesInitialBackoff = Duration.ofMillis(50);
    }
}

