package com.fabricmanagement.shared.infrastructure.policy;

import com.fabricmanagement.shared.domain.policy.*;
import com.fabricmanagement.shared.infrastructure.policy.audit.PolicyAuditService;
import com.fabricmanagement.shared.infrastructure.policy.repository.PolicyDecisionAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for PolicyAuditService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Policy Audit Service Tests")
class PolicyAuditServiceTest {
    
    @Mock
    private PolicyDecisionAuditRepository auditRepository;
    
    private PolicyAuditService auditService;
    
    @BeforeEach
    void setUp() {
        auditService = new PolicyAuditService(auditRepository);
    }
    
    @Test
    @DisplayName("Should log ALLOW decision synchronously")
    void shouldLogAllowDecision() {
        // Given
        PolicyContext context = createTestContext();
        PolicyDecision decision = PolicyDecision.allow("role_default_access", "1.0", context.getCorrelationId());
        
        // When
        auditService.logDecisionSync(context, decision, 50L);
        
        // Then
        ArgumentCaptor<PolicyDecisionAudit> captor = ArgumentCaptor.forClass(PolicyDecisionAudit.class);
        verify(auditRepository, times(1)).save(captor.capture());
        
        PolicyDecisionAudit audit = captor.getValue();
        assertEquals(context.getUserId(), audit.getUserId());
        assertEquals("ALLOW", audit.getDecision());
        assertEquals("role_default_access", audit.getReason());
    }
    
    @Test
    @DisplayName("Should log DENY decision synchronously")
    void shouldLogDenyDecision() {
        // Given
        PolicyContext context = createTestContext();
        PolicyDecision decision = PolicyDecision.deny("guardrail_customer_write", "1.0", context.getCorrelationId());
        
        // When
        auditService.logDecisionSync(context, decision, 25L);
        
        // Then
        ArgumentCaptor<PolicyDecisionAudit> captor = ArgumentCaptor.forClass(PolicyDecisionAudit.class);
        verify(auditRepository, times(1)).save(captor.capture());
        
        PolicyDecisionAudit audit = captor.getValue();
        assertEquals(context.getUserId(), audit.getUserId());
        assertEquals("DENY", audit.getDecision());
        assertEquals("guardrail_customer_write", audit.getReason());
    }
    
    @Test
    @DisplayName("Should query deny decisions")
    void shouldQueryDenyDecisions() {
        // Given
        Page<PolicyDecisionAudit> emptyPage = new PageImpl<>(Collections.emptyList());
        when(auditRepository.findDenyDecisions(any(), any()))
            .thenReturn(emptyPage);
        
        // When
        List<PolicyDecisionAudit> result = auditService.getDenyDecisions(24);
        
        // Then
        assertNotNull(result);
        verify(auditRepository, times(1)).findDenyDecisions(any(), any());
    }
    
    @Test
    @DisplayName("Should get statistics")
    void shouldGetStatistics() {
        // Given
        when(auditRepository.countByDecisionSince(any(), any())).thenReturn(100L, 10L);
        when(auditRepository.getAverageLatency(any())).thenReturn(45.5);
        
        // When
        String stats = auditService.getStats(1);
        
        // Then
        assertNotNull(stats, "Stats should not be null");
        assertTrue(stats.contains("allow") || stats.contains("ALLOW"), "Stats should contain allow count");
    }
    
    private PolicyContext createTestContext() {
        return PolicyContext.builder()
            .userId(UUID.randomUUID())
            .companyId(UUID.randomUUID())
            .companyType(CompanyType.INTERNAL)
            .endpoint("/api/v1/companies")
            .httpMethod("POST")
            .operation(OperationType.WRITE)
            .scope(DataScope.COMPANY)
            .correlationId(UUID.randomUUID().toString())
            .requestId(UUID.randomUUID().toString())
            .build();
    }
}

