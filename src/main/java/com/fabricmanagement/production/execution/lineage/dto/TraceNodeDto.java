package com.fabricmanagement.production.execution.lineage.dto;

import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A node in a recursive lineage trace tree.
 *
 * <p>Used by the backward/forward trace endpoints to return the full ancestry or descendant tree
 * for a batch. Each node contains the batch identity and the edge details that connect it to its
 * parent in the tree.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceNodeDto {

  private UUID batchId;
  private String batchCode;
  private ProductType productType;
  private BigDecimal quantity;
  private String unit;
  private BatchStatus status;

  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
  private Map<String, Object> attributes;

  /** Depth in the trace tree (0 = focal batch, 1 = direct parent/child, etc.) */
  private int depth;

  // ── Edge details (null for the root node) ──
  private BigDecimal consumedQuantity;
  private BigDecimal consumptionPercentage;
  private String processReference;

  /** Recursive children of this node in the trace direction. */
  @Builder.Default private List<TraceNodeDto> children = new ArrayList<>();

  public static TraceNodeDto fromBatch(Batch batch, int depth) {
    return TraceNodeDto.builder()
        .batchId(batch.getId())
        .batchCode(batch.getBatchCode())
        .productType(batch.getProductType())
        .quantity(batch.getQuantity())
        .unit(batch.getUnit())
        .status(batch.getStatus())
        .attributes(batch.getAttributes())
        .depth(depth)
        .children(new ArrayList<>())
        .build();
  }
}
