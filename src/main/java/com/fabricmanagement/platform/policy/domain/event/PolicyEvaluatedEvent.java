package com.fabricmanagement.platform.policy.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Event fired when a policy is evaluated.
 *
 * <p>CRITICAL for audit trail and compliance!
 *
 * <p>Listeners: Audit, Analytics, Monitoring
 */
@Getter
public class PolicyEvaluatedEvent extends DomainEvent {

  private final UUID userId;
  private final String resource;
  private final String action;
  private final boolean allowed;
  private final String reason;
  private final Long evaluationTimeMs;

  public PolicyEvaluatedEvent(
      UUID tenantId,
      UUID userId,
      String resource,
      String action,
      boolean allowed,
      String reason,
      Long evaluationTimeMs) {
    super(tenantId, "POLICY_EVALUATED");
    this.userId = userId;
    this.resource = resource;
    this.action = action;
    this.allowed = allowed;
    this.reason = reason;
    this.evaluationTimeMs = evaluationTimeMs;
  }

  @JsonCreator
  public PolicyEvaluatedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("userId") UUID userId,
      @JsonProperty("resource") String resource,
      @JsonProperty("action") String action,
      @JsonProperty("allowed") boolean allowed,
      @JsonProperty("reason") String reason,
      @JsonProperty("evaluationTimeMs") Long evaluationTimeMs) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "POLICY_EVALUATED",
        occurredAt,
        correlationId);
    this.userId = userId;
    this.resource = resource;
    this.action = action;
    this.allowed = allowed;
    this.reason = reason;
    this.evaluationTimeMs = evaluationTimeMs;
  }
}
