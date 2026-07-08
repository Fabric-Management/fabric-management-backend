package com.fabricmanagement.production.execution.batch.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.api.BatchLotQuantityIntentPort.LotIntentRequest;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchLotQuantityIntent;
import com.fabricmanagement.production.execution.batch.domain.BatchLotQuantityIntentStatus;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.event.BatchLotQuantityIntentPlacedEvent;
import com.fabricmanagement.production.execution.batch.domain.event.BatchLotQuantityIntentReleasedEvent;
import com.fabricmanagement.production.execution.batch.domain.exception.LotIntentQuantityExceededException;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchLotQuantityIntentRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchReservationRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BatchLotQuantityIntentServiceTest {

  @Mock private BatchLotQuantityIntentRepository intentRepository;
  @Mock private BatchRepository batchRepository;
  @Mock private BatchReservationRepository reservationRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private BatchLotQuantityIntentService service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID quoteId = UUID.randomUUID();
  private final UUID quoteLineId = UUID.randomUUID();
  private final UUID batchId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void shouldCalculateCoverageAfterOtherSoftIntentsAndHardReservations() {
    when(batchRepository.findAllById(anyList())).thenReturn(List.of(batch("100.000")));
    when(intentRepository.sumActiveByBatchIds(tenantId, List.of(batchId), quoteLineId))
        .thenReturn(Map.of(batchId, new BigDecimal("20.000")));
    when(reservationRepository.sumActiveRemainingByBatchIds(tenantId, List.of(batchId)))
        .thenReturn(Map.of(batchId, new BigDecimal("30.000")));

    assertTrue(
        service
            .checkCoverage(
                quoteLineId, List.of(new LotIntentRequest(batchId, new BigDecimal("50.000"), "M")))
            .covered());
    assertFalse(
        service
            .checkCoverage(
                quoteLineId, List.of(new LotIntentRequest(batchId, new BigDecimal("50.001"), "M")))
            .covered());
  }

  @Test
  void shouldRejectIntentQuantityAbovePhysicalQuantity() {
    when(batchRepository.findAllById(anyList())).thenReturn(List.of(batch("100.000")));

    LotIntentQuantityExceededException ex =
        assertThrows(
            LotIntentQuantityExceededException.class,
            () ->
                service.checkCoverage(
                    quoteLineId,
                    List.of(new LotIntentRequest(batchId, new BigDecimal("100.001"), "M"))));

    assertEquals("PRODUCTION_015_LOT_INTENT_QUANTITY_EXCEEDED", ex.getErrorCode());
    assertEquals(422, ex.getHttpStatus());
  }

  @Test
  void shouldReplaceIntentsAndReleaseRemovedLots() {
    UUID removedBatchId = UUID.randomUUID();
    BatchLotQuantityIntent removed =
        BatchLotQuantityIntent.place(
            tenantId,
            quoteId,
            "Q-001",
            quoteLineId,
            UUID.randomUUID(),
            "Ayse",
            removedBatchId,
            new BigDecimal("10.000"),
            "M",
            LocalDate.now().plusDays(5));

    when(batchRepository.findAllById(anyList())).thenReturn(List.of(batch("100.000")));
    when(intentRepository.sumActiveByBatchIds(tenantId, List.of(batchId), quoteLineId))
        .thenReturn(Map.of());
    when(reservationRepository.sumActiveRemainingByBatchIds(tenantId, List.of(batchId)))
        .thenReturn(Map.of());
    when(intentRepository.findByTenantIdAndQuoteLineId(tenantId, quoteLineId))
        .thenReturn(List.of(removed));
    when(intentRepository.save(any(BatchLotQuantityIntent.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service.replaceIntents(
        quoteId,
        "Q-001",
        quoteLineId,
        UUID.randomUUID(),
        "Ayse",
        LocalDate.now().plusDays(5),
        List.of(new LotIntentRequest(batchId, new BigDecimal("25.000"), "M")));

    assertEquals(BatchLotQuantityIntentStatus.RELEASED, removed.getStatus());
    verify(intentRepository, times(2)).save(any(BatchLotQuantityIntent.class));
    verify(eventPublisher).publishEvent(any(BatchLotQuantityIntentReleasedEvent.class));
    verify(eventPublisher).publishEvent(any(BatchLotQuantityIntentPlacedEvent.class));
  }

  @Test
  void shouldReleaseExpiredIntentsForCurrentTenant() {
    LocalDate today = LocalDate.now();
    BatchLotQuantityIntent expired =
        BatchLotQuantityIntent.place(
            tenantId,
            quoteId,
            "Q-001",
            quoteLineId,
            UUID.randomUUID(),
            "Ayse",
            batchId,
            new BigDecimal("10.000"),
            "M",
            today.minusDays(1));

    when(intentRepository.findByTenantIdAndStatusAndExpiresAtBeforeAndIsActiveTrue(
            tenantId, BatchLotQuantityIntentStatus.ACTIVE, today))
        .thenReturn(List.of(expired));
    when(intentRepository.save(any(BatchLotQuantityIntent.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    int released = service.releaseExpiredIntents(today);

    assertEquals(1, released);
    assertEquals(BatchLotQuantityIntentStatus.RELEASED, expired.getStatus());
    verify(eventPublisher).publishEvent(any(BatchLotQuantityIntentReleasedEvent.class));
  }

  private Batch batch(String physicalQuantity) {
    Batch batch =
        Batch.builder()
            .productId(UUID.randomUUID())
            .productType(ProductType.FABRIC)
            .batchCode("LOT-001")
            .quantity(new BigDecimal(physicalQuantity))
            .consumedQuantity(BigDecimal.ZERO)
            .reservedQuantity(BigDecimal.ZERO)
            .unit("M")
            .status(BatchStatus.AVAILABLE)
            .build();
    batch.setId(batchId);
    batch.setTenantId(tenantId);
    batch.setIsActive(true);
    return batch;
  }
}
