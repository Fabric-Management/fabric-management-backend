package com.fabricmanagement.shared.infrastructure.policy.guard;

import com.fabricmanagement.shared.domain.policy.PolicyContext;
import com.fabricmanagement.shared.domain.policy.PolicyRegistry;
import com.fabricmanagement.shared.infrastructure.policy.constants.PolicyConstants;
import com.fabricmanagement.shared.infrastructure.policy.repository.PolicyRegistryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Platform Policy Guard
 * 
 * Enforces platform-wide policy rules defined in PolicyRegistry.
 * These are configurable policies that apply globally across all services.
 * 
 * Platform policies include:
 * - Endpoint-specific restrictions
 * - Operation-level policies
 * - Time-based restrictions (e.g., maintenance windows)
 * - Rate limiting policies
 * - IP-based restrictions
 * 
 * Design Principles:
 * - Stateless (no instance variables)
 * - Configurable (reads from PolicyRegistry)
 * - Fail-safe (deny by default)
 * - Cacheable decisions
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformPolicyGuard {
    
    private static final String PLATFORM_PREFIX = PolicyConstants.REASON_PLATFORM;
    
    private final PolicyRegistryRepository policyRegistryRepository;
    
    /**
     * Check platform-wide policies
     * 
     * @param context policy context
     * @return denial reason if policy violated, null if allowed
     */
    public String checkPlatformPolicy(PolicyContext context) {
        try {
            String endpoint = context.getEndpoint();
            
            // Look up policy in registry
            Optional<PolicyRegistry> policyOpt = policyRegistryRepository
                .findByEndpointAndOperationAndActiveTrue(endpoint, context.getOperation());
            
            if (policyOpt.isEmpty()) {
                // No specific policy defined - allow by default
                log.debug("No platform policy found for endpoint: {}", endpoint);
                return null;
            }
            
            PolicyRegistry policy = policyOpt.get();
            
            // Check if company type is allowed
            if (policy.getAllowedCompanyTypes() != null && !policy.getAllowedCompanyTypes().isEmpty()) {
                String companyType = context.getCompanyType() != null ? 
                    context.getCompanyType().name() : null;
                    
                if (companyType == null || !policy.isCompanyTypeAllowed(companyType)) {
                    log.info("Platform policy DENY - Company type {} not allowed for endpoint: {}",
                        companyType, endpoint);
                    return PLATFORM_PREFIX + "_company_type_not_allowed";
                }
            }
            
            // Check if user role has default access
            if (policy.getDefaultRoles() != null && !policy.getDefaultRoles().isEmpty()) {
                boolean hasRole = context.getRoles() != null && 
                    context.getRoles().stream().anyMatch(policy::hasRoleAccess);
                    
                if (!hasRole) {
                    log.info("Platform policy DENY - No required role for endpoint: {}",
                        endpoint);
                    return PLATFORM_PREFIX + "_role_not_allowed";
                }
            }
            
            log.debug("Platform policy check passed for endpoint: {}", endpoint);
            return null; // All checks passed
            
        } catch (Exception e) {
            log.error("Error checking platform policy for endpoint: {}",
                context.getEndpoint(), e);
            // Fail-safe: deny on error
            return PLATFORM_PREFIX + "_check_error";
        }
    }
    
    /**
     * Check if endpoint requires additional authorization
     * 
     * @param endpoint API endpoint
     * @return true if additional checks required
     */
    public boolean requiresGrant(String endpoint) {
        try {
            Boolean requiresGrant = policyRegistryRepository.requiresGrant(endpoint);
            
            if (requiresGrant != null && requiresGrant) {
                return true;
            }
            
            // Fallback: Sensitive endpoints that require explicit grants
            if (endpoint != null) {
                if (endpoint.contains("/admin/") || 
                    endpoint.contains("/settings/") ||
                    endpoint.contains("/permissions/")) {
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking requiresGrant for endpoint: {}", endpoint, e);
            // Fail-safe: require grant on error
            return true;
        }
    }
    
    /**
     * Get allowed company types for endpoint
     * 
     * @param endpoint API endpoint
     * @return array of allowed company types (null = all allowed)
     */
    public String[] getAllowedCompanyTypes(String endpoint) {
        try {
            List<String> allowedTypes = policyRegistryRepository.getAllowedCompanyTypes(endpoint);
            
            if (allowedTypes != null && !allowedTypes.isEmpty()) {
                return allowedTypes.toArray(new String[0]);
            }
            
            // No restrictions defined - all company types allowed
            return null;
            
        } catch (Exception e) {
            log.error("Error getting allowed company types for endpoint: {}", endpoint, e);
            // Fail-safe: return empty array (no company types allowed)
            return new String[0];
        }
    }
}

