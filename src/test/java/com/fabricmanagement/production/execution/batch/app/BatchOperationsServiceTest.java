package com.fabricmanagement.production.execution.batch.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.port.WarehouseLocationPort;
import com.fabricmanagement.production.execution.batch.dto.BatchDto;
import com.fabricmanagement.production.execution.batch.dto.PartialAcceptanceSplitRequest;
import com.fabricmanagement.production.execution.batch.dto.SplitBatchRequest;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchOverrideLogRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.lineage.app.BatchLineageService;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BatchOperationsServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID SOURCE_ID = UUID.randomUUID();
  private static final UUID COLOR_ID = UUID.randomUUID();

  @Mock private BatchService batchService;
  @Mock private BatchRepository batchRepository;
  @Mock private BatchOverrideLogRepository overrideLogRepository;
  @Mock private BatchCodeGenerator batchCodeGenerator;
  @Mock private BatchLineageService batchLineageService;
  @Mock private WarehouseLocationPort warehouseLocationPort;
  @Mock private ApplicationEventPublisher applicationEventPublisher;

  private BatchOperationsService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(UUID.randomUUID());
    service =
        new BatchOperationsService(
            batchService,
            batchRepository,
            overrideLogRepository,
            batchCodeGenerator,
            batchLineageService,
            warehouseLocationPort,
            applicationEventPublisher);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void splitBatch_inheritsSourceColor() {
    Batch source = sourceBatch();
    when(batchRepository.findByIdAndTenantId(SOURCE_ID, TENANT_ID)).thenReturn(Optional.of(source));
    when(batchCodeGenerator.generateSplitCode(SOURCE_ID, "LOT-1")).thenReturn("LOT-1-S1");
    when(batchRepository.save(any(Batch.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(batchService.toBatchDto(any(Batch.class))).thenReturn(BatchDto.builder().build());

    service.splitBatch(
        SOURCE_ID,
        SplitBatchRequest.builder()
            .acceptedQuantity(new BigDecimal("4"))
            .reason("Accepted portion")
            .rejectedStatus(BatchStatus.RETURNED)
            .build());

    assertThat(savedChild().getColorId()).isEqualTo(COLOR_ID);
  }

  @Test
  void splitPartialAcceptance_inheritsSourceColor() {
    Batch source = sourceBatch();
    when(batchRepository.findByIdAndTenantId(SOURCE_ID, TENANT_ID)).thenReturn(Optional.of(source));
    when(batchCodeGenerator.generateSplitCode(SOURCE_ID, "LOT-1")).thenReturn("LOT-1-S1");
    when(batchRepository.save(any(Batch.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(batchService.toBatchDto(any(Batch.class))).thenReturn(BatchDto.builder().build());

    service.splitPartialAcceptance(
        SOURCE_ID,
        PartialAcceptanceSplitRequest.builder()
            .acceptedQuantity(new BigDecimal("4"))
            .reason("Accepted portion")
            .rejectedStatus(BatchStatus.QC_REJECTED)
            .build());

    assertThat(savedChild().getColorId()).isEqualTo(COLOR_ID);
  }

  private Batch savedChild() {
    ArgumentCaptor<Batch> captor = ArgumentCaptor.forClass(Batch.class);
    org.mockito.Mockito.verify(batchRepository, org.mockito.Mockito.times(2))
        .save(captor.capture());
    return captor.getAllValues().get(1);
  }

  private Batch sourceBatch() {
    Batch batch =
        Batch.builder()
            .productId(UUID.randomUUID())
            .productType(ProductType.FABRIC)
            .colorId(COLOR_ID)
            .batchCode("LOT-1")
            .quantity(new BigDecimal("10"))
            .unit("MT")
            .status(BatchStatus.QUARANTINE)
            .build();
    batch.setId(SOURCE_ID);
    batch.setTenantId(TENANT_ID);
    return batch;
  }
}
