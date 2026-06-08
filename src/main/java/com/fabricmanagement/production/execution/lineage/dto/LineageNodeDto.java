package com.fabricmanagement.production.execution.lineage.dto;

import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.lineage.domain.BatchLineage;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Enriched lineage record that includes summary details of the related batch.
 *
 * <p>When used in a "parents" query, the batch fields describe the parent batch. When used in a
 * "children" query, the batch fields describe the child batch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineageNodeDto {

  private UUID lineageId;

  // ── Lineage edge info ──
  private BigDecimal consumedQuantity;
  private String unit;
  private BigDecimal consumptionPercentage;
  private Instant consumedAt;
  private String processReference;
  private String remarks;

  // ── Related batch summary ──
  private UUID batchId;
  private String batchCode;
  private ProductType productType;
  private BigDecimal batchQuantity;
  private BigDecimal availableQuantity;
  private String batchUnit;
  private BatchStatus batchStatus;

  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
  private Map<String, Object> attributes;

  /**
   * Build from a lineage record + the related batch (parent or child).
   *
   * @param lineage the lineage edge
   * @param relatedBatch the batch on the "other side" of the relationship
   */
  public static LineageNodeDto from(BatchLineage lineage, Batch relatedBatch) {
    return LineageNodeDto.builder()
        .lineageId(lineage.getId())
        .consumedQuantity(lineage.getConsumedQuantity())
        .unit(lineage.getUnit())
        .consumptionPercentage(lineage.getConsumptionPercentage())
        .consumedAt(lineage.getConsumedAt())
        .processReference(lineage.getProcessReference())
        .remarks(lineage.getRemarks())
        .batchId(relatedBatch.getId())
        .batchCode(relatedBatch.getBatchCode())
        .productType(relatedBatch.getProductType())
        .batchQuantity(relatedBatch.getQuantity())
        .availableQuantity(relatedBatch.getAvailableQuantity())
        .batchUnit(relatedBatch.getUnit())
        .batchStatus(relatedBatch.getStatus())
        .attributes(relatedBatch.getAttributes())
        .build();
  }
}
