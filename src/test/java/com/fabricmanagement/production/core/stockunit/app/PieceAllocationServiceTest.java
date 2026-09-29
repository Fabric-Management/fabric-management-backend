package com.fabricmanagement.production.core.stockunit.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.production.core.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchReservation;
import com.fabricmanagement.production.core.batch.domain.BatchStatus;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchReservationRepository;
import com.fabricmanagement.production.core.stockunit.api.PieceAllocationPort;
import com.fabricmanagement.production.core.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.core.stockunit.domain.StockUnit;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitAllocation;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitAllocationStatus;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCut;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitAllocationRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitCutRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** SOI D4 / R22: piece allocation is all-or-nothing and a piece is held for one line only. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PieceAllocationServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ORDER = UUID.randomUUID();
  private static final UUID LINE = UUID.randomUUID();
  private static final UUID ACTOR = UUID.randomUUID();

  @Mock private BatchRepository batchRepository;
  @Mock private BatchReservationRepository reservationRepository;
  @Mock private StockUnitRepository stockUnitRepository;
  @Mock private StockUnitAllocationRepository allocationRepository;
  @Mock private StockUnitCutRepository cutRepository;

  private PieceAllocationService service;
  private Batch lot;
  private StockUnit piece;

  @BeforeEach
  void setUp() {
    service =
        new PieceAllocationService(
            batchRepository,
            reservationRepository,
            stockUnitRepository,
            allocationRepository,
            cutRepository,
            new BatchPrimaryMeasureService(),
            Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), ZoneOffset.UTC));
    lot =
        Batch.builder()
            .productType(ProductType.FABRIC)
            .batchCode("P1")
            .unit("M")
            .quantity(new BigDecimal("300"))
            .status(BatchStatus.AVAILABLE)
            .build();
    lot.setId(UUID.randomUUID());
    lot.setTenantId(TENANT);
    piece = piece(StockUnitStatus.AVAILABLE, 4L);
    when(batchRepository.findByIdAndTenantIdForUpdate(lot.getId(), TENANT))
        .thenReturn(Optional.of(lot));
    when(stockUnitRepository.findByTenantIdAndBatchIdInAndIsActiveTrue(eq(TENANT), anyList()))
        .thenReturn(List.of(piece));
    when(allocationRepository.findByTenantIdAndStockUnitIdInAndStatus(
            eq(TENANT), anyList(), eq(StockUnitAllocationStatus.ACTIVE)))
        .thenReturn(List.of());
    when(reservationRepository.save(any(BatchReservation.class)))
        .thenAnswer(
            call -> {
              BatchReservation saved = call.getArgument(0);
              saved.setId(UUID.randomUUID());
              return saved;
            });
  }

  @Test
  @DisplayName("Held: lot counter, piece status and the named allocation move together")
  void allocates() {
    PieceAllocationPort.AllocationOutcome outcome = service.allocate(request(4L));

    assertThat(outcome.allocated()).isTrue();
    assertThat(outcome.allocatedPieces()).isEqualTo(1);
    assertThat(piece.getStatus()).isEqualTo(StockUnitStatus.RESERVED);
    assertThat(lot.getReservedQuantity()).isEqualByComparingTo("100");
    verify(allocationRepository).save(any(StockUnitAllocation.class));
  }

  @Test
  @DisplayName("S10: a piece already held for another line is refused; nothing changes")
  void takenPieceRefused() {
    StockUnitAllocation other =
        StockUnitAllocation.allocate(
            piece.getId(),
            lot.getId(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("100"),
            "M",
            ACTOR,
            Instant.now());
    when(allocationRepository.findByTenantIdAndStockUnitIdInAndStatus(
            eq(TENANT), anyList(), eq(StockUnitAllocationStatus.ACTIVE)))
        .thenReturn(List.of(other));

    PieceAllocationPort.AllocationOutcome outcome = service.allocate(request(4L));

    assertThat(outcome.allocated()).isFalse();
    assertThat(outcome.failureCode()).isEqualTo(PieceAllocationPort.Failures.PIECE_TAKEN);
    assertNothingChanged();
  }

  @Test
  @DisplayName("R22: a piece that changed since the proposal is refused")
  void changedPieceRefused() {
    PieceAllocationPort.AllocationOutcome outcome = service.allocate(request(3L));

    assertThat(outcome.failureCode()).isEqualTo(PieceAllocationPort.Failures.PIECE_CHANGED);
    assertNothingChanged();
  }

  @Test
  @DisplayName(
      "A06: a cut recorded since the proposal changes the piece; the whole piece is refused")
  void cutSinceProposalRefused() {
    StockUnitCut cut =
        StockUnitCut.record(
            piece.getId(),
            new BigDecimal("15"),
            new BigDecimal("242"),
            "M",
            UUID.randomUUID(),
            Instant.parse("2026-09-27T10:00:00Z"));
    when(cutRepository.findByTenantIdAndStockUnitIdInAndIsActiveTrue(eq(TENANT), anyList()))
        .thenReturn(List.of(cut));

    PieceAllocationPort.AllocationOutcome outcome = service.allocate(request(piece.getVersion()));

    assertThat(outcome.failureCode()).isEqualTo(PieceAllocationPort.Failures.PIECE_CHANGED);
    assertNothingChanged();
  }

  @Test
  @DisplayName("A piece reserved by another flow is taken")
  void reservedPieceRefused() {
    piece = piece(StockUnitStatus.RESERVED, 4L);
    when(stockUnitRepository.findByTenantIdAndBatchIdInAndIsActiveTrue(eq(TENANT), anyList()))
        .thenReturn(List.of(piece));

    assertThat(service.allocate(request(4L)).failureCode())
        .isEqualTo(PieceAllocationPort.Failures.PIECE_TAKEN);
    assertNothingChanged();
  }

  @Test
  @DisplayName("The lot's free quantity is checked too; a short lot refuses without changes")
  void lotShortRefused() {
    lot.setReservedQuantity(new BigDecimal("250"));

    assertThat(service.allocate(request(4L)).failureCode())
        .isEqualTo(PieceAllocationPort.Failures.LOT_QUANTITY_INSUFFICIENT);
    assertNothingChanged();
  }

  private void assertNothingChanged() {
    verify(batchRepository, never()).save(any());
    verify(stockUnitRepository, never()).save(any());
    verify(allocationRepository, never()).save(any());
    verify(reservationRepository, times(0)).save(any());
  }

  private PieceAllocationPort.AllocationRequest request(Long version) {
    return new PieceAllocationPort.AllocationRequest(
        TENANT,
        ORDER,
        LINE,
        ACTOR,
        List.of(new PieceAllocationPort.PieceRef(piece.getId(), lot.getId(), version)));
  }

  private StockUnit piece(StockUnitStatus status, long version) {
    StockUnit unit =
        StockUnit.builder()
            .batchId(lot.getId())
            .barcode("R-1")
            .productType(ProductType.FABRIC)
            .unit("KG")
            .currentWeight(new BigDecimal("30"))
            .initialWeight(new BigDecimal("30"))
            .length(new BigDecimal("100"))
            .lengthUnit("M")
            .status(status)
            .qualityDisposition(QualityDisposition.RELEASED)
            .build();
    unit.setId(piece == null ? UUID.randomUUID() : piece.getId());
    unit.setTenantId(TENANT);
    unit.setVersion(version);
    return unit;
  }
}
