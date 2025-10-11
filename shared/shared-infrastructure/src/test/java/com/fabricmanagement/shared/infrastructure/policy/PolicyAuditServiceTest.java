package com.fabricmanagement.shared.infrastructure.policy;

import com.fabricmanagement.shared.domain.policy.*;
import com.fabricmanagement.shared.infrastructure.policy.audit.PolicyAuditService;
import com.fabricmanagement.shared.infrastructure.policy.repository.PolicyDecisionAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PolicyAuditService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Policy Audit Service Tests")
class PolicyAuditServiceTest {
    
    @Mock
    private PolicyDecisionAuditRepository auditRepository;
    
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Mock
    private ObjectMapper objectMapper;
    
    private PolicyAuditService auditService;
    private PolicyAuditService auditServiceWithoutKafka;
    
    @BeforeEach
    void setUp() {
        // Service with Kafka
        auditService = new PolicyAuditService(auditRepository, kafkaTemplate, objectMapper);
        
        // Service without Kafka (for backward compatibility tests)
        auditServiceWithoutKafka = new PolicyAuditService(auditRepository, null, objectMapper);
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
    
    // =========================================================================
    // KAFKA PUBLISHING TESTS (Phase 3)
    // =========================================================================
    
    @Test
    @DisplayName("Should publish event to Kafka when logging decision")
    void shouldPublishToKafkaWhenLoggingDecision() throws Exception {
        // Given
        PolicyContext context = createTestContext();
        PolicyDecision decision = PolicyDecision.allow("role_default_access", "1.0", context.getCorrelationId());
        
        when(auditRepository.save(any(PolicyDecisionAudit.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        when(objectMapper.writeValueAsString(any()))
            .thenReturn("{\"eventType\":\"PolicyAuditEvent\"}");
        
        // When
        auditService.logDecisionSync(context, decision, 50L);
        
        // Then
        verify(auditRepository, times(1)).save(any(PolicyDecisionAudit.class));
        verify(objectMapper, times(1)).writeValueAsString(any(PolicyAuditEvent.class));
        verify(kafkaTemplate, times(1)).send(eq("policy.audit"), anyString(), anyString());
    }
    
    @Test
    @DisplayName("Should save to DB even if Kafka publishing fails")
    void shouldSaveToDbEvenIfKafkaFails() throws Exception {
        // Given
        PolicyContext context = createTestContext();
        PolicyDecision decision = PolicyDecision.deny("guardrail_violation", "1.0", context.getCorrelationId());
        
        when(auditRepository.save(any(PolicyDecisionAudit.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // Simulate Kafka failure
        lenient().when(objectMapper.writeValueAsString(any()))
            .thenThrow(new RuntimeException("Kafka unavailable"));
        
        // When
        auditService.logDecisionSync(context, decision, 25L);
        
        // Then
        verify(auditRepository, times(1)).save(any(PolicyDecisionAudit.class));
        // Kafka failure should not prevent DB save (fail-safe pattern)
    }
    
    @Test
    @DisplayName("Should work without Kafka when not available")
    void shouldWorkWithoutKafka() throws Exception {
        // Given
        PolicyContext context = createTestContext();
        PolicyDecision decision = PolicyDecision.allow("role_default_access", "1.0", context.getCorrelationId());
        
        when(auditRepository.save(any(PolicyDecisionAudit.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        auditServiceWithoutKafka.logDecisionSync(context, decision, 30L);
        
        // Then
        verify(auditRepository, times(1)).save(any(PolicyDecisionAudit.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(objectMapper, never()).writeValueAsString(any());
    }
    
    @Test
    @DisplayName("Should use correlationId as Kafka message key")
    void shouldUseCorrelationIdAsKafkaKey() throws Exception {
        // Given
        String correlationId = UUID.randomUUID().toString();
        PolicyContext context = PolicyContext.builder()
            .userId(UUID.randomUUID())
            .companyId(UUID.randomUUID())
            .companyType(CompanyType.INTERNAL)
            .endpoint("/api/v1/test")
            .httpMethod("GET")
            .operation(OperationType.READ)
            .scope(DataScope.COMPANY)
            .correlationId(correlationId) // Specific correlation ID
            .requestId(UUID.randomUUID().toString())
            .build();
        
        PolicyDecision decision = PolicyDecision.allow("test", "1.0", correlationId);
        
        when(auditRepository.save(any(PolicyDecisionAudit.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        
        when(objectMapper.writeValueAsString(any()))
            .thenReturn("{\"eventType\":\"PolicyAuditEvent\"}");
        
        // When
        auditService.logDecisionSync(context, decision, 10L);
        
        // Then
        verify(kafkaTemplate, times(1)).send(
            eq("policy.audit"), 
            eq(correlationId), // Should use correlationId as key
            anyString()
        );
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

