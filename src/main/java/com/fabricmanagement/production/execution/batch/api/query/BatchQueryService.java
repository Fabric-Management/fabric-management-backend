package com.fabricmanagement.production.execution.batch.api.query;

import com.fabricmanagement.production.execution.batch.domain.exception.BatchDomainException;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public contract for other modules (e.g. GoodsReceipt) to query Batch information without deeply
 * coupling to its internal domain or repositories.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BatchQueryService {

  private final BatchRepository batchRepository;

  /** Retrieves the logical Batch code for a given ID. Used for barcode generation. */
  public String getBatchCode(UUID id) {
    return batchRepository
        .findById(id)
        .orElseThrow(() -> new BatchDomainException("Batch not found with id: " + id))
        .getBatchCode();
  }
}
