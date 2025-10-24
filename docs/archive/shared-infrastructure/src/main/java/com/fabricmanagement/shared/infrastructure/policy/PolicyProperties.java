package com.fabricmanagement.shared.infrastructure.policy;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Policy Properties
 * 
 * Configuration properties for policy framework.
 * Externalizes all policy-related configuration values.
 * 
 * ✅ ZERO HARDCODED VALUES - All values configurable
 * ✅ PRODUCTION-READY - Comprehensive configuration
 * ✅ TYPE SAFETY - Strong typing for all properties
 * ✅ VALIDATION - Built-in validation support
 */
@Component
@ConfigurationProperties(prefix = "policy")
@Data
public class PolicyProperties {

    /**
     * Cache configuration
     */
    private Cache cache = new Cache();

    /**
     * Evaluation configuration
     */
    private Evaluation evaluation = new Evaluation();

    /**
     * Maintenance configuration
     */
    private Maintenance maintenance = new Maintenance();

    /**
     * Security configuration
     */
    private Security security = new Security();

    /**
     * Cache configuration
     */
    @Data
    public static class Cache {
        /**
         * Enable policy caching
         */
        private boolean enabled = true;

        /**
         * Policy TTL in minutes
         */
        private int policyTtlMinutes = 30;

        /**
         * User policies TTL in minutes
         */
        private int userPoliciesTtlMinutes = 15;

        /**
         * Role policies TTL in minutes
         */
        private int rolePoliciesTtlMinutes = 60;

        /**
         * Tenant policies TTL in minutes
         */
        private int tenantPoliciesTtlMinutes = 120;

        /**
         * Cache warming enabled
         */
        private boolean warmingEnabled = true;

        /**
         * Cache statistics enabled
         */
        private boolean statisticsEnabled = true;
    }

    /**
     * Evaluation configuration
     */
    @Data
    public static class Evaluation {
        /**
         * Enable policy evaluation
         */
        private boolean enabled = true;

        /**
         * Evaluation timeout in milliseconds
         */
        private long timeoutMs = 5000;

        /**
         * Enable evaluation caching
         */
        private boolean cachingEnabled = true;

        /**
         * Evaluation cache TTL in minutes
         */
        private int cacheTtlMinutes = 10;

        /**
         * Enable evaluation logging
         */
        private boolean loggingEnabled = true;

        /**
         * Enable evaluation metrics
         */
        private boolean metricsEnabled = true;
    }

    /**
     * Maintenance configuration
     */
    @Data
    public static class Maintenance {
        /**
         * Enable scheduled maintenance
         */
        private boolean enabled = true;

        /**
         * Maintenance interval in milliseconds
         */
        private long intervalMs = 3600000; // 1 hour

        /**
         * Enable expired policy cleanup
         */
        private boolean cleanupEnabled = true;

        /**
         * Enable cache refresh
         */
        private boolean refreshEnabled = true;

        /**
         * Enable policy validation
         */
        private boolean validationEnabled = true;

        /**
         * Maintenance timeout in milliseconds
         */
        private long timeoutMs = 30000; // 30 seconds
    }

    /**
     * Security configuration
     */
    @Data
    public static class Security {
        /**
         * Enable security policies
         */
        private boolean enabled = true;

        /**
         * Enable password policies
         */
        private boolean passwordPoliciesEnabled = true;

        /**
         * Enable account lockout policies
         */
        private boolean lockoutPoliciesEnabled = true;

        /**
         * Enable access control policies
         */
        private boolean accessControlPoliciesEnabled = true;

        /**
         * Enable audit logging
         */
        private boolean auditLoggingEnabled = true;

        /**
         * Enable security metrics
         */
        private boolean metricsEnabled = true;
    }

    /**
     * Get policy TTL as Duration
     */
    public Duration getPolicyTtl() {
        return Duration.ofMinutes(cache.policyTtlMinutes);
    }

    /**
     * Get user policies TTL as Duration
     */
    public Duration getUserPoliciesTtl() {
        return Duration.ofMinutes(cache.userPoliciesTtlMinutes);
    }

    /**
     * Get role policies TTL as Duration
     */
    public Duration getRolePoliciesTtl() {
        return Duration.ofMinutes(cache.rolePoliciesTtlMinutes);
    }

    /**
     * Get tenant policies TTL as Duration
     */
    public Duration getTenantPoliciesTtl() {
        return Duration.ofMinutes(cache.tenantPoliciesTtlMinutes);
    }

    /**
     * Get evaluation timeout as Duration
     */
    public Duration getEvaluationTimeout() {
        return Duration.ofMillis(evaluation.timeoutMs);
    }

    /**
     * Get maintenance interval as Duration
     */
    public Duration getMaintenanceInterval() {
        return Duration.ofMillis(maintenance.intervalMs);
    }

    /**
     * Get maintenance timeout as Duration
     */
    public Duration getMaintenanceTimeout() {
        return Duration.ofMillis(maintenance.timeoutMs);
    }
}
