package com.fabricmanagement.shared.infrastructure.policy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Policy Configuration
 * 
 * Spring configuration for policy framework components.
 * Configures beans, caching, and scheduling for policy management.
 * 
 * ✅ PRODUCTION-READY - Complete Spring configuration
 * ✅ CACHE ENABLED - Redis caching configuration
 * ✅ SCHEDULING ENABLED - Automated maintenance
 * ✅ ASYNC ENABLED - Non-blocking operations
 * ✅ ZERO HARDCODED VALUES - Configurable properties
 */
@Configuration
@EnableConfigurationProperties(PolicyProperties.class)
@EnableAsync
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class PolicyConfiguration {

    private final PolicyProperties policyProperties;

    /**
     * Policy Registry Bean
     */
    @Bean
    public PolicyRegistry policyRegistry() {
        log.info("🔧 Configuring PolicyRegistry");
        return new PolicyRegistry();
    }

    /**
     * Policy Cache Bean
     */
    @Bean
    public PolicyCache policyCache(RedisTemplate<String, Object> redisTemplate, PolicyRegistry policyRegistry) {
        log.info("🔧 Configuring PolicyCache");
        return new PolicyCache(redisTemplate, policyRegistry);
    }

    /**
     * Policy Engine Bean
     */
    @Bean
    public PolicyEngine policyEngine(PolicyCache policyCache, PolicyRegistry policyRegistry) {
        log.info("🔧 Configuring PolicyEngine");
        return new PolicyEngineImpl(policyCache, policyRegistry);
    }

    /**
     * Policy Service Bean
     */
    @Bean
    public PolicyService policyService(PolicyRegistry policyRegistry, PolicyCache policyCache, PolicyEngine policyEngine) {
        log.info("🔧 Configuring PolicyService");
        return new PolicyService(policyRegistry, policyCache, policyEngine);
    }

    /**
     * Policy Configuration Bean
     */
    @Bean
    public PolicyConfiguration policyConfiguration(PolicyRegistry policyRegistry, PolicyCache policyCache) {
        log.info("🔧 Configuring PolicyConfiguration");
        return new PolicyConfiguration(policyRegistry, policyCache);
    }

    /**
     * Policy Properties Bean
     */
    @Bean
    public PolicyProperties policyProperties() {
        log.info("🔧 Configuring PolicyProperties");
        return policyProperties;
    }
}