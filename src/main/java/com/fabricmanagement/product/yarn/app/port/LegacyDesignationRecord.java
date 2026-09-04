package com.fabricmanagement.product.yarn.app.port;

import java.time.Instant;
import java.util.UUID;

/** A verbatim legacy designation and its stable provenance. Ranking is deliberately absent. */
public record LegacyDesignationRecord(
    UUID productId,
    LegacyDesignationSourceKind sourceKind,
    String rawValue,
    Instant recordedAt,
    String sourceRecordId) {}
