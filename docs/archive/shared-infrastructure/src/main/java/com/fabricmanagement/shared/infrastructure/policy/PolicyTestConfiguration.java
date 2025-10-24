package com.fabricmanagement.shared.infrastructure.policy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Policy Test Configuration
 * 
 * Test configuration for policy framework components.
 * Provides test-specific beans and configurations for unit and integration tests.
 * 
 * ✅ TEST-READY - Complete test configuration
 * ✅ MOCK SUPPORT - Test-specific mocks
 * ✅ ISOLATION - Test isolation
 * ✅ PERFORMANCE - Fast test execution
 */
@TestConfiguration
@TestPropertySource(properties = {
    "policy.cache.enabled=false",
    "policy.evaluation.enabled=true",
    "policy.maintenance.enabled=false",
    "policy.security.enabled=true"
})
@RequiredArgsConstructor
@Slf4j
public class PolicyTestConfiguration {

    /**
     * Test Policy Registry Bean
     */
    @Bean
    @Primary
    public PolicyRegistry testPolicyRegistry() {
        log.info("🧪 Configuring test PolicyRegistry");
        return new PolicyRegistry();
    }

    /**
     * Test Policy Cache Bean
     */
    @Bean
    @Primary
    public PolicyCache testPolicyCache(RedisTemplate<String, Object> redisTemplate, PolicyRegistry policyRegistry) {
        log.info("🧪 Configuring test PolicyCache");
        return new PolicyCache(redisTemplate, policyRegistry);
    }

    /**
     * Test Policy Engine Bean
     */
    @Bean
    @Primary
    public PolicyEngine testPolicyEngine(PolicyCache policyCache, PolicyRegistry policyRegistry) {
        log.info("🧪 Configuring test PolicyEngine");
        return new PolicyEngineImpl(policyCache, policyRegistry);
    }

    /**
     * Test Policy Service Bean
     */
    @Bean
    @Primary
    public PolicyService testPolicyService(PolicyRegistry policyRegistry, PolicyCache policyCache, PolicyEngine policyEngine) {
        log.info("🧪 Configuring test PolicyService");
        return new PolicyService(policyRegistry, policyCache, policyEngine);
    }

    /**
     * Test Policy Configuration Bean
     */
    @Bean
    @Primary
    public PolicyConfiguration testPolicyConfiguration(PolicyRegistry policyRegistry, PolicyCache policyCache) {
        log.info("🧪 Configuring test PolicyConfiguration");
        return new PolicyConfiguration(policyRegistry, policyCache);
    }

    /**
     * Test Policy Properties Bean
     */
    @Bean
    @Primary
    public PolicyProperties testPolicyProperties() {
        log.info("🧪 Configuring test PolicyProperties");
        PolicyProperties properties = new PolicyProperties();
        
        // Test-specific configuration
        properties.getCache().setEnabled(false);
        properties.getEvaluation().setEnabled(true);
        properties.getMaintenance().setEnabled(false);
        properties.getSecurity().setEnabled(true);
        
        return properties;
    }
}
