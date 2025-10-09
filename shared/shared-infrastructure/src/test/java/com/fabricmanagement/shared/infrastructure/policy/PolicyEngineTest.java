package com.fabricmanagement.shared.infrastructure.policy;

import com.fabricmanagement.shared.domain.policy.*;
import com.fabricmanagement.shared.infrastructure.policy.engine.PolicyEngine;
import com.fabricmanagement.shared.infrastructure.policy.guard.CompanyTypeGuard;
import com.fabricmanagement.shared.infrastructure.policy.resolver.ScopeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PolicyEngine
 * 
 * Tests the core PDP decision logic.
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyEngine Tests")
class PolicyEngineTest {
    
    @Mock
    private CompanyTypeGuard companyTypeGuard;
    
    @Mock
    private ScopeResolver scopeResolver;
    
    @InjectMocks
    private PolicyEngine policyEngine;
    
    private UUID userId;
    private UUID companyId;
    private String correlationId;
    
    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        correlationId = UUID.randomUUID().toString();
    }
    
    @Test
    @DisplayName("Should ALLOW when INTERNAL user with ADMIN role")
    void shouldAllowInternalAdmin() {
        // Given
        PolicyContext context = createContext(
            CompanyType.INTERNAL,
            OperationType.WRITE,
            DataScope.COMPANY,
            List.of("ADMIN")
        );
        
        when(companyTypeGuard.checkGuardrails(any())).thenReturn(null); // No guardrail violation
        when(scopeResolver.validateScope(any())).thenReturn(null); // Valid scope
        
        // When
        PolicyDecision decision = policyEngine.evaluate(context);
        
        // Then
        assertTrue(decision.isAllowed());
        assertEquals("role_default_allowed", decision.getReason());
        assertEquals(correlationId, decision.getCorrelationId());
        assertNotNull(decision.getDecidedAt());
        
        verify(companyTypeGuard).checkGuardrails(context);
        verify(scopeResolver).validateScope(context);
    }
    
    @Test
    @DisplayName("Should DENY when CUSTOMER tries to WRITE")
    void shouldDenyCustomerWrite() {
        // Given
        PolicyContext context = createContext(
            CompanyType.CUSTOMER,
            OperationType.WRITE,
            DataScope.COMPANY,
            List.of("USER")
        );
        
        when(companyTypeGuard.checkGuardrails(any()))
            .thenReturn("company_type_guardrail_customer_readonly");
        
        // When
        PolicyDecision decision = policyEngine.evaluate(context);
        
        // Then
        assertFalse(decision.isAllowed());
        assertTrue(decision.isDenied());
        assertEquals("company_type_guardrail_customer_readonly", decision.getReason());
        assertEquals(correlationId, decision.getCorrelationId());
        
        verify(companyTypeGuard).checkGuardrails(context);
        verify(scopeResolver, never()).validateScope(any()); // Should not reach scope validation
    }
    
    @Test
    @DisplayName("Should DENY when scope validation fails")
    void shouldDenyScopeViolation() {
        // Given
        PolicyContext context = createContext(
            CompanyType.INTERNAL,
            OperationType.READ,
            DataScope.SELF,
            List.of("USER")
        );
        
        when(companyTypeGuard.checkGuardrails(any())).thenReturn(null);
        when(scopeResolver.validateScope(any())).thenReturn("scope_violation_self_not_owner");
        
        // When
        PolicyDecision decision = policyEngine.evaluate(context);
        
        // Then
        assertFalse(decision.isAllowed());
        assertEquals("scope_violation_self_not_owner", decision.getReason());
        
        verify(scopeResolver).validateScope(context);
    }
    
    @Test
    @DisplayName("Should DENY when user has no recognized role")
    void shouldDenyNoRole() {
        // Given
        PolicyContext context = createContext(
            CompanyType.INTERNAL,
            OperationType.WRITE,
            DataScope.COMPANY,
            List.of() // No roles
        );
        
        when(companyTypeGuard.checkGuardrails(any())).thenReturn(null);
        
        // When
        PolicyDecision decision = policyEngine.evaluate(context);
        
        // Then
        assertFalse(decision.isAllowed());
        assertEquals("role_no_default_access", decision.getReason());
    }
    
    @Test
    @DisplayName("Should ALLOW when USER role with READ operation")
    void shouldAllowUserRead() {
        // Given
        PolicyContext context = createContext(
            CompanyType.INTERNAL,
            OperationType.READ,
            DataScope.COMPANY,
            List.of("USER")
        );
        
        when(companyTypeGuard.checkGuardrails(any())).thenReturn(null);
        when(scopeResolver.validateScope(any())).thenReturn(null);
        
        // When
        PolicyDecision decision = policyEngine.evaluate(context);
        
        // Then
        assertTrue(decision.isAllowed());
        assertEquals("role_default_allowed", decision.getReason());
    }
    
    @Test
    @DisplayName("Should DENY when USER role with WRITE operation")
    void shouldDenyUserWrite() {
        // Given
        PolicyContext context = createContext(
            CompanyType.INTERNAL,
            OperationType.WRITE,
            DataScope.COMPANY,
            List.of("USER")
        );
        
        when(companyTypeGuard.checkGuardrails(any())).thenReturn(null);
        
        // When
        PolicyDecision decision = policyEngine.evaluate(context);
        
        // Then
        assertFalse(decision.isAllowed());
        assertEquals("role_no_default_access", decision.getReason());
    }
    
    @Test
    @DisplayName("Should ALLOW when MANAGER role")
    void shouldAllowManager() {
        // Given
        PolicyContext context = createContext(
            CompanyType.INTERNAL,
            OperationType.DELETE,
            DataScope.COMPANY,
            List.of("MANAGER")
        );
        
        when(companyTypeGuard.checkGuardrails(any())).thenReturn(null);
        when(scopeResolver.validateScope(any())).thenReturn(null);
        
        // When
        PolicyDecision decision = policyEngine.evaluate(context);
        
        // Then
        assertTrue(decision.isAllowed());
    }
    
    @Test
    @DisplayName("Should DENY on exception (fail-safe)")
    void shouldDenyOnException() {
        // Given
        PolicyContext context = createContext(
            CompanyType.INTERNAL,
            OperationType.READ,
            DataScope.COMPANY,
            List.of("ADMIN")
        );
        
        when(companyTypeGuard.checkGuardrails(any()))
            .thenThrow(new RuntimeException("Simulated error"));
        
        // When
        PolicyDecision decision = policyEngine.evaluate(context);
        
        // Then
        assertFalse(decision.isAllowed());
        assertEquals("policy_evaluation_error", decision.getReason());
    }
    
    @Test
    @DisplayName("Quick check should return true for valid context")
    void quickCheckShouldReturnTrue() {
        // Given
        PolicyContext context = createContext(
            CompanyType.INTERNAL,
            OperationType.READ,
            DataScope.COMPANY,
            List.of("ADMIN")
        );
        
        when(companyTypeGuard.checkGuardrails(any())).thenReturn(null);
        
        // When
        boolean allowed = policyEngine.quickCheck(context);
        
        // Then
        assertTrue(allowed);
    }
    
    @Test
    @DisplayName("Quick check should return false for invalid context")
    void quickCheckShouldReturnFalse() {
        // Given
        PolicyContext context = createContext(
            CompanyType.CUSTOMER,
            OperationType.WRITE,
            DataScope.COMPANY,
            List.of("USER")
        );
        
        when(companyTypeGuard.checkGuardrails(any()))
            .thenReturn("company_type_guardrail_customer_readonly");
        
        // When
        boolean allowed = policyEngine.quickCheck(context);
        
        // Then
        assertFalse(allowed);
    }
    
    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    
    private PolicyContext createContext(CompanyType companyType, 
                                       OperationType operation,
                                       DataScope scope,
                                       List<String> roles) {
        return PolicyContext.builder()
            .userId(userId)
            .companyId(companyId)
            .companyType(companyType)
            .endpoint("/api/test")
            .httpMethod("POST")
            .operation(operation)
            .scope(scope)
            .roles(roles)
            .correlationId(correlationId)
            .requestId(UUID.randomUUID().toString())
            .requestIp("127.0.0.1")
            .build();
    }
}

