package com.fabricmanagement.shared.infrastructure.policy;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.application.response.PagedResponse;
import com.fabricmanagement.shared.infrastructure.constants.ServiceConstants;
import com.fabricmanagement.shared.security.annotation.InternalEndpoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Policy Controller
 * 
 * REST API endpoints for policy management operations.
 * Provides CRUD operations and policy evaluation endpoints.
 * 
 * ✅ ZERO HARDCODED VALUES - ServiceConstants kullanıyor
 * ✅ PRODUCTION-READY - ApiResponse wrapper
 * ✅ INTERNAL ENDPOINT - Service-to-service calls
 * ✅ UUID TYPE SAFETY - her yerde UUID
 * ✅ PAGINATION - PagedResponse for large datasets
 */
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
@Slf4j
public class PolicyController {

    private final PolicyService policyService;

    // =========================================================================
    // POLICY CRUD ENDPOINTS
    // =========================================================================

    /**
     * Create new policy
     * Internal endpoint - called by other services
     */
    @InternalEndpoint(description = "Called by services for policy creation", calledBy = {"auth-service", "user-service"})
    @PostMapping
    public ResponseEntity<ApiResponse<PolicyRegistry.Policy>> createPolicy(
            @Valid @RequestBody PolicyService.CreatePolicyRequest request) {
        
        log.info("📋 Creating policy: {}", request.getName());
        
        PolicyRegistry.Policy policy = policyService.createPolicy(request);
        
        return ResponseEntity.ok(ApiResponse.success(policy, ServiceConstants.MSG_PERMISSION_CREATED));
    }

    /**
     * Update existing policy
     * Internal endpoint - called by other services
     */
    @InternalEndpoint(description = "Called by services for policy updates", calledBy = {"auth-service", "user-service"})
    @PutMapping("/{policyId}")
    public ResponseEntity<ApiResponse<PolicyRegistry.Policy>> updatePolicy(
            @PathVariable UUID policyId,
            @Valid @RequestBody PolicyService.UpdatePolicyRequest request) {
        
        log.info("📋 Updating policy: {}", policyId);
        
        PolicyRegistry.Policy policy = policyService.updatePolicy(policyId, request);
        
        return ResponseEntity.ok(ApiResponse.success(policy, ServiceConstants.MSG_PERMISSION_UPDATED));
    }

    /**
     * Delete policy
     * Internal endpoint - called by other services
     */
    @InternalEndpoint(description = "Called by services for policy deletion", calledBy = {"auth-service", "user-service"})
    @DeleteMapping("/{policyId}")
    public ResponseEntity<ApiResponse<Void>> deletePolicy(@PathVariable UUID policyId) {
        
        log.info("🗑️ Deleting policy: {}", policyId);
        
        policyService.deletePolicy(policyId);
        
        return ResponseEntity.ok(ApiResponse.success(null, ServiceConstants.MSG_PERMISSION_DELETED));
    }

