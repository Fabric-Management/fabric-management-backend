package com.fabricmanagement.gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Modern, type-safe routing configuration replacing YAML-based routes.
 * 
 * Benefits over YAML:
 * ✅ Type-safe (compile-time errors)
 * ✅ Better IDE support (autocomplete, refactoring)
 * ✅ Conditional routing (environment-based)
 * ✅ Dynamic configuration from properties
 * ✅ Better readability and maintainability
 * 
 * Architecture:
 * - Public routes: Aggressive rate limiting (protect from abuse)
 * - Protected routes: Standard rate limiting
 * - All routes: Circuit breaker + retry + correlation ID + security headers
 * 
 * @since 3.1.0 - Gateway Refactor (Oct 13, 2025)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DynamicRoutesConfig {
    
    private final GatewayProperties gatewayProperties;
    private final SmartKeyResolver smartKeyResolver;
    
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        log.info("🚀 Configuring dynamic routes for API Gateway v3.1.0");
        
        return builder.routes()
                // =============================================================================
                // PUBLIC ROUTES - Aggressive Rate Limiting (Anti-abuse)
                // =============================================================================
                
                // Tenant Onboarding (Public)
                .route("public-tenant-onboarding", r -> r
                        .path("/api/v1/public/onboarding/**")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("userServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/user-service"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter(
                                                gatewayProperties.getRateLimit().getPublicEndpoints().getOnboardingReplenishRate(),
                                                gatewayProperties.getRateLimit().getPublicEndpoints().getOnboardingBurstCapacity()))
                                        .setKeyResolver(smartKeyResolver))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(1)
                                        .setMethods(HttpMethod.POST)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofMillis(500), 2, true)))
                        .uri("${USER_SERVICE_URL:http://localhost:8081}"))
                
                // User Login (Public - Very Strict)
                .route("public-user-login", r -> r
                        .path("/api/v1/users/auth/login")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("userServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/user-service"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter(
                                                gatewayProperties.getRateLimit().getPublicEndpoints().getLoginReplenishRate(),
                                                gatewayProperties.getRateLimit().getPublicEndpoints().getLoginBurstCapacity()))
                                        .setKeyResolver(smartKeyResolver))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(1)
                                        .setMethods(HttpMethod.POST)
                                        .setBackoff(
                                                gatewayProperties.getRetry().getPublicRoutesInitialBackoff(), 
                                                gatewayProperties.getRetry().getMaxBackoff(), 
                                                2, true)))
                        .uri("${USER_SERVICE_URL:http://localhost:8081}"))
                
                // Check Contact (Public - Moderate)
                .route("public-check-contact", r -> r
                        .path("/api/v1/users/auth/check-contact")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("userServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/user-service"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter(
                                                gatewayProperties.getRateLimit().getPublicEndpoints().getCheckContactReplenishRate(),
                                                gatewayProperties.getRateLimit().getPublicEndpoints().getCheckContactBurstCapacity()))
                                        .setKeyResolver(smartKeyResolver))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(2)
                                        .setMethods(HttpMethod.POST)
                                        .setBackoff(
                                                gatewayProperties.getRetry().getProtectedRoutesInitialBackoff(), 
                                                gatewayProperties.getRetry().getMaxBackoff(), 
                                                2, true)))
                        .uri("${USER_SERVICE_URL:http://localhost:8081}"))
                
                // Setup Password (Public - Very Strict)
                .route("public-setup-password", r -> r
                        .path("/api/v1/users/auth/setup-password")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("userServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/user-service"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter(
                                                gatewayProperties.getRateLimit().getPublicEndpoints().getSetupPasswordReplenishRate(),
                                                gatewayProperties.getRateLimit().getPublicEndpoints().getSetupPasswordBurstCapacity()))
                                        .setKeyResolver(smartKeyResolver))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(1)
                                        .setMethods(HttpMethod.POST)
                                        .setBackoff(
                                                gatewayProperties.getRetry().getPublicRoutesInitialBackoff(), 
                                                gatewayProperties.getRetry().getMaxBackoff(), 
                                                2, true)))
                        .uri("${USER_SERVICE_URL:http://localhost:8081}"))
                
                // Other Auth Routes (Public)
                .route("public-auth-routes", r -> r
                        .path("/api/v1/users/auth/**")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("userServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/user-service"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter(
                                                gatewayProperties.getRateLimit().getPublicEndpoints().getOtherAuthReplenishRate(),
                                                gatewayProperties.getRateLimit().getPublicEndpoints().getOtherAuthBurstCapacity()))
                                        .setKeyResolver(smartKeyResolver))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setMethods(HttpMethod.GET, HttpMethod.POST)
                                        .setBackoff(
                                                gatewayProperties.getRetry().getProtectedRoutesInitialBackoff(), 
                                                gatewayProperties.getRetry().getMaxBackoff(), 
                                                2, true)))
                        .uri("${USER_SERVICE_URL:http://localhost:8081}"))
                
                // =============================================================================
                // PROTECTED ROUTES - Standard Rate Limiting
                // =============================================================================
                
                // User Service (Protected)
                .route("user-service-protected", r -> r
                        .path("/api/v1/users/**")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("userServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/user-service"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter(
                                                gatewayProperties.getRateLimit().getProtectedEndpoints().getStandardReplenishRate(),
                                                gatewayProperties.getRateLimit().getProtectedEndpoints().getStandardBurstCapacity()))
                                        .setKeyResolver(smartKeyResolver))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setMethods(HttpMethod.GET)
                                        .setBackoff(
                                                gatewayProperties.getRetry().getProtectedRoutesInitialBackoff(), 
                                                gatewayProperties.getRetry().getMaxBackoff(), 
                                                2, true)))
                        .uri("${USER_SERVICE_URL:http://localhost:8081}"))
                
                // Company Service (Protected)
                .route("company-service-protected", r -> r
                        .path("/api/v1/companies/**")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("companyServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/company-service"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter(
                                                gatewayProperties.getRateLimit().getProtectedEndpoints().getStandardReplenishRate(),
                                                gatewayProperties.getRateLimit().getProtectedEndpoints().getStandardBurstCapacity()))
                                        .setKeyResolver(smartKeyResolver))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setMethods(HttpMethod.GET)
                                        .setBackoff(
                                                gatewayProperties.getRetry().getProtectedRoutesInitialBackoff(), 
                                                gatewayProperties.getRetry().getMaxBackoff(), 
                                                2, true)))
                        .uri("${COMPANY_SERVICE_URL:http://localhost:8083}"))
                
                // Contact Service - Find by Value (Internal/Protected - Strict)
                .route("contact-service-find-by-value", r -> r
                        .path("/api/v1/contacts/find-by-value")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("contactServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/contact-service"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter(
                                                gatewayProperties.getRateLimit().getProtectedEndpoints().getInternalEndpointReplenishRate(),
                                                gatewayProperties.getRateLimit().getProtectedEndpoints().getInternalEndpointBurstCapacity()))
                                        .setKeyResolver(smartKeyResolver))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(2)
                                        .setMethods(HttpMethod.GET)
                                        .setBackoff(
                                                gatewayProperties.getRetry().getProtectedRoutesInitialBackoff(), 
                                                gatewayProperties.getRetry().getMaxBackoff(), 
                                                2, true)))
                        .uri("${CONTACT_SERVICE_URL:http://localhost:8082}"))
                
                // Contact Service (Protected)
                .route("contact-service-protected", r -> r
                        .path("/api/v1/contacts/**")
                        .filters(f -> f
                                .circuitBreaker(c -> c
                                        .setName("contactServiceCircuitBreaker")
                                        .setFallbackUri("forward:/fallback/contact-service"))
                                .requestRateLimiter(rl -> rl
                                        .setRateLimiter(redisRateLimiter(
                                                gatewayProperties.getRateLimit().getProtectedEndpoints().getStandardReplenishRate(),
                                                gatewayProperties.getRateLimit().getProtectedEndpoints().getStandardBurstCapacity()))
                                        .setKeyResolver(smartKeyResolver))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setMethods(HttpMethod.GET)
                                        .setBackoff(
                                                gatewayProperties.getRetry().getProtectedRoutesInitialBackoff(), 
                                                gatewayProperties.getRetry().getMaxBackoff(), 
                                                2, true)))
                        .uri("${CONTACT_SERVICE_URL:http://localhost:8082}"))
                
                .build();
    }
    
    /**
     * Creates a Redis-based rate limiter with specified rates.
     * 
     * @param replenishRate Number of requests per second to allow
     * @param burstCapacity Maximum burst size (bucket capacity)
     * @return Configured RedisRateLimiter
     */
    private RedisRateLimiter redisRateLimiter(int replenishRate, int burstCapacity) {
        return new RedisRateLimiter(replenishRate, burstCapacity, 1);
    }
}

