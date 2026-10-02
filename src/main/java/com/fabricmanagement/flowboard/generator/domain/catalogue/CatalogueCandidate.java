package com.fabricmanagement.flowboard.generator.domain.catalogue;

import java.time.Instant;
import java.util.UUID;

/** A target-tenant task-template row, reduced to what the §5 reconciliation needs. */
public record CatalogueCandidate(
    UUID id,
    String catalogKey,
    String eventType,
    String taskType,
    String name,
    String description,
    String uid,
    UUID createdBy,
    UUID updatedBy,
    long version,
    Instant deletedAt,
    String fingerprint) {}
