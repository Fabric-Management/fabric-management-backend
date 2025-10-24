package com.fabricmanagement.gateway.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.time.Duration;

/**
 * Dynamic Routes Configuration for API Gateway
 * 
 * Production-ready routing with optional rate limiting.
 * Rate limiting: Set GATEWAY_RATE_LIMIT_ENABLED=true in production
 */
@Slf4j
@Configuration
public class DynamicRoutesConfig {
    
    private final GatewayProperties gatewayProperties;
    private final SmartKeyResolver smartKeyResolver;
    
    @Autowired(required = false)
    private RedisRateLimiter redisRateLimiter;
    
    @Value("${USER_SERVICE_URL:http://localhost:8081}")
    private String userServiceUrl;
    
    @Value("${CONTACT_SERVICE_URL:http://localhost:8082}")
    private String contactServiceUrl;
    
    @Value("${COMPANY_SERVICE_URL:http://localhost:8083}")
    private String companyServiceUrl;
    
    @Value("${FIBER_SERVICE_URL:http://localhost:8094}")
    private String fiberServiceUrl;
    
    @Value("${NOTIFICATION_SERVICE_URL:http://localhost:8084}")
    private String notificationServiceUrl;
    
    @Value("${GATEWAY_RATE_LIMIT_ENABLED:false}")
    private boolean rateLimitEnabled;
    
    public DynamicRoutesConfig(
            @Qualifier("fabricGatewayProperties") GatewayProperties gatewayProperties,
            SmartKeyResolver smartKeyResolver) {
        this.gatewayProperties = gatewayProperties;
        this.smartKeyResolver = smartKeyResolver;
    }
    
    @PostConstruct
    public void init() {
        log.info("🔍 DynamicRoutesConfig initialized:");
        log.info("   USER_SERVICE_URL = {}", userServiceUrl);
        log.info("   COMPANY_SERVICE_URL = {}", companyServiceUrl);
        log.info("   CONTACT_SERVICE_URL = {}", contactServiceUrl);
        log.info("   FIBER_SERVICE_URL = {}", fiberServiceUrl);
        log.info("   NOTIFICATION_SERVICE_URL = {}", notificationServiceUrl);
        log.info("   Rate Limiting: {}", rateLimitEnabled ? "✅ ENABLED" : "⚠️ DISABLED (dev mode)");
    }
    
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        log.info("🚀 Configuring dynamic routes (Rate limiting: {})", 
            rateLimitEnabled ? "ON" : "OFF");
        
        return builder.routes()
                // Tenant Onboarding
                .route("public-tenant-onboarding", r -> r
                        .path("/api/v1/public/onboarding/**")
                        .filters(f -> {
                            var filter = f.circuitBreaker(c -> c
                                    .setName("userServiceCircuitBreaker")
                                    .setFallbackUri("forward:/fallback/user-service"))
                                .retry(retryConfig -> retryConfig
                                    .setRetries(1)
                                    .setMethods(HttpMethod.POST)
                                    .setBackoff(Duration.ofMillis(100), Duration.ofMillis(500), 2, true));
                            
                            // Apply rate limiting only if enabled
                            if (rateLimitEnabled && redisRateLimiter != null) {
                                filter = filter.requestRateLimiter(rl -> rl
                                    .setRateLimiter(redisRateLimiter)
                                    .setKeyResolver(smartKeyResolver));
                            }
                            return filter;
                        })
                        .uri(userServiceUrl))
                
                // Login
                .route("public-user-login", r -> r
                        .path("/api/v1/users/auth/login")
                        .filters(f -> {
                            var filter = f.circuitBreaker(c -> c
                                    .setName("userServiceCircuitBreaker")
                                    .setFallbackUri("forward:/fallback/user-service"))
                                .retry(retryConfig -> retryConfig
                                    .setRetries(1)
                                    .setMethods(HttpMethod.POST)
                                    .setBackoff(gatewayProperties.getRetry().getPublicRoutesInitialBackoff(), 
                                            gatewayProperties.getRetry().getMaxBackoff(), 2, true));
                            
                            if (rateLimitEnabled && redisRateLimiter != null) {
                                filter = filter.requestRateLimiter(rl -> rl
                                    .setRateLimiter(redisRateLimiter)
                                    .setKeyResolver(smartKeyResolver));
                            }
                            return filter;
                        })
                        .uri(userServiceUrl))
                
