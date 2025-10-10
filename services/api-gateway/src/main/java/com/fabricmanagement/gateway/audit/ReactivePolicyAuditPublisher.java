package com.fabricmanagement.gateway.audit;

import com.fabricmanagement.shared.domain.policy.PolicyAuditEvent;
import com.fabricmanagement.shared.domain.policy.PolicyContext;
import com.fabricmanagement.shared.domain.policy.PolicyDecision;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

/**
 * Reactive Policy Audit Publisher (Gateway-specific)
 * 
 * Lightweight audit publisher for API Gateway.
 * 
 * Design:
 * - Reactive (non-blocking)
 * - Kafka-only (no database - Gateway has no DB)
 * - Fire-and-forget pattern
 * - Fail-safe (audit failure doesn't block request)
 * 
 * Why not use PolicyAuditService?
 * - PolicyAuditService uses JPA (blocking I/O)
 * - Gateway is reactive (WebFlux) - incompatible
 * - Gateway has no database access
 * 
 * Architecture:
 * - Gateway → Kafka (this class)
 * - Company Service → Consume & persist to DB
 * 
 * @author Fabric Management Team
 * @since 2.0 (Policy Authorization - Phase 3)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "policy.audit.enabled", havingValue = "true", matchIfMissing = true)
public class ReactivePolicyAuditPublisher {
    
    private static final String KAFKA_TOPIC = "policy.audit";
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * Publish policy decision audit event (reactive, non-blocking)
     * 
     * @param context policy context
     * @param decision policy decision
     * @param latencyMs evaluation latency
     * @return Mono<Void> for reactive composition
     */
    public Mono<Void> publishDecision(PolicyContext context, PolicyDecision decision, long latencyMs) {
        return Mono.fromRunnable(() -> publishSync(context, decision, latencyMs))
            .subscribeOn(Schedulers.boundedElastic()) // Offload to separate thread
            .onErrorResume(error -> {
                log.error("Failed to publish audit event. Context: {}, Decision: {}, Error: {}",
                    context.getEndpoint(), decision.getReason(), error.getMessage());
                return Mono.empty(); // Fail-safe: swallow error
            })
            .then();
    }
    
    /**
     * Synchronous publish (called on separate thread)
     */
    private void publishSync(PolicyContext context, PolicyDecision decision, long latencyMs) {
        try {
            PolicyAuditEvent event = buildAuditEvent(context, decision, latencyMs);
            String eventJson = objectMapper.writeValueAsString(event);
            
            String key = context.getCorrelationId() != null ? context.getCorrelationId() :
                        (context.getRequestId() != null ? context.getRequestId() : 
                         context.getUserId().toString());
            
            kafkaTemplate.send(KAFKA_TOPIC, key, eventJson);
            
            log.debug("Published policy audit event. Topic: {}, Decision: {}, Endpoint: {}, Latency: {}ms",
                KAFKA_TOPIC, decision.isAllowed() ? "ALLOW" : "DENY", context.getEndpoint(), latencyMs);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize policy audit event", e);
        } catch (Exception e) {
            log.error("Failed to send audit event to Kafka", e);
        }
    }
    
    /**
     * Build audit event from context and decision
     */
    private PolicyAuditEvent buildAuditEvent(PolicyContext context, PolicyDecision decision, long latencyMs) {
        return PolicyAuditEvent.builder()
            .userId(context.getUserId())
            .companyId(context.getCompanyId())
            .companyType(context.getCompanyType() != null ? context.getCompanyType().name() : null)
            .endpoint(context.getEndpoint())
            .httpMethod(context.getHttpMethod())
            .operation(context.getOperation() != null ? context.getOperation().name() : null)
            .scope(context.getScope() != null ? context.getScope().name() : null)
            .decision(decision.isAllowed() ? "ALLOW" : "DENY")
            .reason(decision.getReason())
            .policyVersion(decision.getPolicyVersion())
            .timestamp(LocalDateTime.now())
            .latencyMs((int) latencyMs)
            .requestId(context.getRequestId())
            .correlationId(context.getCorrelationId())
            .requestIp(context.getRequestIp())
            .userRoles(context.getRoles() != null ? String.join(",", context.getRoles()) : null)
            .build();
    }
}

