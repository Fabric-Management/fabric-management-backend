package com.fabricmanagement.production.core.batch.api.facade;

import com.fabricmanagement.production.core.batch.app.BatchOperationsService;
import com.fabricmanagement.production.core.batch.app.BatchService;
import com.fabricmanagement.production.core.batch.dto.BatchDto;
import com.fabricmanagement.production.core.batch.dto.CreateBlendedBatchRequest;
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
