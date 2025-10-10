package com.fabricmanagement.user.infrastructure.security;

import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.domain.policy.*;
import com.fabricmanagement.shared.infrastructure.exception.ForbiddenException;
import com.fabricmanagement.shared.infrastructure.policy.engine.PolicyEngine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for PolicyValidationFilter
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Policy Validation Filter Tests")
class PolicyValidationFilterTest {
    
    @Mock
    private PolicyEngine policyEngine;
    
    @Mock
    private HttpServletRequest request;
    
    @Mock
    private HttpServletResponse response;
    
    @Mock
    private FilterChain filterChain;
    
    private PolicyValidationFilter filter;
    private SecurityContext securityContext;
    
    @BeforeEach
    void setUp() {
        filter = new PolicyValidationFilter(policyEngine);
        
        securityContext = SecurityContext.builder()
            .userId(UUID.randomUUID().toString())
            .tenantId(UUID.randomUUID())
            .companyType(CompanyType.INTERNAL)
            .roles(new String[]{"ADMIN"})
            .build();
        
        // Setup Spring Security context
        Authentication authentication = new UsernamePasswordAuthenticationToken(
            securityContext, null, List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    
    @Test
    @DisplayName("Should allow when policy decision is ALLOW")
    void shouldAllowWhenPolicyAllows() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("X-Correlation-ID")).thenReturn(UUID.randomUUID().toString());
        
        PolicyDecision allowDecision = PolicyDecision.allow("role_default_access", "1.0", "test-correlation");
        when(policyEngine.evaluate(any(PolicyContext.class))).thenReturn(allowDecision);
        
        // When
        filter.doFilter(request, response, filterChain);
        
        // Then
        verify(filterChain, times(1)).doFilter(request, response);
        verify(policyEngine, times(1)).evaluate(any(PolicyContext.class));
    }
    
    @Test
    @DisplayName("Should deny when policy decision is DENY")
    void shouldDenyWhenPolicyDenies() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/users/delete");
        when(request.getMethod()).thenReturn("DELETE");
        
        PolicyDecision denyDecision = PolicyDecision.deny("role_no_access", "1.0", "test-correlation");
        when(policyEngine.evaluate(any(PolicyContext.class))).thenReturn(denyDecision);
        
        // When & Then
        assertThrows(ForbiddenException.class, () -> {
            filter.doFilter(request, response, filterChain);
        });
        
        verify(policyEngine, times(1)).evaluate(any(PolicyContext.class));
        verify(filterChain, never()).doFilter(any(), any());
    }
    
    @Test
    @DisplayName("Should skip policy check for public paths")
    void shouldSkipPublicPaths() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/actuator/health");
        
        // When
        filter.doFilter(request, response, filterChain);
        
        // Then
        verify(filterChain, times(1)).doFilter(request, response);
        verify(policyEngine, never()).evaluate(any());
    }
    
    @Test
    @DisplayName("Should pass correct PolicyContext to engine")
    void shouldPassCorrectPolicyContext() throws Exception {
        // Given
        String correlationId = UUID.randomUUID().toString();
        when(request.getRequestURI()).thenReturn("/api/v1/users/123");
        when(request.getMethod()).thenReturn("PUT");
        when(request.getHeader("X-Correlation-ID")).thenReturn(correlationId);
        when(request.getHeader("X-Request-ID")).thenReturn("req-123");
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        
        PolicyDecision allowDecision = PolicyDecision.allow("test", "1.0", correlationId);
        when(policyEngine.evaluate(any(PolicyContext.class))).thenReturn(allowDecision);
        
        // When
        filter.doFilter(request, response, filterChain);
        
        // Then
        ArgumentCaptor<PolicyContext> contextCaptor = ArgumentCaptor.forClass(PolicyContext.class);
        verify(policyEngine).evaluate(contextCaptor.capture());
        
        PolicyContext capturedContext = contextCaptor.getValue();
        assertNotNull(capturedContext);
        assertEquals("/api/v1/users/123", capturedContext.getEndpoint());
        assertEquals("PUT", capturedContext.getHttpMethod());
        assertEquals(OperationType.WRITE, capturedContext.getOperation());
        assertEquals(correlationId, capturedContext.getCorrelationId());
        assertEquals("192.168.1.1", capturedContext.getRequestIp());
    }
    
    @Test
    @DisplayName("Should skip when no authentication")
    void shouldSkipWhenNoAuthentication() throws Exception {
        // Given
        SecurityContextHolder.clearContext();
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        
        // When
        filter.doFilter(request, response, filterChain);
        
        // Then
        verify(filterChain, times(1)).doFilter(request, response);
        verify(policyEngine, never()).evaluate(any());
    }
    
    @Test
    @DisplayName("Should deny on policy evaluation error (fail-safe)")
    void shouldDenyOnEvaluationError() throws Exception {
        // Given
        when(request.getRequestURI()).thenReturn("/api/v1/users");
        when(request.getMethod()).thenReturn("GET");
        
        when(policyEngine.evaluate(any(PolicyContext.class)))
            .thenThrow(new RuntimeException("Database error"));
        
        // When & Then
        assertThrows(ForbiddenException.class, () -> {
            filter.doFilter(request, response, filterChain);
        });
        
        verify(filterChain, never()).doFilter(any(), any());
    }
}

