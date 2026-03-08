package com.fabricmanagement.production.execution.lineage.dto;

import com.fabricmanagement.production.execution.batch.dto.BatchDto;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated lineage view for a focal batch: its identity plus enriched parent &amp; child nodes.
 *
 * <p>Returned by the "batch lineage detail" endpoint so the UI can render the full one-step-back /
 * one-step-forward traceability panel in a single API call.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchLineageDetailDto {

  /** The focal batch for which lineage is being queried. */
  private BatchDto batch;

  /** Input batches consumed to produce this batch (backward trace, one level). */
  private List<LineageNodeDto> parents;

  /** Output batches produced from this batch (forward trace, one level). */
  private List<LineageNodeDto> children;
}
