package com.fabricmanagement.production.execution.output.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.output.domain.ProductionOutputItem;
import com.fabricmanagement.production.execution.output.domain.ProductionOutputRecord;
import com.fabricmanagement.production.execution.output.infra.repository.ProductionOutputItemRepository;
import com.fabricmanagement.production.execution.output.infra.repository.ProductionOutputRecordRepository;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductionOutputServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID RECORD_ID = UUID.randomUUID();
  private static final UUID BATCH_ID = UUID.randomUUID();

  @Mock private ProductionOutputRecordRepository recordRepository;
  @Mock private ProductionOutputItemRepository itemRepository;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private ProductFacade productFacade;
  @Mock private BatchRepository batchRepository;
  @InjectMocks private ProductionOutputService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(UUID.randomUUID());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void confirmSynchronouslyClosesAvailabilityGateBeforePublishingOutputEvent() {
    ProductionOutputRecord record =
        ProductionOutputRecord.create(
            TENANT_ID,
            UUID.randomUUID(),
            "WO-100",
            BATCH_ID,
            UUID.randomUUID(),
            ProductType.FABRIC,
            "M",
            null);
    record.setId(RECORD_ID);
    record.addItem(
        ProductionOutputItem.create(
            PackageType.ROLL, new BigDecimal("25"), null, UUID.randomUUID(), 0, null));
    Batch batch = new Batch();
    batch.setId(BATCH_ID);
    batch.setTenantId(TENANT_ID);
    batch.setStatus(BatchStatus.AVAILABLE);
    batch.setReservedQuantity(BigDecimal.ZERO);
    batch.setConsumedQuantity(BigDecimal.ZERO);
    when(recordRepository.findByIdAndTenantIdAndIsActiveTrue(RECORD_ID, TENANT_ID))
        .thenReturn(Optional.of(record));
    when(batchRepository.findByIdAndTenantIdForUpdate(BATCH_ID, TENANT_ID))
        .thenReturn(Optional.of(batch));
    when(recordRepository.save(record)).thenReturn(record);

    service.confirm(RECORD_ID);

    assertThat(batch.getStatus()).isEqualTo(BatchStatus.PENDING_QC);
    verify(batchRepository).save(batch);
    verify(eventPublisher).publish(any());
  }
}
