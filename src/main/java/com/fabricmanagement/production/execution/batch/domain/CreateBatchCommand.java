package com.fabricmanagement.production.execution.batch.domain;

import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable command object for {@link Batch#create(CreateBatchCommand)}.
 *
 * <p>Replaces the 13-parameter factory method with a named, self-documenting command record. All
 * domain-relevant fields required to create a new batch are captured here.
 *
 * <p>Construction should happen in the service layer (after resolving qualityStandardId, composing
 * attributes, etc.). The domain entity stays decoupled from request DTOs.
 */
public record CreateBatchCommand(
    UUID tenantId,
    UUID materialId,
    MaterialType materialType,
    String batchCode,
    String supplierBatchCode,
    BigDecimal quantity,
    String unit,
    Instant productionDate,
    Instant expiryDate,
    UUID locationId,
    UUID qualityStandardId,
    String remarks,
    Map<String, Object> attributes,
    BatchSourceType sourceType,
    UUID sourceId) {

  /** Compact constructor — validates required fields eagerly. */
  public CreateBatchCommand {
    if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
    if (materialId == null) throw new IllegalArgumentException("materialId is required");
    if (materialType == null) throw new IllegalArgumentException("materialType is required");
    if (batchCode == null || batchCode.isBlank())
      throw new IllegalArgumentException("batchCode is required");
    if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0)
      throw new IllegalArgumentException("quantity must be positive");
    if (unit == null || unit.isBlank()) throw new IllegalArgumentException("unit is required");
    // Normalize attributes — never null in domain
    attributes = (attributes != null) ? attributes : Map.of();
  }
}
