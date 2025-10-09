package com.fabricmanagement.shared.infrastructure.policy.resolver;

import com.fabricmanagement.shared.domain.policy.PolicyContext;
import com.fabricmanagement.shared.domain.policy.UserPermission;
import com.fabricmanagement.shared.infrastructure.policy.constants.PolicyConstants;
import com.fabricmanagement.shared.infrastructure.policy.repository.UserPermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * User Grant Resolver
 * 
 * Resolves user-specific permission grants (from Advanced Settings).
 * Handles both ALLOW and DENY grants.
 * 
 * Grant Precedence (First DENY wins):
 * 1. User explicit DENY → Blocks access (cannot be overridden)
 * 2. Role default ALLOW → Standard access
 * 3. User explicit ALLOW → Additional access
 * 
 * Grant Types:
 * - DENY: Explicit denial (strongest)
 * - ALLOW: Explicit permission (weaker than role default)
 * 
 * Grant Properties:
 * - Endpoint-specific
 * - Operation-specific
 * - Time-bound (TTL)
 * - Audited (who granted, why, when)
 * 
 * Design Principles:
 * - Stateless (no instance variables)
 * - DENY takes precedence
 * - Expired grants ignored
 * - Cacheable results
 * 
 * Usage:
 * <pre>
 * String denyReason = userGrantResolver.checkUserDeny(context);
 * if (denyReason != null) {
 *     return PolicyDecision.deny(denyReason, ...);
 * }
 * 
 * boolean hasExplicitAllow = userGrantResolver.hasUserAllow(context);
 * if (hasExplicitAllow) {
 *     return PolicyDecision.allow("user_grant_explicit_allow", ...);
 * }
 * </pre>
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserGrantResolver {
    
    private static final String GRANT_PREFIX = PolicyConstants.REASON_USER_GRANT;
    
    private final UserPermissionRepository userPermissionRepository;
    
    /**
     * Check if user has explicit DENY grant
     * 
     * @param context policy context
     * @return denial reason if explicit deny exists, null otherwise
     */
    public String checkUserDeny(PolicyContext context) {
        try {
            List<UserPermission> denyGrants = userPermissionRepository.findDenyPermissions(
                context.getUserId(),
                context.getEndpoint(),
                context.getOperation(),
                LocalDateTime.now()
            );
            
            if (!denyGrants.isEmpty()) {
                UserPermission denyGrant = denyGrants.get(0);
                
                log.info("User {} has explicit DENY grant for endpoint: {}, operation: {}. Reason: {}",
                    context.getUserId(), context.getEndpoint(), context.getOperation(),
                    denyGrant.getReason());
                
                return GRANT_PREFIX + "_explicit_deny";
            }
            
            log.debug("No DENY grants found for user: {}, endpoint: {}",
                context.getUserId(), context.getEndpoint());
            
            return null;
            
        } catch (Exception e) {
            log.error("Error checking user DENY grants for user: {}, endpoint: {}",
                context.getUserId(), context.getEndpoint(), e);
            // Fail-safe: don't deny on error (let other checks decide)
            return null;
        }
    }
    
    /**
     * Check if user has explicit ALLOW grant
     * 
     * @param context policy context
     * @return true if explicit allow exists
     */
    public boolean hasUserAllow(PolicyContext context) {
        try {
            List<UserPermission> allowGrants = userPermissionRepository.findAllowPermissions(
                context.getUserId(),
                context.getEndpoint(),
                context.getOperation(),
                LocalDateTime.now()
            );
            
            if (!allowGrants.isEmpty()) {
                log.debug("User {} has explicit ALLOW grant for endpoint: {}, operation: {}",
                    context.getUserId(), context.getEndpoint(), context.getOperation());
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking user ALLOW grants for user: {}, endpoint: {}",
                context.getUserId(), context.getEndpoint(), e);
            // Fail-safe: don't allow on error
            return false;
        }
    }
    
    /**
     * Get all effective grants for user
     * (For debugging / Advanced Settings UI)
     * 
     * @param userId user ID (as UUID)
     * @return count of effective grants
     */
    public int getEffectiveGrantsCount(java.util.UUID userId) {
        try {
            List<UserPermission> grants = userPermissionRepository.findEffectivePermissionsForUser(
                userId,
                LocalDateTime.now()
            );
            
            return grants.size();
            
        } catch (Exception e) {
            log.error("Error getting effective grants for user: {}", userId, e);
            return 0;
        }
    }
    
    /**
     * Check if user needs explicit grant for endpoint
     * Note: This is now handled by PlatformPolicyGuard
     * 
     * @param context policy context
     * @return true if explicit grant required
     */
    public boolean requiresExplicitGrant(PolicyContext context) {
        // Sensitive operations require explicit grants (fallback logic)
        String endpoint = context.getEndpoint();
        if (endpoint != null) {
            if (endpoint.contains("/admin/") || 
                endpoint.contains("/permissions/") ||
                endpoint.contains("/grants/")) {
                return true;
            }
        }
        
        return false;
    }
}

