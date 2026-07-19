package com.fabricmanagement.production.execution.batch.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchReservation;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.exception.BatchUnitMeasureMismatchException;
import com.fabricmanagement.production.execution.batch.dto.BatchDto;
import com.fabricmanagement.production.execution.batch.dto.CreateBatchRequest;
import com.fabricmanagement.production.execution.batch.dto.ReserveRequest;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchCertificationRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchReservationRepository;
import com.fabricmanagement.production.masterdata.color.api.query.ColorQueryService;
import com.fabricmanagement.production.masterdata.color.api.query.ColorQueryService.ColorReference;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberQualityStandardRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BatchServiceTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID BATCH_ID = UUID.randomUUID();

  @Mock private BatchRepository batchRepository;
  @Mock private BatchReservationRepository reservationRepository;
  @Mock private BatchCertificationRepository batchCertificationRepository;
  @Mock private FiberRepository fiberRepository;
  @Mock private FiberQualityStandardRepository qualityStandardRepository;
  @Mock private ColorQueryService colorQueryService;
  @Mock private ApplicationEventPublisher applicationEventPublisher;
  @Spy private BatchPrimaryMeasureService primaryMeasureService = new BatchPrimaryMeasureService();

  @InjectMocks private BatchService batchService;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void releaseFromQc_transitionsPendingQcBatchToAvailable() {
    Batch batch = pendingQcBatch();
    when(batchRepository.findByIdAndTenantId(BATCH_ID, TENANT_ID)).thenReturn(Optional.of(batch));
    when(batchRepository.save(any(Batch.class))).thenAnswer(inv -> inv.getArgument(0));

    BatchDto result = batchService.releaseFromQc(BATCH_ID);

    assertThat(batch.getStatus()).isEqualTo(BatchStatus.AVAILABLE);
    assertThat(result.getStatus()).isEqualTo(BatchStatus.AVAILABLE);
    verify(batchRepository).save(batch);
  }

  @Test
  void releaseFromQc_whenBatchNotFoundForTenant_throwsNotFound() {
    when(batchRepository.findByIdAndTenantId(BATCH_ID, TENANT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> batchService.releaseFromQc(BATCH_ID))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(BATCH_ID.toString());
    verify(batchRepository, never()).save(any());
  }

  @Test
  void updateColor_assignsActiveTenantColor() {
    UUID colorId = UUID.randomUUID();
    Batch batch = pendingQcBatch();
    when(batchRepository.findByIdAndTenantId(BATCH_ID, TENANT_ID)).thenReturn(Optional.of(batch));
    when(colorQueryService.findActiveReferenceById(colorId))
        .thenReturn(Optional.of(new ColorReference(colorId, "NAVY-01", "Navy", "#1F2A44", true)));
    when(batchRepository.save(batch)).thenReturn(batch);

    BatchDto result = batchService.updateColor(BATCH_ID, colorId);

    assertThat(batch.getColorId()).isEqualTo(colorId);
    assertThat(result.getColorId()).isEqualTo(colorId);
  }

  @Test
  void updateColor_clearsColorWithoutLookup() {
    Batch batch = pendingQcBatch();
    batch.setColorId(UUID.randomUUID());
    when(batchRepository.findByIdAndTenantId(BATCH_ID, TENANT_ID)).thenReturn(Optional.of(batch));
    when(batchRepository.save(batch)).thenReturn(batch);

    BatchDto result = batchService.updateColor(BATCH_ID, null);

    assertThat(batch.getColorId()).isNull();
    assertThat(result.getColorId()).isNull();
    verify(colorQueryService, never()).findActiveReferenceById(any());
  }

  @Test
  void updateColor_rejectsInactiveUnknownOrCrossTenantColorAsNotFound() {
    UUID colorId = UUID.randomUUID();
    Batch batch = pendingQcBatch();
    when(batchRepository.findByIdAndTenantId(BATCH_ID, TENANT_ID)).thenReturn(Optional.of(batch));
    when(colorQueryService.findActiveReferenceById(colorId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> batchService.updateColor(BATCH_ID, colorId))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining(colorId.toString());

    verify(batchRepository, never()).save(any());
  }

  @Test
  void create_acceptsActiveTenantColorAndExposesItInDto() {
    UUID colorId = UUID.randomUUID();
    when(colorQueryService.findActiveReferenceById(colorId))
        .thenReturn(Optional.of(new ColorReference(colorId, "NAVY-01", "Navy", "#1F2A44", true)));
    when(batchRepository.save(any(Batch.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    BatchDto result =
        batchService.create(
            CreateBatchRequest.builder()
                .productId(UUID.randomUUID())
                .productType(ProductType.FABRIC)
                .colorId(colorId)
                .batchCode("LOT-COLOR")
                .quantity(BigDecimal.ONE)
                .unit("KG")
                .build());

    assertThat(result.getColorId()).isEqualTo(colorId);
  }

  @Test
  void reserve_rejectsBatchUnitThatContradictsPrimaryMeasure() {
    Batch batch = availableBatch(ProductType.FABRIC, "KG", "100");
    when(batchRepository.findByIdAndTenantId(BATCH_ID, TENANT_ID)).thenReturn(Optional.of(batch));

    assertThatThrownBy(
            () ->
                batchService.reserve(
                    BATCH_ID,
                    ReserveRequest.builder()
                        .quantity(BigDecimal.TEN)
                        .referenceType("SALES_ORDER")
                        .build()))
        .isInstanceOf(BatchUnitMeasureMismatchException.class)
        .extracting(error -> ((BatchUnitMeasureMismatchException) error).getErrorCode())
        .isEqualTo("BATCH_UNIT_MEASURE_MISMATCH");

    verify(reservationRepository, never()).save(any());
  }

  @Test
  void reserve_convertsCompatibleBatchQuantityAndStoresCanonicalUnit() {
    Batch batch = availableBatch(ProductType.FIBER, "MT", "2");
    when(batchRepository.findByIdAndTenantId(BATCH_ID, TENANT_ID)).thenReturn(Optional.of(batch));
    when(batchRepository.save(any(Batch.class))).thenAnswer(inv -> inv.getArgument(0));
    when(reservationRepository.save(any(BatchReservation.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    var result =
        batchService.reserve(
            BATCH_ID,
            ReserveRequest.builder().quantity(BigDecimal.ONE).referenceType("SALES_ORDER").build());

    assertThat(result.getUnit()).isEqualTo("KG");
    assertThat(result.getReservedQuantity()).isEqualByComparingTo("1000");
    assertThat(batch.getReservedQuantity()).isEqualByComparingTo("1");
  }

  private Batch pendingQcBatch() {
    Batch batch =
        Batch.builder()
            .productId(UUID.randomUUID())
            .productType(ProductType.FABRIC)
            .batchCode("LOT-24011")
            .quantity(new BigDecimal("2000"))
            .unit("M")
            .status(BatchStatus.PENDING_QC)
            .build();
    batch.setId(BATCH_ID);
    batch.setTenantId(TENANT_ID);
    return batch;
  }

  private Batch availableBatch(ProductType productType, String unit, String quantity) {
    Batch batch =
        Batch.builder()
            .productId(UUID.randomUUID())
            .productType(productType)
            .batchCode("LOT-RESERVE")
            .quantity(new BigDecimal(quantity))
            .consumedQuantity(BigDecimal.ZERO)
            .reservedQuantity(BigDecimal.ZERO)
            .unit(unit)
            .status(BatchStatus.AVAILABLE)
            .build();
    batch.setId(BATCH_ID);
    batch.setTenantId(TENANT_ID);
    batch.setIsActive(true);
    return batch;
  }
}
