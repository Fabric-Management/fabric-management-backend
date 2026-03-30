package com.fabricmanagement.production.execution.batch.api.facade;

import com.fabricmanagement.production.execution.batch.dto.BatchDto;
import java.util.Optional;
import java.util.UUID;

/** Cross-module communication port for Batch module. */
public interface BatchFacade {

  /**
   * Retrieves a batch by its ID for cross-module validation/usage.
   *
   * @param id The ID of the batch
   * @return Optional containing the batch details if found
   */
  Optional<BatchDto> getById(UUID id);

  /** Creates a blended batch for cross-module usage (e.g., from WorkOrder). */
  BatchDto createBlendedBatch(
      com.fabricmanagement.production.execution.batch.dto.CreateBlendedBatchRequest request);
}