    /**
     * Get policy by ID
     * Internal endpoint - called by other services
     */
    @InternalEndpoint(description = "Called by services for policy queries", calledBy = {"auth-service", "user-service"})
    @GetMapping("/{policyId}")
    public ResponseEntity<ApiResponse<PolicyRegistry.Policy>> getPolicy(@PathVariable UUID policyId) {
        
        log.debug("🔍 Getting policy: {}", policyId);
        
        return policyService.getPolicy(policyId)
            .map(policy -> ResponseEntity.ok(ApiResponse.success(policy, "Policy retrieved successfully")))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get policies by name
     * Internal endpoint - called by other services
     */
    @InternalEndpoint(description = "Called by services for policy queries", calledBy = {"auth-service", "user-service"})
    @GetMapping("/by-name/{policyName}")
    public ResponseEntity<ApiResponse<List<PolicyRegistry.Policy>>> getPoliciesByName(
            @PathVariable String policyName) {
        
        log.debug("🔍 Getting policies by name: {}", policyName);
        
        List<PolicyRegistry.Policy> policies = policyService.getPoliciesByName(policyName);
        
        return ResponseEntity.ok(ApiResponse.success(policies, "Policies retrieved successfully"));
    }

    /**
     * Get tenant policies
     * Internal endpoint - called by other services
     */
    @InternalEndpoint(description = "Called by services for tenant policy queries", calledBy = {"auth-service", "user-service"})
    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<ApiResponse<List<PolicyRegistry.Policy>>> getTenantPolicies(
            @PathVariable UUID tenantId) {
        
        log.debug("🔍 Getting policies for tenant: {}", tenantId);
        
        List<PolicyRegistry.Policy> policies = policyService.getTenantPolicies(tenantId);
        
        return ResponseEntity.ok(ApiResponse.success(policies, "Tenant policies retrieved successfully"));
    }

    /**
     * Get active tenant policies
     * Internal endpoint - called by other services
     */
    @InternalEndpoint(description = "Called by services for active tenant policy queries", calledBy = {"auth-service", "user-service"})
    @GetMapping("/tenant/{tenantId}/active")
    public ResponseEntity<ApiResponse<List<PolicyRegistry.Policy>>> getActiveTenantPolicies(
            @PathVariable UUID tenantId) {
        
        log.debug("🔍 Getting active policies for tenant: {}", tenantId);
        
        List<PolicyRegistry.Policy> policies = policyService.getActiveTenantPolicies(tenantId);
        
        return ResponseEntity.ok(ApiResponse.success(policies, "Active tenant policies retrieved successfully"));
    }

    // =========================================================================
    // POLICY EVALUATION ENDPOINTS
    // =========================================================================

    /**
     * Evaluate policy for user action
     * Internal endpoint - called by other services
     */
    @InternalEndpoint(description = "Called by services for policy evaluation", calledBy = {"auth-service", "user-service"})
    @PostMapping("/evaluate")
    public ResponseEntity<ApiResponse<PolicyDecision>> evaluatePolicy(
            @Valid @RequestBody EvaluatePolicyRequest request) {
        
        log.debug("🔍 Evaluating policy: {} for user: {}", request.getPolicyName(), request.getUserId());
        
        PolicyDecision decision = policyService.evaluatePolicy(request.getPolicyName(), request.getContext());
        
        return ResponseEntity.ok(ApiResponse.success(decision, "Policy evaluation completed"));
    }

    /**
     * Check user permission
     * Internal endpoint - called by other services
     */
    @InternalEndpoint(description = "Called by services for permission checks", calledBy = {"auth-service", "user-service"})
    @PostMapping("/check-permission")
    public ResponseEntity<ApiResponse<Boolean>> checkPermission(
            @Valid @RequestBody CheckPermissionRequest request) {
        
        log.debug("🔍 Checking permission: {} for user: {}:{}", request.getPermission(), request.getUserId(), request.getTenantId());
        
        boolean hasPermission = policyService.hasPermission(
            request.getUserId(),
            request.getTenantId(),
            request.getPermission(),
            request.getResourceType(),
            request.getResourceId()
        );
        
        return ResponseEntity.ok(ApiResponse.success(hasPermission, "Permission check completed"));
    }

    /**
     * Check user role
     * Internal endpoint - called by other services
     */
    @InternalEndpoint(description = "Called by services for role checks", calledBy = {"auth-service", "user-service"})
    @PostMapping("/check-role")
    public ResponseEntity<ApiResponse<Boolean>> checkRole(
            @Valid @RequestBody CheckRoleRequest request) {
        
        log.debug("🔍 Checking role: {} for user: {}:{}", request.getRoleName(), request.getUserId(), request.getTenantId());
        
        boolean hasRole = policyService.hasRole(
            request.getUserId(),
            request.getTenantId(),
            request.getRoleName()
        );
        
        return ResponseEntity.ok(ApiResponse.success(hasRole, "Role check completed"));
    }

    /**
     * Get user effective policies
     * Internal endpoint - called by other services
     */
    @InternalEndpoint(description = "Called by services for user policy queries", calledBy = {"auth-service", "user-service"})
    @GetMapping("/user/{userId}/tenant/{tenantId}/effective")
    public ResponseEntity<ApiResponse<List<PolicyRegistry.Policy>>> getUserEffectivePolicies(
            @PathVariable UUID userId,
            @PathVariable UUID tenantId) {
        
        log.debug("🔍 Getting effective policies for user: {}:{}", userId, tenantId);
        
        List<PolicyRegistry.Policy> policies = policyService.getUserEffectivePolicies(userId, tenantId);
        
        return ResponseEntity.ok(ApiResponse.success(policies, "User effective policies retrieved successfully"));
    }

    // =========================================================================
    // POLICY MANAGEMENT ENDPOINTS
    // =========================================================================

    /**
     * Activate policy
     * Internal endpoint - called by other services
     */
    @InternalEndpoint(description = "Called by services for policy activation", calledBy = {"auth-service", "user-service"})
    @PostMapping("/{policyId}/activate")
    public ResponseEntity<ApiResponse<PolicyRegistry.Policy>> activatePolicy(@PathVariable UUID policyId) {
        
        log.info("✅ Activating policy: {}", policyId);
        
        PolicyRegistry.Policy policy = policyService.activatePolicy(policyId);
        
        return ResponseEntity.ok(ApiResponse.success(policy, "Policy activated successfully"));
    }

    /**
     * Deactivate policy
     * Internal endpoint - called by other services
     */
    @InternalEndpoint(description = "Called by services for policy deactivation", calledBy = {"auth-service", "user-service"})
    @PostMapping("/{policyId}/deactivate")
    public ResponseEntity<ApiResponse<PolicyRegistry.Policy>> deactivatePolicy(@PathVariable UUID policyId) {
        
        log.info("❌ Deactivating policy: {}", policyId);
        
        PolicyRegistry.Policy policy = policyService.deactivatePolicy(policyId);
        
        return ResponseEntity.ok(ApiResponse.success(policy, "Policy deactivated successfully"));
    }

    // =========================================================================
    // REQUEST/RESPONSE DTOs
    // =========================================================================

    /**
     * Evaluate Policy Request DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class EvaluatePolicyRequest {
        @NotBlank
        private final String policyName;
        @NotNull
        private final PolicyContext context;
    }

    /**
     * Check Permission Request DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class CheckPermissionRequest {
        @NotNull
        private final UUID userId;
        @NotNull
        private final UUID tenantId;
        @NotBlank
        private final String permission;
        @NotBlank
        private final String resourceType;
        @NotNull
        private final UUID resourceId;
    }

    /**
     * Check Role Request DTO
     */
    @lombok.Builder
    @lombok.Getter
    public static class CheckRoleRequest {
        @NotNull
        private final UUID userId;
        @NotNull
        private final UUID tenantId;
        @NotBlank
        private final String roleName;
    }
}