                // Check Contact
                .route("public-check-contact", r -> r
                        .path("/api/v1/users/auth/check-contact")
                        .filters(f -> {
                            var filter = f.circuitBreaker(c -> c
                                    .setName("userServiceCircuitBreaker")
                                    .setFallbackUri("forward:/fallback/user-service"))
                                .retry(retryConfig -> retryConfig
                                    .setRetries(2)
                                    .setMethods(HttpMethod.POST)
                                    .setBackoff(gatewayProperties.getRetry().getProtectedRoutesInitialBackoff(), 
                                            gatewayProperties.getRetry().getMaxBackoff(), 2, true));
                            
                            if (rateLimitEnabled && redisRateLimiter != null) {
                                filter = filter.requestRateLimiter(rl -> rl
                                    .setRateLimiter(redisRateLimiter)
                                    .setKeyResolver(smartKeyResolver));
                            }
                            return filter;
                        })
                        .uri(userServiceUrl))
                
                // Setup Password
                .route("public-setup-password", r -> r
                        .path("/api/v1/users/auth/setup-password")
                        .filters(f -> {
                            var filter = f.circuitBreaker(c -> c
                                    .setName("userServiceCircuitBreaker")
                                    .setFallbackUri("forward:/fallback/user-service"))
                                .retry(retryConfig -> retryConfig
                                    .setRetries(1)
                                    .setMethods(HttpMethod.POST)
                                    .setBackoff(gatewayProperties.getRetry().getPublicRoutesInitialBackoff(), 
                                            gatewayProperties.getRetry().getMaxBackoff(), 2, true));
                            
                            if (rateLimitEnabled && redisRateLimiter != null) {
                                filter = filter.requestRateLimiter(rl -> rl
                                    .setRateLimiter(redisRateLimiter)
                                    .setKeyResolver(smartKeyResolver));
                            }
                            return filter;
                        })
                        .uri(userServiceUrl))
                
                // Other Auth
                .route("public-auth-routes", r -> r
                        .path("/api/v1/users/auth/**")
                        .filters(f -> {
                            var filter = f.circuitBreaker(c -> c
                                    .setName("userServiceCircuitBreaker")
                                    .setFallbackUri("forward:/fallback/user-service"))
                                .retry(retryConfig -> retryConfig
                                    .setRetries(3)
                                    .setMethods(HttpMethod.GET, HttpMethod.POST)
                                    .setBackoff(gatewayProperties.getRetry().getProtectedRoutesInitialBackoff(), 
                                            gatewayProperties.getRetry().getMaxBackoff(), 2, true));
                            
                            if (rateLimitEnabled && redisRateLimiter != null) {
                                filter = filter.requestRateLimiter(rl -> rl
                                    .setRateLimiter(redisRateLimiter)
                                    .setKeyResolver(smartKeyResolver));
                            }
                            return filter;
                        })
                        .uri(userServiceUrl))
                
                // User Service
                .route("user-service-protected", r -> r
                        .path("/api/v1/users/**")
                        .filters(f -> {
                            var filter = f.circuitBreaker(c -> c
                                    .setName("userServiceCircuitBreaker")
                                    .setFallbackUri("forward:/fallback/user-service"))
                                .retry(retryConfig -> retryConfig
                                    .setRetries(3)
                                    .setMethods(HttpMethod.GET)
                                    .setBackoff(gatewayProperties.getRetry().getProtectedRoutesInitialBackoff(), 
                                            gatewayProperties.getRetry().getMaxBackoff(), 2, true));
                            
                            if (rateLimitEnabled && redisRateLimiter != null) {
                                filter = filter.requestRateLimiter(rl -> rl
                                    .setRateLimiter(redisRateLimiter)
                                    .setKeyResolver(smartKeyResolver));
                            }
                            return filter;
                        })
                        .uri(userServiceUrl))
                
