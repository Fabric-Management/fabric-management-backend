package com.fabricmanagement.production.core.batch.app.adapter;

import com.fabricmanagement.product.fiber.app.port.FiberUsagePort;
import com.fabricmanagement.production.core.batch.domain.BatchStatus;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves active production usage through the batch execution model. */
@Component
@RequiredArgsConstructor
public class BatchFiberUsageAdapter implements FiberUsagePort {

  private final BatchRepository batchRepository;

  @Override
  public boolean isFiberInActiveProduction(UUID tenantId, UUID productId) {
    return batchRepository.existsByTenantIdAndProductIdAndStatusIn(
        tenantId, productId, BatchStatus.PRODUCTION_ACTIVE);
  }
}
