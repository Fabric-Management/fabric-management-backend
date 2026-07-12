package com.fabricmanagement.common.infrastructure.events;

import java.util.UUID;

public record StuckEventPresentation(
    String entityType,
    UUID entityId,
    String entityRef,
    String summary,
    String referenceType,
    UUID referenceId,
    UUID affectedUserId) {}
