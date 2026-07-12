package com.fabricmanagement.common.infrastructure.events;

import java.time.Instant;
import java.util.UUID;

public record FollowUpFeedbackReport(
    UUID tenantId,
    UUID publicationId,
    String eventType,
    String entityType,
    String entityRef,
    String summary,
    String referenceType,
    UUID referenceId,
    UUID affectedUserId,
    Instant detectedAt) {}