                // Company Service
                .route("company-service-protected", r -> r
                        .path("/api/v1/companies/**")
                        .filters(f -> {
                            var filter = f.circuitBreaker(c -> c
                                    .setName("companyServiceCircuitBreaker")
                                    .setFallbackUri("forward:/fallback/company-service"))
                                .retry(retryConfig -> retryConfig
                                    .setRetries(3)
                                    .setMethods(HttpMethod.GET)
                                    .setBackoff(gatewayProperties.getRetry().getProtectedRoutesInitialBackoff(), 
                                            gatewayProperties.getRetry().getMaxBackoff(), 2, true));
                            
                            if (rateLimitEnabled && redisRateLimiter != null) {
                                filter = filter.requestRateLimiter(rl -> rl
                                    .setRateLimiter(redisRateLimiter)
                                    .setKeyResolver(smartKeyResolver));
                            }
                            return filter;
                        })
                        .uri(companyServiceUrl))
                
                // Contact Service
                .route("contact-service-protected", r -> r
                        .path("/api/v1/contacts/**")
                        .filters(f -> {
                            var filter = f.circuitBreaker(c -> c
                                    .setName("contactServiceCircuitBreaker")
                                    .setFallbackUri("forward:/fallback/contact-service"))
                                .retry(retryConfig -> retryConfig
                                    .setRetries(3)
                                    .setMethods(HttpMethod.GET)
                                    .setBackoff(gatewayProperties.getRetry().getProtectedRoutesInitialBackoff(), 
                                            gatewayProperties.getRetry().getMaxBackoff(), 2, true));
                            
                            if (rateLimitEnabled && redisRateLimiter != null) {
                                filter = filter.requestRateLimiter(rl -> rl
                                    .setRateLimiter(redisRateLimiter)
                                    .setKeyResolver(smartKeyResolver));
                            }
                            return filter;
                        })
                        .uri(contactServiceUrl))
                
                // Fiber Service
                .route("fiber-service-protected", r -> r
                        .path("/api/v1/fibers/**")
                        .filters(f -> {
                            var filter = f.circuitBreaker(c -> c
                                    .setName("fiberServiceCircuitBreaker")
                                    .setFallbackUri("forward:/fallback/fiber-service"))
                                .retry(retryConfig -> retryConfig
                                    .setRetries(3)
                                    .setMethods(HttpMethod.GET)
                                    .setBackoff(gatewayProperties.getRetry().getProtectedRoutesInitialBackoff(), 
                                            gatewayProperties.getRetry().getMaxBackoff(), 2, true));
                            
                            if (rateLimitEnabled && redisRateLimiter != null) {
                                filter = filter.requestRateLimiter(rl -> rl
                                    .setRateLimiter(redisRateLimiter)
                                    .setKeyResolver(smartKeyResolver));
                            }
                            return filter;
                        })
                        .uri(fiberServiceUrl))
                
                // Notification Service
                .route("notification-service-protected", r -> r
                        .path("/api/v1/notifications/**")
                        .filters(f -> {
                            var filter = f.circuitBreaker(c -> c
                                    .setName("notificationServiceCircuitBreaker")
                                    .setFallbackUri("forward:/fallback/notification-service"))
                                .retry(retryConfig -> retryConfig
                                    .setRetries(3)
                                    .setMethods(HttpMethod.GET)
                                    .setBackoff(gatewayProperties.getRetry().getProtectedRoutesInitialBackoff(), 
                                            gatewayProperties.getRetry().getMaxBackoff(), 2, true));
                            
                            if (rateLimitEnabled && redisRateLimiter != null) {
                                filter = filter.requestRateLimiter(rl -> rl
                                    .setRateLimiter(redisRateLimiter)
                                    .setKeyResolver(smartKeyResolver));
                            }
                            return filter;
                        })
                        .uri(notificationServiceUrl))
                
                .build();
    }
}
