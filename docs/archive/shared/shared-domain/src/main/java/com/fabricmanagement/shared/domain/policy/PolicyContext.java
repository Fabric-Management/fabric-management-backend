package com.fabricmanagement.shared.domain.policy;

import com.fabricmanagement.shared.domain.valueobject.OperationType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Policy Context
 * 
 * Context information for policy evaluation
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ POLICY FRAMEWORK
 * ✅ UUID TYPE SAFETY
 */
@Getter
@Setter
@Builder
@ToString
public class PolicyContext {
    
    // =========================================================================
    // USER CONTEXT
    // =========================================================================
    private UUID userId;
    private UUID tenantId;
    private String userRole;
    private String userScope;
    private Map<String, Object> userAttributes;
    
    // =========================================================================
    // RESOURCE CONTEXT
    // =========================================================================
    private UUID resourceId;
    private String resourceType;
    private String resourceScope;
    private Map<String, Object> resourceAttributes;
    
    // =========================================================================
    // OPERATION CONTEXT
    // =========================================================================
    private OperationType operation;
    private String operationName;
    private Map<String, Object> operationAttributes;
    
    // =========================================================================
    // ENVIRONMENT CONTEXT
    // =========================================================================
    private String ipAddress;
    private String userAgent;
    private LocalDateTime timestamp;
    private String traceId;
    private String correlationId;
    
    // =========================================================================
    // POLICY CONTEXT
    // =========================================================================
    private String policyName;
    private String policyVersion;
    private Map<String, Object> policyParameters;
    
    // =========================================================================
    // UTILITY METHODS
    // =========================================================================
    
    /**
     * Check if user has specific role
     */
    public boolean hasRole(String role) {
        return role != null && role.equals(this.userRole);
    }
    
    /**
     * Check if user has any of the specified roles
     */
    public boolean hasAnyRole(String... roles) {
        if (roles == null || userRole == null) return false;
        for (String role : roles) {
            if (role != null && role.equals(this.userRole)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if user has specific scope
     */
    public boolean hasScope(String scope) {
        return scope != null && scope.equals(this.userScope);
    }
    
    /**
     * Check if operation is specific type
     */
    public boolean isOperation(OperationType operation) {
        return operation != null && operation.equals(this.operation);
    }
    
    /**
     * Check if resource is specific type
     */
    public boolean isResourceType(String resourceType) {
        return resourceType != null && resourceType.equals(this.resourceType);
    }
    
    /**
     * Get user attribute
     */
    public Object getUserAttribute(String key) {
        return userAttributes != null ? userAttributes.get(key) : null;
    }
    
    /**
     * Get resource attribute
     */
    public Object getResourceAttribute(String key) {
        return resourceAttributes != null ? resourceAttributes.get(key) : null;
    }
    
    /**
     * Get operation attribute
     */
    public Object getOperationAttribute(String key) {
        return operationAttributes != null ? operationAttributes.get(key) : null;
    }
    
    /**
     * Get policy parameter
     */
    public Object getPolicyParameter(String key) {
        return policyParameters != null ? policyParameters.get(key) : null;
    }
    
    /**
     * Check if context is valid
     */
    public boolean isValid() {
        return userId != null && tenantId != null && operation != null;
    }
    
    // =========================================================================
    // ADDITIONAL METHODS FOR POLICY FRAMEWORK
    // =========================================================================
    
    /**
     * Get effective scope (user scope or resource scope)
     */
    public String getEffectiveScope() {
        return resourceScope != null ? resourceScope : userScope;
    }
    
    /**
     * Get resource owner ID
     */
    public UUID getResourceOwnerId() {
        return (UUID) getResourceAttribute("ownerId");
    }
    
    /**
     * Get company ID from user attributes
     */
    public UUID getCompanyId() {
        return (UUID) getUserAttribute("companyId");
    }
    
    /**
     * Get resource company ID
     */
    public UUID getResourceCompanyId() {
        return (UUID) getResourceAttribute("companyId");
    }
    
    /**
     * Get company type from user attributes
     */
    public String getCompanyType() {
        return (String) getUserAttribute("companyType");
    }
    
    /**
     * Get endpoint from operation attributes
     */
    public String getEndpoint() {
        return (String) getOperationAttribute("endpoint");
    }
    
    /**
     * Get HTTP method from operation attributes
     */
    public String getHttpMethod() {
        return (String) getOperationAttribute("httpMethod");
    }
    
    /**
     * Get scope (alias for userScope)
     */
    public String getScope() {
        return userScope;
    }
    
    /**
     * Get request IP address
     */
    public String getRequestIp() {
        return ipAddress;
    }
    
    /**
     * Get request ID
     */
    public String getRequestId() {
        return correlationId;
    }
    
    /**
     * Check if this is an internal request
     */
    public boolean isInternal() {
        return Boolean.TRUE.equals(getOperationAttribute("internal"));
    }
    
    /**
     * Get roles as list
     */
    public String getRoles() {
        return userRole;
    }
    
    /**
     * Create builder with common fields
     */
    public static PolicyContextBuilder builder(UUID userId, UUID tenantId, OperationType operation) {
        return PolicyContext.builder()
            .userId(userId)
            .tenantId(tenantId)
            .operation(operation)
            .timestamp(LocalDateTime.now());
    }
}