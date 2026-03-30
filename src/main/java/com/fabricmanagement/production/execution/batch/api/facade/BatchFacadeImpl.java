package com.fabricmanagement.production.execution.batch.api.facade;

import com.fabricmanagement.production.execution.batch.app.BatchOperationsService;
import com.fabricmanagement.production.execution.batch.app.BatchService;
import com.fabricmanagement.production.execution.batch.dto.BatchDto;
import com.fabricmanagement.production.execution.batch.dto.CreateBlendedBatchRequest;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Implementation of the BatchFacade port. Delegates to respective app/ services. */
@Service
@RequiredArgsConstructor
public class BatchFacadeImpl implements BatchFacade {

  private final BatchService batchService;
  private final BatchOperationsService batchOperationsService;

  @Override
  public Optional<BatchDto> getById(UUID id) {
    return batchService.getById(id);
  }

  @Override
  public BatchDto createBlendedBatch(CreateBlendedBatchRequest request) {
    return batchOperationsService.createBlendedBatch(request);
  }
}
