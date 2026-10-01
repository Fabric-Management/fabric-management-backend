package com.fabricmanagement.production.core.batch.api.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.qualitygrade.api.query.QualityGradeQueryService;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.OfferableStockSummary;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalStock;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalStockQuery;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.WidthEvidence;
import com.fabricmanagement.production.core.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchFinishedWidthMeasurement;
import com.fabricmanagement.production.core.batch.domain.BatchReservation;
import com.fabricmanagement.production.core.batch.domain.BatchStatus;
import com.fabricmanagement.production.core.batch.domain.LotCompatibilityConfirmation;
import com.fabricmanagement.production.core.batch.infra.repository.BatchFinishedWidthMeasurementRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchReservationRepository;
import com.fabricmanagement.production.core.batch.infra.repository.LotCompatibilityConfirmationRepository;
import com.fabricmanagement.production.core.stockunit.domain.PackageType;
import com.fabricmanagement.production.core.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.core.stockunit.domain.StockUnit;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCut;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitAllocationRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitCutRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** SOI D2 — piece-level evidence: missing evidence is UNKNOWN, never "suitable" or "none". */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProposalStockQueryServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID PRODUCT = UUID.randomUUID();
  private static final UUID COLOR = UUID.randomUUID();
  private static final UUID CUSTOMER = UUID.randomUUID();

  @Mock private BatchRepository batchRepository;
  @Mock private StockUnitRepository stockUnitRepository;
  @Mock private StockUnitCutRepository cutRepository;
  @Mock private StockUnitAllocationRepository allocationRepository;
  @Mock private BatchReservationRepository reservationRepository;
  @Mock private BatchFinishedWidthMeasurementRepository widthRepository;
  @Mock private LotCompatibilityConfirmationRepository compatibilityRepository;
  @Mock private QualityGradeQueryService gradeQueryService;

  private ProposalStockQueryService service;

  @BeforeEach
  void setUp() {
    service =
        new ProposalStockQueryService(
            batchRepository,
            stockUnitRepository,
            cutRepository,
            allocationRepository,
            reservationRepository,
            widthRepository,
            compatibilityRepository,
            gradeQueryService,
            new BatchPrimaryMeasureService());
    when(cutRepository.findByTenantIdAndStockUnitIdInAndIsActiveTrue(any(), any()))
        .thenReturn(List.of());
    when(widthRepository.findByTenantIdAndBatchIdInAndIsActiveTrue(any(), any()))
        .thenReturn(List.of());
    when(compatibilityRepository.findByTenantIdAndRevokedAtIsNullAndIsActiveTrue(TENANT))
        .thenReturn(List.of());
    when(gradeQueryService.findReferencesByIds(any())).thenReturn(List.of());
    when(reservationRepository.findByTenantIdAndBatchIdInAndIsActiveTrueOrderById(any(), any()))
        .thenReturn(List.of());
    when(allocationRepository.findByTenantIdAndBatchIdInAndStatus(any(), any(), any()))
        .thenReturn(List.of());
  }

  @Test
  @DisplayName(
      "S09: a released, measured, available piece is eligible; each gap has its own reason")
  void pieceStatesCarryReasons() {
    Batch lot = lot(COLOR, Instant.parse("2026-01-10T00:00:00Z"));
    StockUnit eligible = piece(lot, "50");
    StockUnit reserved =
        piece(lot, "40", StockUnitStatus.RESERVED, QualityDisposition.RELEASED, false);
    StockUnit partial =
        piece(lot, "30", StockUnitStatus.PARTIAL, QualityDisposition.RELEASED, false);
    StockUnit pendingQc =
        piece(lot, "20", StockUnitStatus.AVAILABLE, QualityDisposition.PENDING_INSPECTION, false);
    StockUnit unmeasured = piece(lot, null);
    StockUnit flagged =
        piece(lot, "10", StockUnitStatus.AVAILABLE, QualityDisposition.RELEASED, true);
    stock(lot, eligible, reserved, partial, pendingQc, unmeasured, flagged);

    Map<UUID, ProposalPiece> pieces = byId(onlyLot(query(null, null)));

    assertThat(pieces.get(eligible.getId()).state()).isEqualTo(PieceState.ELIGIBLE);
    assertThat(pieces.get(eligible.getId()).canonicalMeasure()).isEqualByComparingTo("50");
    assertThat(pieces.get(reserved.getId()).reason()).isEqualTo("ALLOCATED");
    assertThat(pieces.get(reserved.getId()).state()).isEqualTo(PieceState.EXCLUDED);
    assertThat(pieces.get(partial.getId()).reason()).isEqualTo("REMAINING_LENGTH_UNVERIFIED");
    assertThat(pieces.get(pendingQc.getId()).state()).isEqualTo(PieceState.UNKNOWN);
    assertThat(pieces.get(unmeasured.getId()).reason()).isEqualTo("MEASURE_MISSING");
    assertThat(pieces.get(flagged.getId()).reason()).isEqualTo("FLAGGED");
  }

  @Test
  @DisplayName("A06: a cut piece stays unknown until its remaining length is verified")
  void unverifiedCutIsUnknown() {
    Batch lot = lot(COLOR, null);
    StockUnit cutPiece = piece(lot, "35");
    stock(lot, cutPiece);
    StockUnitCut cut =
        StockUnitCut.record(
            cutPiece.getId(),
            new BigDecimal("15"),
            new BigDecimal("35"),
            "M",
            UUID.randomUUID(),
            Instant.now());
    when(cutRepository.findByTenantIdAndStockUnitIdInAndIsActiveTrue(eq(TENANT), anyList()))
        .thenReturn(List.of(cut));

    ProposalPiece unverified = onlyLot(query(null, null)).pieces().get(0);
    assertThat(unverified.state()).isEqualTo(PieceState.UNKNOWN);
    assertThat(unverified.reason()).isEqualTo("CUT_REMAINING_UNVERIFIED");

    cut.verifyRemaining(UUID.randomUUID(), Instant.now());
    assertThat(onlyLot(query(null, null)).pieces().get(0).state()).isEqualTo(PieceState.ELIGIBLE);
  }

  @Test
  @DisplayName(
      "A06: a verified cut with nothing left excludes the piece; its old length is never offered")
  void consumedCutIsExcluded() {
    Batch lot = lot(COLOR, null);
    StockUnit cutPiece = piece(lot, "35");
    stock(lot, cutPiece);
    StockUnitCut cut =
        StockUnitCut.record(
            cutPiece.getId(),
            new BigDecimal("35"),
            BigDecimal.ZERO,
            "M",
            UUID.randomUUID(),
            Instant.now());
    cut.verifyRemaining(UUID.randomUUID(), Instant.now());
    when(cutRepository.findByTenantIdAndStockUnitIdInAndIsActiveTrue(eq(TENANT), anyList()))
        .thenReturn(List.of(cut));

    ProposalPiece consumed = onlyLot(query(null, null)).pieces().get(0);
    assertThat(consumed.state()).isEqualTo(PieceState.EXCLUDED);
    assertThat(consumed.reason()).isEqualTo("CUT_CONSUMED");
  }

  @Test
  @DisplayName("IK-08: width requirement — unmeasured lot unknown, other width dropped, match kept")
  void finishedWidthEvidence() {
    Batch unmeasured = lot(COLOR, null);
    Batch measured155 = lot(COLOR, null);
    Batch measured160 = lot(COLOR, null);
    StockUnit a = piece(unmeasured, "50");
    StockUnit b = piece(measured155, "50");
    StockUnit c = piece(measured160, "50");
    when(batchRepository.findByTenantIdAndProductIdAndIsActiveTrue(TENANT, PRODUCT))
        .thenReturn(List.of(unmeasured, measured155, measured160));
    when(stockUnitRepository.findByTenantIdAndBatchIdInAndIsActiveTrue(eq(TENANT), anyList()))
        .thenReturn(List.of(a, b, c));
    when(widthRepository.findByTenantIdAndBatchIdInAndIsActiveTrue(eq(TENANT), anyList()))
        .thenReturn(
            List.of(width(measured155.getId(), "155.00"), width(measured160.getId(), "160.00")));

    ProposalStock stock = service.find(query(new BigDecimal("160"), "cm"));

    Map<UUID, ProposalLot> lots =
        stock.lots().stream().collect(Collectors.toMap(ProposalLot::batchId, Function.identity()));
    assertThat(lots).doesNotContainKey(measured155.getId());
    assertThat(lots.get(unmeasured.getId()).widthEvidence()).isEqualTo(WidthEvidence.UNKNOWN);
    assertThat(lots.get(unmeasured.getId()).pieces().get(0).reason())
        .isEqualTo("WIDTH_NOT_MEASURED");
    assertThat(lots.get(measured160.getId()).widthEvidence()).isEqualTo(WidthEvidence.MATCH);
    assertThat(lots.get(measured160.getId()).pieces().get(0).state())
        .isEqualTo(PieceState.ELIGIBLE);
  }

  @Test
  @DisplayName("Lots of another colour are not candidates")
  void colourMustMatch() {
    Batch other = lot(UUID.randomUUID(), null);
    when(batchRepository.findByTenantIdAndProductIdAndIsActiveTrue(TENANT, PRODUCT))
        .thenReturn(List.of(other));

    assertThat(service.find(query(null, null)).lots()).isEmpty();
  }

  @Test
  @DisplayName("IK-15: a lot-level reservation without pieces makes the free pieces unknown")
  void lotLevelReservationWithoutPiecesIsUnknown() {
    Batch lot = lot(COLOR, null);
    StockUnit free = piece(lot, "50");
    stock(lot, free);
    BatchReservation workOrder =
        BatchReservation.create(
            TENANT, lot.getId(), UUID.randomUUID(), "WORK_ORDER", new BigDecimal("20"), "M", null);
    when(reservationRepository.findByTenantIdAndBatchIdInAndIsActiveTrueOrderById(
            eq(TENANT), anyList()))
        .thenReturn(List.of(workOrder));

    ProposalPiece piece = onlyLot(query(null, null)).pieces().get(0);
    assertThat(piece.state()).isEqualTo(PieceState.UNKNOWN);
    assertThat(piece.reason()).isEqualTo("LOT_RESERVED_WITHOUT_PIECES");
  }

  @Test
  @DisplayName("A04: only effective confirmations for this customer (or any customer) form groups")
  void compatibilityGroups() {
    Batch first = lot(COLOR, null);
    Batch second = lot(COLOR, null);
    when(batchRepository.findByTenantIdAndProductIdAndIsActiveTrue(TENANT, PRODUCT))
        .thenReturn(List.of(first, second));
    when(stockUnitRepository.findByTenantIdAndBatchIdInAndIsActiveTrue(eq(TENANT), anyList()))
        .thenReturn(List.of(piece(first, "50"), piece(second, "50")));
    LotCompatibilityConfirmation general =
        LotCompatibilityConfirmation.confirm(
            List.of(first.getId(), second.getId()),
            null,
            "same recipe",
            UUID.randomUUID(),
            Instant.now());
    LotCompatibilityConfirmation otherCustomer =
        LotCompatibilityConfirmation.confirm(
            List.of(first.getId(), second.getId()),
            UUID.randomUUID(),
            "only for them",
            UUID.randomUUID(),
            Instant.now());
    when(compatibilityRepository.findByTenantIdAndRevokedAtIsNullAndIsActiveTrue(TENANT))
        .thenReturn(List.of(general, otherCustomer));

    List<Set<UUID>> groups = service.find(query(null, null)).confirmedCompatibleGroups();

    assertThat(groups).containsExactly(Set.of(first.getId(), second.getId()));
  }

  @Test
  void offerableSummaryBatchesProductsAndKeepsMissingMeasurementsUnknown() {
    Batch first = lot(COLOR, null);
    Batch second = lot(UUID.randomUUID(), null);
    UUID noStockProduct = UUID.randomUUID();
    StockUnit measured = stockPiece(first, "40", "10", StockUnitStatus.AVAILABLE, false);
    StockUnit missingWeight = stockPiece(second, "60", null, StockUnitStatus.AVAILABLE, false);
    StockUnit flagged = stockPiece(second, "20", "5", StockUnitStatus.AVAILABLE, true);
    StockUnit reserved = stockPiece(first, "30", "4", StockUnitStatus.RESERVED, false);
    when(batchRepository.findByTenantIdAndProductIdInAndIsActiveTrueOrderById(
            TENANT, List.of(PRODUCT, noStockProduct)))
        .thenReturn(List.of(first, second));
    when(stockUnitRepository.findByTenantIdAndBatchIdInAndIsActiveTrue(
            TENANT, List.of(first.getId(), second.getId())))
        .thenReturn(List.of(measured, missingWeight, flagged, reserved));

    Map<UUID, OfferableStockSummary> summaries =
        service.offerableSummaries(TENANT, List.of(PRODUCT, noStockProduct));

    assertThat(summaries).doesNotContainKey(noStockProduct);
    OfferableStockSummary summary = summaries.get(PRODUCT);
    assertThat(summary.packages()).containsEntry(PackageType.ROLL, 2L).hasSize(1);
    assertThat(summary.metres()).isEqualByComparingTo("100");
    assertThat(summary.kilograms()).isNull();
    assertThat(summary.unknownPieces()).isEqualTo(1);
    verify(batchRepository)
        .findByTenantIdAndProductIdInAndIsActiveTrueOrderById(
            TENANT, List.of(PRODUCT, noStockProduct));
  }

  @Test
  void offerableSummaryDoesNotReuseWeightRecordedBeforeALengthCut() {
    Batch lot = lot(COLOR, null);
    StockUnit remnant = stockPiece(lot, "35", "8", StockUnitStatus.AVAILABLE, false);
    StockUnitCut cut =
        StockUnitCut.record(
            remnant.getId(),
            new BigDecimal("20"),
            new BigDecimal("15"),
            "M",
            UUID.randomUUID(),
            Instant.now());
    cut.verifyRemaining(UUID.randomUUID(), Instant.now());
    remnant.recordLength(new BigDecimal("15"), "M");
    when(batchRepository.findByTenantIdAndProductIdInAndIsActiveTrueOrderById(
            TENANT, List.of(PRODUCT)))
        .thenReturn(List.of(lot));
    when(stockUnitRepository.findByTenantIdAndBatchIdInAndIsActiveTrue(
            TENANT, List.of(lot.getId())))
        .thenReturn(List.of(remnant));
    when(cutRepository.findByTenantIdAndStockUnitIdInAndIsActiveTrue(
            TENANT, List.of(remnant.getId())))
        .thenReturn(List.of(cut));

    OfferableStockSummary summary =
        service.offerableSummaries(TENANT, List.of(PRODUCT)).get(PRODUCT);

    assertThat(summary.packages()).containsEntry(PackageType.ROLL, 1L);
    assertThat(summary.metres()).isEqualByComparingTo("15");
    assertThat(summary.kilograms()).isNull();
  }

  private ProposalStockQuery query(BigDecimal width, String widthUnit) {
    return new ProposalStockQuery(TENANT, PRODUCT, COLOR, width, widthUnit, CUSTOMER);
  }

  private ProposalLot onlyLot(ProposalStockQuery query) {
    List<ProposalLot> lots = service.find(query).lots();
    assertThat(lots).hasSize(1);
    return lots.get(0);
  }

  private void stock(Batch lot, StockUnit... units) {
    when(batchRepository.findByTenantIdAndProductIdAndIsActiveTrue(TENANT, PRODUCT))
        .thenReturn(List.of(lot));
    when(stockUnitRepository.findByTenantIdAndBatchIdInAndIsActiveTrue(eq(TENANT), anyList()))
        .thenReturn(List.of(units));
  }

  private static Map<UUID, ProposalPiece> byId(ProposalLot lot) {
    return lot.pieces().stream()
        .collect(Collectors.toMap(ProposalPiece::stockUnitId, Function.identity()));
  }

  private static Batch lot(UUID colorId, Instant productionDate) {
    Batch batch =
        Batch.builder()
            .productId(PRODUCT)
            .colorId(colorId)
            .productType(ProductType.FABRIC)
            .batchCode("LOT-" + UUID.randomUUID().toString().substring(0, 6))
            .unit("M")
            .status(BatchStatus.AVAILABLE)
            .productionDate(productionDate)
            .build();
    batch.setId(UUID.randomUUID());
    batch.setTenantId(TENANT);
    return batch;
  }

  private static StockUnit piece(Batch lot, String lengthMetres) {
    return piece(lot, lengthMetres, StockUnitStatus.AVAILABLE, QualityDisposition.RELEASED, false);
  }

  private static StockUnit piece(
      Batch lot,
      String lengthMetres,
      StockUnitStatus status,
      QualityDisposition disposition,
      boolean flagged) {
    StockUnit unit =
        StockUnit.builder()
            .batchId(lot.getId())
            .barcode("R-" + UUID.randomUUID().toString().substring(0, 6))
            .productType(ProductType.FABRIC)
            .unit("KG")
            .length(lengthMetres == null ? null : new BigDecimal(lengthMetres))
            .lengthUnit(lengthMetres == null ? null : "M")
            .status(status)
            .qualityDisposition(disposition)
            .flagged(flagged)
            .build();
    unit.setId(UUID.randomUUID());
    unit.setTenantId(TENANT);
    return unit;
  }

  private static BatchFinishedWidthMeasurement width(UUID batchId, String value) {
    return BatchFinishedWidthMeasurement.record(
        batchId, new BigDecimal(value), "CM", null, UUID.randomUUID(), Instant.now());
  }

  private static StockUnit stockPiece(
      Batch lot, String metres, String kg, StockUnitStatus status, boolean flagged) {
    StockUnit unit =
        StockUnit.builder()
            .batchId(lot.getId())
            .barcode("R-" + UUID.randomUUID().toString().substring(0, 6))
            .productType(ProductType.FABRIC)
            .packageType(PackageType.ROLL)
            .unit("KG")
            .currentWeight(kg == null ? null : new BigDecimal(kg))
            .length(new BigDecimal(metres))
            .lengthUnit("M")
            .status(status)
            .qualityDisposition(QualityDisposition.RELEASED)
            .flagged(flagged)
            .build();
    unit.setId(UUID.randomUUID());
    unit.setTenantId(TENANT);
    return unit;
  }
}
