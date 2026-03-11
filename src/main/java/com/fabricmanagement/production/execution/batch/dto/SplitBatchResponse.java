package com.fabricmanagement.production.execution.batch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response for POST /batches/{id}/split — returns both source and new batch. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitBatchResponse {

  private BatchDto sourceBatch;
  private BatchDto newBatch;
}
