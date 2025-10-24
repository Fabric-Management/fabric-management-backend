package com.fabricmanagement.gateway.audit;

import com.fabricmanagement.shared.domain.policy.PolicyAuditEvent;
import com.fabricmanagement.shared.domain.policy.PolicyContext;
import com.fabricmanagement.shared.domain.policy.PolicyDecision;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
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
 * - Config-driven enable/disable
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
public class ReactivePolicyAuditPublisher {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    // ✅ Config-driven: Enable/disable audit publishing
    @org.springframework.beans.factory.annotation.Value("${policy.audit.enabled:false}")
    private boolean auditEnabled;
    
    // ✅ Config-driven topic name (ZERO HARDCODED!)
    @org.springframework.beans.factory.annotation.Value("${kafka.topics.policy-audit:policy.audit}")
    private String kafkaTopic;
    
    @PostConstruct
    public void init() {
        if (auditEnabled) {
            log.info("✅ Policy audit publisher initialized and ENABLED - Topic: {}", kafkaTopic);
        } else {
            log.warn("⚠️ Policy audit publisher initialized but DISABLED by configuration.");
        }
    }
    
    /**
     * Publish policy decision audit event (reactive, non-blocking)
     * 
     * @param context policy context
     * @param decision policy decision
     * @param latencyMs evaluation latency
     * @return Mono<Void> for reactive composition
     */
    public Mono<Void> publishDecision(PolicyContext context, PolicyDecision decision, long latencyMs) {
        // ✅ Config-driven: Skip if audit disabled
        if (!auditEnabled) {
            log.debug("Policy audit disabled - skipping event: {}", context.getEndpoint());
            return Mono.empty();
        }
        
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
     * Async publish (fire-and-forget, non-blocking)
     */
    private void publishSync(PolicyContext context, PolicyDecision decision, long latencyMs) {
        try {
            PolicyAuditEvent event = buildAuditEvent(context, decision, latencyMs);
            String eventJson = objectMapper.writeValueAsString(event);
            
            String key = context.getCorrelationId() != null ? context.getCorrelationId() :
                        (context.getRequestId() != null ? context.getRequestId() : 
                         context.getUserId().toString());
            
            // ✅ ASYNC: Fire-and-forget (CompletableFuture)
            kafkaTemplate.send(kafkaTopic, key, eventJson)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("✅ Policy audit published: {}, Decision: {}", 
                            context.getEndpoint(), decision.isAllowed() ? "ALLOW" : "DENY");
                    } else {
                        log.warn("⚠️ Policy audit failed (non-blocking): {} - {}", 
                            context.getEndpoint(), ex.getMessage());
                    }
                });
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize policy audit event", e);
        } catch (Exception e) {
            log.warn("Failed to send audit event (non-blocking): {}", e.getMessage());
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

