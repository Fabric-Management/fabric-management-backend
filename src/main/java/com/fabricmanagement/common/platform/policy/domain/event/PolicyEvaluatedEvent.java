package com.fabricmanagement.common.platform.policy.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Event fired when a policy is evaluated.
 *
 * <p>CRITICAL for audit trail and compliance!</p>
 * <p>Listeners: Audit, Analytics, Monitoring</p>
 */
@Getter
public class PolicyEvaluatedEvent extends DomainEvent {

    private final UUID userId;
    private final String resource;
    private final String action;
    private final boolean allowed;
    private final String reason;
    private final Long evaluationTimeMs;

    public PolicyEvaluatedEvent(UUID tenantId, UUID userId, String resource, String action,
                               boolean allowed, String reason, Long evaluationTimeMs) {
        super(tenantId, "POLICY_EVALUATED");
        this.userId = userId;
        this.resource = resource;
        this.action = action;
        this.allowed = allowed;
        this.reason = reason;
        this.evaluationTimeMs = evaluationTimeMs;
    }
}

