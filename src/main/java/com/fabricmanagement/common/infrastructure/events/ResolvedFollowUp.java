package com.fabricmanagement.common.infrastructure.events;

import java.util.UUID;

public record ResolvedFollowUp(
    UUID tenantId,
    UUID affectedUserId,
    String entityType,
    String entityRef,
    UUID referenceId,
    String referenceType,
    String summary) {}
