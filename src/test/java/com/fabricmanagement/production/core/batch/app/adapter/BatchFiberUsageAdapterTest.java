package com.fabricmanagement.production.core.batch.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.production.core.batch.domain.BatchStatus;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BatchFiberUsageAdapterTest {

  @Mock private BatchRepository batchRepository;
  @InjectMocks private BatchFiberUsageAdapter adapter;

  @Test
  void delegatesToActiveProductionStatusQuery() {
    UUID tenantId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    when(batchRepository.existsByTenantIdAndProductIdAndStatusIn(
            tenantId, productId, BatchStatus.PRODUCTION_ACTIVE))
        .thenReturn(true);

    assertThat(adapter.isFiberInActiveProduction(tenantId, productId)).isTrue();

    verify(batchRepository)
        .existsByTenantIdAndProductIdAndStatusIn(
            tenantId, productId, BatchStatus.PRODUCTION_ACTIVE);
  }
}
