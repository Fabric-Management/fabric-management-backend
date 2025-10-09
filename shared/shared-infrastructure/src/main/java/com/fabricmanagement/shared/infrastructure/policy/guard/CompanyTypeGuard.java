package com.fabricmanagement.shared.infrastructure.policy.guard;

import com.fabricmanagement.shared.domain.policy.CompanyType;
import com.fabricmanagement.shared.domain.policy.OperationType;
import com.fabricmanagement.shared.domain.policy.PolicyContext;
import com.fabricmanagement.shared.infrastructure.policy.constants.PolicyConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Company Type Guard
 * 
 * Enforces company type-based guardrails.
 * These are platform-wide security rules that cannot be overridden.
 * 
 * Rules:
 * - INTERNAL: Full access (READ, WRITE, DELETE, APPROVE, EXPORT, MANAGE)
 * - CUSTOMER: Read-only (READ, EXPORT only)
 * - SUPPLIER: Read + limited write (READ, WRITE for purchase orders)
 * - SUBCONTRACTOR: Read + limited write (READ, WRITE for production orders)
 * 
 * Design Principles:
 * - Stateless (no instance variables)
 * - Fail-safe (deny by default)
 * - First DENY wins
 * - Explainable reasons
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization)
 */
@Slf4j
@Component
public class CompanyTypeGuard {
    
    private static final String GUARDRAIL_PREFIX = PolicyConstants.REASON_GUARDRAIL;
    
    /**
     * Check if operation is allowed for the company type
     * 
     * @param context policy context
     * @return denial reason if not allowed, null if allowed
     */
    public String checkGuardrails(PolicyContext context) {
        CompanyType companyType = context.getCompanyType();
        OperationType operation = context.getOperation();
        
        // Null safety
        if (companyType == null) {
            log.warn("CompanyType is null for user: {}. Denying by default.", context.getUserId());
            return GUARDRAIL_PREFIX + "_null_company_type";
        }
        
        if (operation == null) {
            log.warn("OperationType is null for endpoint: {}. Denying by default.", context.getEndpoint());
            return GUARDRAIL_PREFIX + "_null_operation";
        }
        
        // Apply guardrails based on company type
        return switch (companyType) {
            case INTERNAL -> checkInternalGuardrails(context, operation);
            case CUSTOMER -> checkCustomerGuardrails(context, operation);
            case SUPPLIER -> checkSupplierGuardrails(context, operation);
            case SUBCONTRACTOR -> checkSubcontractorGuardrails(context, operation);
        };
    }
    
    /**
     * INTERNAL company guardrails
     * Full access - no restrictions
     * 
     * @param context policy context
     * @param operation operation type
     * @return null (no restrictions)
     */
    private String checkInternalGuardrails(PolicyContext context, OperationType operation) {
        // Internal companies have full access
        // No guardrails
        return null;
    }
    
    /**
     * CUSTOMER company guardrails
     * Read-only access
     * 
     * @param context policy context
     * @param operation operation type
     * @return denial reason if not allowed
     */
    private String checkCustomerGuardrails(PolicyContext context, OperationType operation) {
        // Customers can only READ and EXPORT
        if (operation == OperationType.READ || operation == OperationType.EXPORT) {
            return null; // Allowed
        }
        
        log.info("Customer {} attempted {} operation on {}. Denied by guardrail.",
            context.getCompanyId(), operation, context.getEndpoint());
        
        return GUARDRAIL_PREFIX + "_customer_readonly";
    }
    
    /**
     * SUPPLIER company guardrails
     * Read + limited write (purchase orders)
     * 
     * @param context policy context
     * @param operation operation type
     * @return denial reason if not allowed
     */
    private String checkSupplierGuardrails(PolicyContext context, OperationType operation) {
        // Suppliers can READ, EXPORT
        if (operation == OperationType.READ || operation == OperationType.EXPORT) {
            return null; // Allowed
        }
        
        // Suppliers can WRITE only to purchase order related endpoints
        if (operation == OperationType.WRITE) {
            String endpoint = context.getEndpoint();
            
            // Allow write to purchase order related endpoints
            if (isPurchaseOrderEndpoint(endpoint)) {
                return null; // Allowed
            }
            
            log.info("Supplier {} attempted WRITE on non-purchase-order endpoint: {}. Denied.",
                context.getCompanyId(), endpoint);
            
            return GUARDRAIL_PREFIX + "_supplier_limited_write";
        }
        
        // All other operations denied
        log.info("Supplier {} attempted {} operation. Denied by guardrail.",
            context.getCompanyId(), operation);
        
        return GUARDRAIL_PREFIX + "_supplier_operation_denied";
    }
    
    /**
     * SUBCONTRACTOR company guardrails
     * Read + limited write (production orders)
     * 
     * @param context policy context
     * @param operation operation type
     * @return denial reason if not allowed
     */
    private String checkSubcontractorGuardrails(PolicyContext context, OperationType operation) {
        // Subcontractors can READ, EXPORT
        if (operation == OperationType.READ || operation == OperationType.EXPORT) {
            return null; // Allowed
        }
        
        // Subcontractors can WRITE only to production order related endpoints
        if (operation == OperationType.WRITE) {
            String endpoint = context.getEndpoint();
            
            // Allow write to production order related endpoints
            if (isProductionOrderEndpoint(endpoint)) {
                return null; // Allowed
            }
            
            log.info("Subcontractor {} attempted WRITE on non-production-order endpoint: {}. Denied.",
                context.getCompanyId(), endpoint);
            
            return GUARDRAIL_PREFIX + "_subcontractor_limited_write";
        }
        
        // All other operations denied
        log.info("Subcontractor {} attempted {} operation. Denied by guardrail.",
            context.getCompanyId(), operation);
        
        return GUARDRAIL_PREFIX + "_subcontractor_operation_denied";
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    /**
     * Check if endpoint is purchase order related
     * 
     * @param endpoint API endpoint
     * @return true if purchase order related
     */
    private boolean isPurchaseOrderEndpoint(String endpoint) {
        if (endpoint == null) {
            return false;
        }
        
        // Purchase order endpoints
        return endpoint.contains("/purchase-orders") ||
               endpoint.contains("/po/") ||
               endpoint.contains("/supplier/orders");
    }
    
    /**
     * Check if endpoint is production order related
     * 
     * @param endpoint API endpoint
     * @return true if production order related
     */
    private boolean isProductionOrderEndpoint(String endpoint) {
        if (endpoint == null) {
            return false;
        }
        
        // Production order endpoints
        return endpoint.contains("/production-orders") ||
               endpoint.contains("/production/") ||
               endpoint.contains("/subcontractor/orders");
    }
    
    /**
     * Check if company type allows operation (simple check)
     * 
     * @param companyType company type
     * @param operation operation type
     * @return true if allowed
     */
    public boolean isOperationAllowed(CompanyType companyType, OperationType operation) {
        if (companyType == null || operation == null) {
            return false;
        }
        
        return switch (companyType) {
            case INTERNAL -> true; // All operations allowed
            case CUSTOMER -> operation == OperationType.READ || operation == OperationType.EXPORT;
            case SUPPLIER -> operation == OperationType.READ || operation == OperationType.EXPORT;
            case SUBCONTRACTOR -> operation == OperationType.READ || operation == OperationType.EXPORT;
        };
    }
}

