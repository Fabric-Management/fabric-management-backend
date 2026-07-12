package com.fabricmanagement.common.infrastructure.events;

import java.time.Instant;
import java.util.UUID;

public record StuckEventContext(
    UUID publicationId,
    String eventType,
    String listenerId,
    UUID tenantId,
    String payload,
    Instant firstSeenAt) {}
