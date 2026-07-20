package com.fabricmanagement.production.execution.batch.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.fabricmanagement.production.execution.batch.domain.exception.LotIntentUnitMismatchException;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchLotQuantityIntentRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BatchLotQuantityIntentServiceTest {

  @Mock private BatchLotQuantityIntentRepository intentRepository;
  @Mock private BatchRepository batchRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private BatchCommitmentQuantityService commitmentQuantityService;
  @Spy private BatchPrimaryMeasureService primaryMeasureService = new BatchPrimaryMeasureService();

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
    Batch batch = batch("100.000");
    when(batchRepository.findByTenantIdAndIdInAndIsActiveTrue(tenantId, List.of(batchId)))
        .thenReturn(List.of(batch));
    when(commitmentQuantityService.summarize(eq(tenantId), any(), eq(quoteLineId)))
        .thenReturn(
            Map.of(
                batchId,
                new BatchCommitmentQuantityService.Summary(
                    new BigDecimal("20.000"), new BigDecimal("30.000"), List.of())));

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
    when(batchRepository.findByTenantIdAndIdInAndIsActiveTrue(tenantId, List.of(batchId)))
        .thenReturn(List.of(batch("100.000")));

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
  void shouldRejectWrongUnitFromCoverageAndReplaceWithTheSameCodedError() {
    Batch batch = batch("100.000");
    when(batchRepository.findByTenantIdAndIdInAndIsActiveTrue(tenantId, List.of(batchId)))
        .thenReturn(List.of(batch));
    LotIntentRequest wrong = new LotIntentRequest(batchId, BigDecimal.TEN, "KG");

    LotIntentUnitMismatchException coverageError =
        assertThrows(
            LotIntentUnitMismatchException.class,
            () -> service.checkCoverage(quoteLineId, List.of(wrong)));
    LotIntentUnitMismatchException replaceError =
        assertThrows(
            LotIntentUnitMismatchException.class,
            () ->
                service.replaceIntents(
                    quoteId,
                    "Q-001",
                    quoteLineId,
                    UUID.randomUUID(),
                    "Ayse",
                    LocalDate.now().plusDays(5),
                    List.of(wrong)));

    assertEquals("LOT_INTENT_UNIT_MISMATCH", coverageError.getErrorCode());
    assertEquals(422, coverageError.getHttpStatus());
    assertEquals(coverageError.getErrorCode(), replaceError.getErrorCode());
  }

  @Test
  void shouldAcceptExactMetricUnitAndPersistCanonicalQuantityAndUnit() {
    Batch batch = batch("100.000");
    when(batchRepository.findByTenantIdAndIdInAndIsActiveTrue(tenantId, List.of(batchId)))
        .thenReturn(List.of(batch));
    when(commitmentQuantityService.summarize(eq(tenantId), any(), eq(quoteLineId)))
        .thenReturn(
            Map.of(
                batchId,
                new BatchCommitmentQuantityService.Summary(
                    BigDecimal.ZERO, BigDecimal.ZERO, List.of())));
    when(intentRepository.findByTenantIdAndQuoteLineId(tenantId, quoteLineId))
        .thenReturn(List.of());
    when(intentRepository.save(any(BatchLotQuantityIntent.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service.replaceIntents(
        quoteId,
        "Q-001",
        quoteLineId,
        UUID.randomUUID(),
        "Ayse",
        LocalDate.now().plusDays(5),
        List.of(new LotIntentRequest(batchId, new BigDecimal("250"), "CM")));

    var captor = org.mockito.ArgumentCaptor.forClass(BatchLotQuantityIntent.class);
    verify(intentRepository).save(captor.capture());
    assertEquals(0, captor.getValue().getQuantity().compareTo(new BigDecimal("2.5")));
    assertEquals("M", captor.getValue().getUnit());
  }

  @Test
  void shouldCompareCoverageInCanonicalUnits() {
    Batch batch = batch("2", ProductType.FIBER, "MT");
    when(batchRepository.findByTenantIdAndIdInAndIsActiveTrue(tenantId, List.of(batchId)))
        .thenReturn(List.of(batch));
    when(commitmentQuantityService.summarize(eq(tenantId), any(), eq(quoteLineId)))
        .thenReturn(
            Map.of(
                batchId,
                new BatchCommitmentQuantityService.Summary(
                    BigDecimal.ZERO, BigDecimal.ZERO, List.of())));

    assertTrue(
        service
            .checkCoverage(
                quoteLineId, List.of(new LotIntentRequest(batchId, new BigDecimal("1500"), "KG")))
            .covered());
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

    Batch batch = batch("100.000");
    when(batchRepository.findByTenantIdAndIdInAndIsActiveTrue(tenantId, List.of(batchId)))
        .thenReturn(List.of(batch));
    when(commitmentQuantityService.summarize(eq(tenantId), any(), eq(quoteLineId)))
        .thenReturn(
            Map.of(
                batchId,
                new BatchCommitmentQuantityService.Summary(
                    BigDecimal.ZERO, BigDecimal.ZERO, List.of())));
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

  @Test
  void shouldResyncExpiryOnActiveIntentsWhenValidUntilChanges() {
    LocalDate oldExpiry = LocalDate.now().plusDays(5);
    LocalDate newExpiry = LocalDate.now().plusDays(15);
    BatchLotQuantityIntent intent = intent(oldExpiry);

    when(intentRepository.findByTenantIdAndQuoteIdAndStatusAndIsActiveTrue(
            tenantId, quoteId, BatchLotQuantityIntentStatus.ACTIVE))
        .thenReturn(List.of(intent));
    when(intentRepository.save(any(BatchLotQuantityIntent.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service.resyncExpiry(quoteId, newExpiry);

    assertEquals(newExpiry, intent.getExpiresAt());
    verify(intentRepository).save(intent);
  }

  @Test
  void shouldSkipSaveWhenExpiryAlreadyMatches() {
    LocalDate expiry = LocalDate.now().plusDays(5);
    BatchLotQuantityIntent intent = intent(expiry);

    when(intentRepository.findByTenantIdAndQuoteIdAndStatusAndIsActiveTrue(
            tenantId, quoteId, BatchLotQuantityIntentStatus.ACTIVE))
        .thenReturn(List.of(intent));

    service.resyncExpiry(quoteId, expiry);

    verify(intentRepository, times(0)).save(any(BatchLotQuantityIntent.class));
  }

  @Test
  void shouldNotResyncReleasedIntents() {
    LocalDate oldExpiry = LocalDate.now().plusDays(5);
    BatchLotQuantityIntent released = intent(oldExpiry);
    released.release(java.time.Instant.now());

    when(intentRepository.findByTenantIdAndQuoteIdAndStatusAndIsActiveTrue(
            tenantId, quoteId, BatchLotQuantityIntentStatus.ACTIVE))
        .thenReturn(List.of(released));

    service.resyncExpiry(quoteId, oldExpiry.plusDays(10));

    assertEquals(oldExpiry, released.getExpiresAt());
    verify(intentRepository, times(0)).save(any(BatchLotQuantityIntent.class));
  }

  private BatchLotQuantityIntent intent(LocalDate expiresAt) {
    return BatchLotQuantityIntent.place(
        tenantId,
        quoteId,
        "Q-001",
        quoteLineId,
        UUID.randomUUID(),
        "Ayse",
        batchId,
        new BigDecimal("10.000"),
        "M",
        expiresAt);
  }

  private Batch batch(String physicalQuantity) {
    return batch(physicalQuantity, ProductType.FABRIC, "M");
  }

  private Batch batch(String physicalQuantity, ProductType productType, String unit) {
    Batch batch =
        Batch.builder()
            .productId(UUID.randomUUID())
            .productType(productType)
            .batchCode("LOT-001")
            .quantity(new BigDecimal(physicalQuantity))
            .consumedQuantity(BigDecimal.ZERO)
            .reservedQuantity(BigDecimal.ZERO)
            .unit(unit)
            .status(BatchStatus.AVAILABLE)
            .build();
    batch.setId(batchId);
    batch.setTenantId(tenantId);
    batch.setIsActive(true);
    return batch;
  }
}
