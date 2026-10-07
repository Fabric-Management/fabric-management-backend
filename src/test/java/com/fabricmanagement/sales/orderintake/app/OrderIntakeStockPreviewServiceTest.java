package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalStock;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.WidthEvidence;
import com.fabricmanagement.production.core.batch.api.query.QuoteHoldQueryService;
import com.fabricmanagement.production.core.batch.api.query.QuoteHoldQueryService.HeldPieceView;
import com.fabricmanagement.production.core.batch.api.query.QuoteHoldQueryService.LotIntentView;
import com.fabricmanagement.production.core.batch.api.query.QuoteHoldQueryService.QuoteHolds;
import com.fabricmanagement.production.core.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.sales.orderintake.domain.QuantityProposal;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult.EvaluationStatus;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewDto;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewDto.FreeIncompleteReason;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewDto.HoldStatus;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewDto.OptionLotCheck;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewRequest;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerToneAcceptanceRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.QuantityProposalRepository;
import com.fabricmanagement.sales.quote.app.QuoteScopeQueryService;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * STOCK-PREVIEW-1 A3/A4 on the service level, with the real {@link QuantityEvaluationService} and
 * {@link QuantityEvaluator}: parity with a saved line, the lot view from the same stock read, quote
 * intents and piece holds, the two separate option checks and the undeterminable free quantity.
 * Data is fictional.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderIntakeStockPreviewServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-07T09:00:00Z");
  private static final LocalDate EXPIRES = LocalDate.parse("2026-10-21");

  private final UUID tenantId = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();
  private final UUID colorId = UUID.randomUUID();
  private final UUID customerId = UUID.randomUUID();
  private final BatchPrimaryMeasureService measures = new BatchPrimaryMeasureService();

  @Mock private OrderIntakeAccess access;
  @Mock private ProductSalesDefinitionQueryService productDefinitions;
  @Mock private ProposalStockQueryService stockQuery;
  @Mock private CustomerToneAcceptanceRepository toneAcceptances;
  @Mock private QuantityProposalRepository proposals;
  @Mock private QuoteHoldQueryService quoteHolds;
  @Mock private QuoteScopeQueryService quoteScopes;

  private QuantityEvaluationService evaluations;
  private OrderIntakeStockPreviewService service;
  private SalesOrder order;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    evaluations =
        new QuantityEvaluationService(
            access,
            productDefinitions,
            stockQuery,
            toneAcceptances,
            proposals,
            Clock.fixed(NOW, ZoneOffset.UTC));
    service = new OrderIntakeStockPreviewService(evaluations, stockQuery, quoteHolds, quoteScopes);
    order = SalesOrder.builder().tradingPartnerId(customerId).orderNumber("SO-1").build();
    order.setId(UUID.randomUUID());

    when(productDefinitions.find(tenantId, productId))
        .thenReturn(Optional.of(definition(ProductType.FABRIC)));
    when(stockQuery.measureFor(ProductType.FABRIC)).thenReturn(Optional.of(PrimaryMeasure.LENGTH));
    when(stockQuery.canonicalUnit(any()))
        .thenAnswer(call -> measures.canonicalUnit(call.getArgument(0)));
    when(stockQuery.toCanonical(any(), any(), any()))
        .thenAnswer(
            call ->
                measures.toCanonical(
                    call.getArgument(0), call.getArgument(1), call.getArgument(2)));
    when(stockQuery.fromCanonical(any(), any(), any()))
        .thenAnswer(
            call ->
                measures.fromCanonical(
                    call.getArgument(0), call.getArgument(1), call.getArgument(2)));
    when(stockQuery.find(any())).thenReturn(new ProposalStock(List.of(), List.of()));
    when(toneAcceptances.findByTenantIdAndCustomerIdAndIsActiveTrue(any(), any()))
        .thenReturn(List.of());
    when(proposals.save(any(QuantityProposal.class))).thenAnswer(call -> call.getArgument(0));
    when(quoteHolds.find(any())).thenReturn(holds(List.of(), List.of()));
    // Every quote is readable unless a test says otherwise.
    when(quoteScopes.findReadableQuoteIds(any(), any(), any()))
        .thenAnswer(
            call -> {
              Collection<UUID> quoteIds = call.getArgument(2);
              return quoteIds == null ? Set.of() : Set.copyOf(quoteIds);
            });
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  // ── Parity with a saved line (A1) ────────────────────────────────────────

  @Test
  void theEvaluationIsTheOneASavedLineWithTheSameInputsIsStoredWith() {
    stock(
        lot(UUID.randomUUID(), "LOT-A", eligible("100"), eligible("100"), eligible("100")),
        lot(UUID.randomUUID(), "LOT-B", eligible("100"), eligible("120")));
    SalesOrderLine line = savedLine("200", "M", false, null, null);

    OrderIntakeStockPreviewDto.Evaluation stored = storedEvaluation(line);
    OrderIntakeStockPreviewDto preview =
        service.preview(request("200", "M", false, null, null), actor);

    assertThat(preview.evaluation()).isEqualTo(stored);
    assertThat(preview.evaluation().options()).isNotEmpty();
  }

  @Test
  void parityHoldsWithASingleLotRequirementAndAnAgreedTolerance() {
    stock(
        lot(UUID.randomUUID(), "LOT-A", eligible("100"), eligible("100"), eligible("100")),
        lot(UUID.randomUUID(), "LOT-B", eligible("100"), eligible("120")));
    SalesOrderLine line = savedLine("250", "M", true, "5", "5");

    OrderIntakeStockPreviewDto.Evaluation stored = storedEvaluation(line);
    OrderIntakeStockPreviewDto preview =
        service.preview(request("250", "M", true, "5", "5"), actor);

    assertThat(preview.evaluation()).isEqualTo(stored);
  }

  @Test
  void anUnknownMeasureIsTheSavedLinesUnknownAndReadsNoStock() {
    when(stockQuery.measureFor(ProductType.FABRIC)).thenReturn(Optional.empty());
    SalesOrderLine line = savedLine("250", "M", false, null, null);

    OrderIntakeStockPreviewDto.Evaluation stored = storedEvaluation(line);
    OrderIntakeStockPreviewDto preview =
        service.preview(request("250", "M", false, null, null), actor);

    assertThat(preview.evaluation()).isEqualTo(stored);
    assertThat(preview.evaluation().status()).isEqualTo(EvaluationStatus.UNKNOWN);
    assertThat(preview.evaluation().unknownReasons()).containsExactly("MEASURE_UNKNOWN");
    assertThat(preview.canonicalUnit()).isEqualTo("?");
    assertThat(preview.lots()).isEmpty();
    assertThat(preview.totals().free()).isNull();
    assertThat(preview.totals().notCoveredByReadyStock()).isNull();
    assertThat(preview.totals().freeComplete()).isFalse();
    verify(stockQuery, never()).find(any());
    verify(quoteHolds, never()).find(any());
  }

  @Test
  void aKgLineForAMetreProductIsUnitNotConvertible_andTheLotsStayReadableInMetres() {
    stock(lot(UUID.randomUUID(), "LOT-A", eligible("100"), eligible("100"), eligible("100")));
    SalesOrderLine line = savedLine("120", "KG", false, null, null);

    OrderIntakeStockPreviewDto.Evaluation stored = storedEvaluation(line);
    OrderIntakeStockPreviewDto preview =
        service.preview(request("120", "KG", false, null, null), actor);

    assertThat(preview.evaluation()).isEqualTo(stored);
    assertThat(preview.evaluation().status()).isEqualTo(EvaluationStatus.UNKNOWN);
    assertThat(preview.evaluation().unknownReasons()).containsExactly("UNIT_NOT_CONVERTIBLE");
    assertThat(preview.unit()).isEqualTo("KG");
    assertThat(preview.canonicalUnit()).isEqualTo("M");
    OrderIntakeStockPreviewDto.Lot lot = preview.lots().getFirst();
    assertThat(lot.eligible().canonical()).isEqualByComparingTo("300");
    assertThat(lot.eligible().inLineUnit()).isNull();
    assertThat(preview.totals().free().canonical()).isEqualByComparingTo("300");
    assertThat(preview.totals().free().inLineUnit()).isNull();
    assertThat(preview.totals().notCoveredByReadyStock()).isNull();
  }

  @Test
  void theSavedPathStillSeedsAnUnknownFingerprintWithTheLineId() {
    stock(lot(UUID.randomUUID(), "LOT-A", eligible("100")));
    SalesOrderLine line = savedLine("120", "KG", false, null, null);
    when(access.writableOrder(order.getId(), actor)).thenReturn(order);
    when(access.line(order, line.getId())).thenReturn(line);

    evaluations.evaluate(order.getId(), line.getId(), actor);

    ArgumentCaptor<QuantityProposal> stored = ArgumentCaptor.forClass(QuantityProposal.class);
    verify(proposals).save(stored.capture());
    assertThat(stored.getValue().getEvidenceFingerprint())
        .isEqualTo(
            QuantityEvaluator.sha256(
                line.getId() + "|UNIT_NOT_CONVERTIBLE|" + line.getRequestedQty()));
    assertThat(stored.getValue().getProductId()).isEqualTo(productId);
  }

  @Test
  void stockIsReadOncePerPreview() {
    stock(lot(UUID.randomUUID(), "LOT-A", eligible("100"), eligible("100")));

    service.preview(request("100", "M", false, null, null), actor);

    verify(stockQuery, times(1)).find(any());
  }

  @Test
  void aCentimetreLineIsConverted() {
    stock(lot(UUID.randomUUID(), "LOT-A", eligible("100"), eligible("200")));

    OrderIntakeStockPreviewDto preview =
        service.preview(request("25000", "CM", false, null, null), actor);

    assertThat(preview.evaluation().status()).isNotEqualTo(EvaluationStatus.UNKNOWN);
    OrderIntakeStockPreviewDto.Lot lot = preview.lots().getFirst();
    assertThat(lot.eligible().canonical()).isEqualByComparingTo("300");
    assertThat(lot.eligible().inLineUnit()).isEqualByComparingTo("30000");
    assertThat(preview.totals().notCoveredByReadyStock().canonical()).isEqualByComparingTo("0");
    assertThat(preview.totals().notCoveredByReadyStock().inLineUnit()).isEqualByComparingTo("0");
  }

  @Test
  void singleLotRequirementAndTolerancesReachTheEvaluator() {
    UUID lotA = UUID.randomUUID();
    UUID lotB = UUID.randomUUID();
    stock(lot(lotA, "LOT-A", eligible("100")), lot(lotB, "LOT-B", eligible("100")));

    OrderIntakeStockPreviewDto anyLot =
        service.preview(request("200", "M", false, null, null), actor);
    OrderIntakeStockPreviewDto singleLot =
        service.preview(request("200", "M", true, null, null), actor);

    assertThat(anyLot.evaluation().options())
        .anySatisfy(option -> assertThat(option.lots()).hasSize(2));
    assertThat(singleLot.evaluation().options())
        .allSatisfy(option -> assertThat(option.lots()).hasSizeLessThanOrEqualTo(1));

    stock(lot(lotA, "LOT-A", eligible("100"), eligible("100"), eligible("100")));
    OrderIntakeStockPreviewDto free =
        service.preview(request("250", "M", false, null, null), actor);
    OrderIntakeStockPreviewDto agreed =
        service.preview(request("250", "M", false, "5", "5"), actor);

    assertThat(free.evaluation().optionsOutsideAgreedTolerance()).isZero();
    assertThat(agreed.evaluation().optionsOutsideAgreedTolerance()).isPositive();
  }

  // ── Quote intents ────────────────────────────────────────────────────────

  @Test
  void intentsLowerFreeNeverBelowZero_andLeaveTheOptionsUnchanged() {
    UUID lotA = UUID.randomUUID();
    UUID lotB = UUID.randomUUID();
    stock(
        lot(lotA, "LOT-A", eligible("100"), eligible("100"), eligible("100")),
        lot(lotB, "LOT-B", eligible("100")));
    OrderIntakeStockPreviewDto withoutHolds =
        service.preview(request("250", "M", false, null, null), actor);

    when(quoteHolds.find(any()))
        .thenReturn(
            holds(
                List.of(
                    intent(lotA, UUID.randomUUID(), UUID.randomUUID(), "80", "80"),
                    intent(lotB, UUID.randomUUID(), UUID.randomUUID(), "500", "500")),
                List.of()));
    OrderIntakeStockPreviewDto preview =
        service.preview(request("250", "M", false, null, null), actor);

    assertThat(lotView(preview, lotA).heldByQuotes().canonical()).isEqualByComparingTo("80");
    assertThat(lotView(preview, lotA).free().canonical()).isEqualByComparingTo("220");
    assertThat(lotView(preview, lotB).free().canonical()).isEqualByComparingTo("0");
    assertThat(preview.totals().free().canonical()).isEqualByComparingTo("220");
    assertThat(preview.totals().heldByQuotes().canonical()).isEqualByComparingTo("580");
    assertThat(preview.totals().notCoveredByReadyStock().canonical()).isEqualByComparingTo("30");
    assertThat(preview.totals().freeComplete()).isTrue();
    assertThat(preview.evaluation()).isEqualTo(withoutHolds.evaluation());
  }

  @Test
  void anUnconvertibleIntentMakesFreeUndeterminable_neverAGuessedNumber() {
    UUID lotA = UUID.randomUUID();
    stock(lot(lotA, "LOT-A", eligible("100"), eligible("100"), eligible("100")));
    when(quoteHolds.find(any()))
        .thenReturn(
            holds(
                List.of(
                    intent(lotA, UUID.randomUUID(), UUID.randomUUID(), "40", "40"),
                    intent(lotA, UUID.randomUUID(), UUID.randomUUID(), "12", null)),
                List.of()));

    OrderIntakeStockPreviewDto preview =
        service.preview(request("100", "M", false, null, null), actor);

    OrderIntakeStockPreviewDto.Lot lot = lotView(preview, lotA);
    assertThat(lot.free()).isNull();
    assertThat(lot.freeComplete()).isFalse();
    assertThat(lot.freeIncompleteReasons())
        .containsExactly(FreeIncompleteReason.UNCONVERTIBLE_INTENT);
    assertThat(lot.heldByQuotes().canonical()).isEqualByComparingTo("40");
    assertThat(preview.totals().free()).isNull();
    assertThat(preview.totals().notCoveredByReadyStock()).isNull();
    assertThat(preview.totals().freeComplete()).isFalse();
    assertThat(preview.totals().freeIncompleteReasons())
        .containsExactly(FreeIncompleteReason.UNCONVERTIBLE_INTENT);
    assertThat(preview.unconvertibleIntentCount()).isEqualTo(1);
    assertThat(preview.optionChecks()).isNotEmpty();
    assertThat(preview.optionChecks())
        .allSatisfy(
            check -> {
              assertThat(check.coveredByFreeStock()).isFalse();
              assertThat(check.lots()).allSatisfy(part -> assertThat(part.exceedsFree()).isNull());
            });
  }

  // ── The two separate option checks ───────────────────────────────────────

  @Test
  void checkA_aPartTakingMoreThanTheLotsFreeQuantityIsNotCoveredByFreeStock() {
    UUID lotA = UUID.randomUUID();
    stock(lot(lotA, "LOT-A", eligible("100"), eligible("100"), eligible("100")));
    when(quoteHolds.find(any()))
        .thenReturn(
            holds(
                List.of(intent(lotA, UUID.randomUUID(), UUID.randomUUID(), "250", "250")),
                List.of()));

    OrderIntakeStockPreviewDto preview =
        service.preview(request("100", "M", false, null, null), actor);

    assertThat(lotView(preview, lotA).free().canonical()).isEqualByComparingTo("50");
    assertThat(preview.optionChecks()).isNotEmpty();
    assertThat(preview.optionChecks())
        .allSatisfy(
            check -> {
              assertThat(check.coveredByFreeStock()).isFalse();
              OptionLotCheck part = check.lots().getFirst();
              assertThat(part.exceedsFree()).isTrue();
              assertThat(part.heldPieceIds()).isEmpty();
              assertThat(part.unverifiedHoldPieceIds()).isEmpty();
            });
  }

  @Test
  void checkB_aQuoteHeldPieceIsNotCoveredByFreeStock_evenWhenTheLotHasEnoughFree() {
    UUID lotA = UUID.randomUUID();
    ProposalPiece first = eligible("100");
    ProposalPiece second = eligible("100");
    ProposalPiece third = eligible("100");
    // Three pieces: 100 m is one whole piece leaving two, so the only option is EXACT. With two
    // pieces one would be left as a single remnant, and the evaluator offers ABOVE 200 m instead.
    stock(lot(lotA, "LOT-A", first, second, third));
    UUID quoteLine = UUID.randomUUID();
    when(quoteHolds.find(any()))
        .thenReturn(
            holds(
                List.of(intent(lotA, UUID.randomUUID(), quoteLine, "10", "10")),
                List.of(
                    hold(first.stockUnitId(), lotA, quoteLine),
                    hold(second.stockUnitId(), lotA, quoteLine),
                    hold(third.stockUnitId(), lotA, quoteLine))));

    OrderIntakeStockPreviewDto preview =
        service.preview(request("100", "M", false, null, null), actor);

    assertThat(lotView(preview, lotA).free().canonical()).isEqualByComparingTo("290");
    assertThat(lotView(preview, lotA).heldPieces())
        .allSatisfy(
            piece -> {
              assertThat(piece.holdStatus()).isEqualTo(HoldStatus.VERIFIED);
              assertThat(piece.quoteNumber()).isEqualTo("Q-0001");
              assertThat(piece.expiresAt()).isEqualTo(EXPIRES);
            });
    assertThat(preview.optionChecks()).isNotEmpty();
    assertThat(preview.optionChecks())
        .allSatisfy(
            check -> {
              assertThat(check.coveredByFreeStock()).isFalse();
              OptionLotCheck part = check.lots().getFirst();
              assertThat(part.exceedsFree()).isFalse();
              assertThat(part.heldPieceIds()).isNotEmpty();
              assertThat(part.unverifiedHoldPieceIds()).isEmpty();
            });
  }

  @Test
  void anOptionWithinFreeStockAndWithoutHeldPiecesIsCoveredByFreeStock() {
    UUID lotA = UUID.randomUUID();
    stock(lot(lotA, "LOT-A", eligible("100"), eligible("100"), eligible("100")));

    OrderIntakeStockPreviewDto preview =
        service.preview(request("100", "M", false, null, null), actor);

    assertThat(preview.optionChecks()).isNotEmpty();
    assertThat(preview.optionChecks())
        .allSatisfy(check -> assertThat(check.coveredByFreeStock()).isTrue());
  }

  // ── Piece holds: verified and unverified ─────────────────────────────────

  @Test
  void aHoldWithoutAnActiveIntentOnThatLotIsUnverified_evenWhenItsQuoteLineHasOneElsewhere() {
    UUID lotA = UUID.randomUUID();
    UUID lotB = UUID.randomUUID();
    ProposalPiece held = eligible("100");
    stock(
        lot(lotA, "LOT-A", held, eligible("100")),
        lot(lotB, "LOT-B", piece("50", PieceState.UNKNOWN)));
    UUID quoteLine = UUID.randomUUID();
    // The same quote line has a readable, active intent, but on another lot.
    when(quoteHolds.find(any()))
        .thenReturn(
            holds(
                List.of(intent(lotB, UUID.randomUUID(), quoteLine, "30", "30")),
                List.of(hold(held.stockUnitId(), lotA, quoteLine))));

    OrderIntakeStockPreviewDto preview =
        service.preview(request("200", "M", false, null, null), actor);

    OrderIntakeStockPreviewDto.Lot lot = lotView(preview, lotA);
    OrderIntakeStockPreviewDto.HeldPiece piece = lot.heldPieces().getFirst();
    assertThat(piece.holdStatus()).isEqualTo(HoldStatus.UNVERIFIED);
    assertThat(piece.pieceNo()).isEqualTo(held.pieceNo());
    assertThat(piece.quoteNumber()).isNull();
    assertThat(piece.marketerName()).isNull();
    assertThat(piece.expiresAt()).isNull();
    assertThat(lot.unverifiedHold().canonical()).isEqualByComparingTo("100");
    assertThat(lot.unverifiedHoldPieces()).isEqualTo(1);
    assertThat(lot.heldByQuotes().canonical()).isEqualByComparingTo("0");
    assertThat(lot.free()).isNull();
    assertThat(lot.freeComplete()).isFalse();
    assertThat(lot.freeIncompleteReasons()).containsExactly(FreeIncompleteReason.UNVERIFIED_HOLD);
    assertThat(preview.totals().free()).isNull();
    assertThat(preview.totals().notCoveredByReadyStock()).isNull();
    assertThat(preview.totals().unverifiedHold().canonical()).isEqualByComparingTo("100");
    assertThat(preview.totals().freeIncompleteReasons())
        .containsExactly(FreeIncompleteReason.UNVERIFIED_HOLD);

    // The only way to 200 is both pieces of LOT-A, so the option uses the unverified piece.
    assertThat(preview.optionChecks()).isNotEmpty();
    assertThat(preview.optionChecks())
        .allSatisfy(
            check -> {
              assertThat(check.coveredByFreeStock()).isFalse();
              OptionLotCheck part = partOf(check, lotA);
              assertThat(part.exceedsFree()).isNull();
              assertThat(part.unverifiedHoldPieceIds()).containsExactly(held.stockUnitId());
              assertThat(part.heldPieceIds()).isEmpty();
            });
  }

  @Test
  void aVerifiedHeldPieceIsNotSubtractedTwice() {
    UUID lotA = UUID.randomUUID();
    ProposalPiece held = eligible("100");
    stock(lot(lotA, "LOT-A", held, eligible("100"), eligible("100")));
    UUID quoteLine = UUID.randomUUID();
    when(quoteHolds.find(any()))
        .thenReturn(
            holds(
                List.of(intent(lotA, UUID.randomUUID(), quoteLine, "100", "100")),
                List.of(hold(held.stockUnitId(), lotA, quoteLine))));

    OrderIntakeStockPreviewDto preview =
        service.preview(request("100", "M", false, null, null), actor);

    OrderIntakeStockPreviewDto.Lot lot = lotView(preview, lotA);
    assertThat(lot.free().canonical()).isEqualByComparingTo("200");
    assertThat(lot.unverifiedHold().canonical()).isEqualByComparingTo("0");
    assertThat(lot.freeComplete()).isTrue();
    assertThat(lot.heldPieces().getFirst().holdStatus()).isEqualTo(HoldStatus.VERIFIED);
  }

  @Test
  void anUnreadableQuoteIsAnonymous_forIntentsAndHeldPieces() {
    UUID lotA = UUID.randomUUID();
    ProposalPiece held = eligible("100");
    stock(lot(lotA, "LOT-A", held, eligible("100")));
    UUID quoteLine = UUID.randomUUID();
    when(quoteHolds.find(any()))
        .thenReturn(
            holds(
                List.of(intent(lotA, UUID.randomUUID(), quoteLine, "10", "10")),
                List.of(hold(held.stockUnitId(), lotA, quoteLine))));
    // doReturn: re-stubbing with when(...) would call the default answer with null arguments.
    doReturn(Set.of()).when(quoteScopes).findReadableQuoteIds(any(), any(), any());

    OrderIntakeStockPreviewDto preview =
        service.preview(request("100", "M", false, null, null), actor);

    OrderIntakeStockPreviewDto.Intent intent = lotView(preview, lotA).intents().getFirst();
    assertThat(intent.quoteNumber()).isNull();
    assertThat(intent.marketerName()).isNull();
    assertThat(intent.quantity()).isEqualByComparingTo("10");
    assertThat(intent.unit()).isEqualTo("M");
    OrderIntakeStockPreviewDto.HeldPiece piece = lotView(preview, lotA).heldPieces().getFirst();
    assertThat(piece.holdStatus()).isEqualTo(HoldStatus.VERIFIED);
    assertThat(piece.quoteNumber()).isNull();
    assertThat(piece.marketerName()).isNull();
    assertThat(piece.expiresAt()).isEqualTo(EXPIRES);
  }

  // ── Ready stock remainder (business clarification 2026-10-07) ────────────

  @Test
  void stockAwaitingSuitabilityAndUnmeasuredRecordsNeverReduceTheReadyStockRemainder() {
    UUID lotA = UUID.randomUUID();
    stock(
        lot(
            lotA,
            "LOT-A",
            piece("50", PieceState.UNKNOWN),
            unmeasured(),
            piece("70", PieceState.EXCLUDED)));

    OrderIntakeStockPreviewDto preview =
        service.preview(request("100", "M", false, null, null), actor);

    // 100 m, not 50 m (pending stock is not ready) and not null (a missing measurement on a piece
    // that is not ready does not make the ready-free quantity unknown).
    assertThat(preview.totals().notCoveredByReadyStock().canonical()).isEqualByComparingTo("100");
    assertThat(preview.totals().free().canonical()).isEqualByComparingTo("0");
    assertThat(preview.totals().freeComplete()).isTrue();
    // Pending measured stock and unmeasured records stay separate facts.
    assertThat(preview.totals().unknown().canonical()).isEqualByComparingTo("50");
    OrderIntakeStockPreviewDto.Lot lot = lotView(preview, lotA);
    assertThat(lot.unknown().canonical()).isEqualByComparingTo("50");
    assertThat(lot.unknownPieces()).isEqualTo(2);
    assertThat(lot.unmeasuredPieces()).isEqualTo(1);
    assertThat(lot.eligiblePieces()).isZero();
    assertThat(preview.evaluation().options()).isEmpty();
    assertThat(preview.optionChecks()).isEmpty();
  }

  @Test
  void measuredStockAwaitingSuitabilityAloneLeavesTheWholeRequestUncovered() {
    stock(lot(UUID.randomUUID(), "LOT-A", piece("120", PieceState.UNKNOWN)));

    OrderIntakeStockPreviewDto preview =
        service.preview(request("100", "M", false, null, null), actor);

    assertThat(preview.totals().notCoveredByReadyStock().canonical()).isEqualByComparingTo("100");
    assertThat(preview.totals().unknown().canonical()).isEqualByComparingTo("120");
  }

  @Test
  void withNoStockTheWholeRequestIsUncovered() {
    stock();

    OrderIntakeStockPreviewDto preview =
        service.preview(request("100", "M", false, null, null), actor);

    assertThat(preview.lots()).isEmpty();
    assertThat(preview.totals().free().canonical()).isEqualByComparingTo("0");
    assertThat(preview.totals().unknown().canonical()).isEqualByComparingTo("0");
    assertThat(preview.totals().notCoveredByReadyStock().canonical()).isEqualByComparingTo("100");
    assertThat(preview.totals().freeComplete()).isTrue();
    verify(quoteHolds, never()).find(any());
  }

  @Test
  void partlyReadyStockLeavesOnlyTheRestUncovered_andNoPendingPieceEntersAnOption() {
    stock(
        lot(
            UUID.randomUUID(),
            "LOT-A",
            eligible("40"),
            piece("50", PieceState.UNKNOWN),
            unmeasured(),
            piece("70", PieceState.EXCLUDED)));

    OrderIntakeStockPreviewDto preview =
        service.preview(request("100", "M", false, null, null), actor);

    assertThat(preview.totals().free().canonical()).isEqualByComparingTo("40");
    assertThat(preview.totals().notCoveredByReadyStock().canonical()).isEqualByComparingTo("60");
    assertThat(preview.totals().freeComplete()).isTrue();
    // Any pending or excluded piece in an option would take it above the 40 m that is ready.
    assertThat(preview.evaluation().options()).isNotEmpty();
    assertThat(preview.evaluation().options())
        .allSatisfy(
            option -> assertThat(option.quantity()).isLessThanOrEqualTo(new BigDecimal("40")));
  }

  @Test
  void enoughReadyStockLeavesNothingUncovered() {
    stock(lot(UUID.randomUUID(), "LOT-A", eligible("60"), eligible("60"), unmeasured()));

    OrderIntakeStockPreviewDto preview =
        service.preview(request("100", "M", false, null, null), actor);

    assertThat(preview.totals().free().canonical()).isEqualByComparingTo("120");
    assertThat(preview.totals().notCoveredByReadyStock().canonical()).isEqualByComparingTo("0");
  }

  // ── Unverified holds on pieces that are not ready ────────────────────────

  @Test
  void anUnverifiedHoldOnAPieceAwaitingSuitabilityIsListedButLeavesFreeKnown() {
    UUID lotA = UUID.randomUUID();
    ProposalPiece pending = piece("50", PieceState.UNKNOWN);
    stock(lot(lotA, "LOT-A", eligible("100"), eligible("100"), pending));
    when(quoteHolds.find(any()))
        .thenReturn(
            holds(List.of(), List.of(hold(pending.stockUnitId(), lotA, UUID.randomUUID()))));

    OrderIntakeStockPreviewDto preview =
        service.preview(request("100", "M", false, null, null), actor);

    assertNotReadyHoldLeavesFreeKnown(preview, lotA, pending);
  }

  @Test
  void anUnverifiedHoldOnAnExcludedPieceIsListedButLeavesFreeKnown() {
    UUID lotA = UUID.randomUUID();
    ProposalPiece excluded = piece("70", PieceState.EXCLUDED);
    stock(lot(lotA, "LOT-A", eligible("100"), eligible("100"), excluded));
    when(quoteHolds.find(any()))
        .thenReturn(
            holds(List.of(), List.of(hold(excluded.stockUnitId(), lotA, UUID.randomUUID()))));

    OrderIntakeStockPreviewDto preview =
        service.preview(request("100", "M", false, null, null), actor);

    assertNotReadyHoldLeavesFreeKnown(preview, lotA, excluded);
  }

  private static void assertNotReadyHoldLeavesFreeKnown(
      OrderIntakeStockPreviewDto preview, UUID lotId, ProposalPiece held) {
    OrderIntakeStockPreviewDto.Lot lot = lotView(preview, lotId);
    assertThat(lot.heldPieces())
        .singleElement()
        .satisfies(
            piece -> {
              assertThat(piece.stockUnitId()).isEqualTo(held.stockUnitId());
              assertThat(piece.holdStatus()).isEqualTo(HoldStatus.UNVERIFIED);
              assertThat(piece.quoteNumber()).isNull();
            });
    assertThat(lot.freeComplete()).isTrue();
    assertThat(lot.freeIncompleteReasons()).isEmpty();
    assertThat(lot.free().canonical()).isEqualByComparingTo("200");
    assertThat(lot.unverifiedHold().canonical()).isEqualByComparingTo("0");
    assertThat(lot.unverifiedHoldPieces()).isZero();
    assertThat(preview.totals().freeComplete()).isTrue();
    assertThat(preview.totals().notCoveredByReadyStock().canonical()).isEqualByComparingTo("0");
    assertThat(preview.optionChecks())
        .allSatisfy(
            check ->
                assertThat(check.lots())
                    .allSatisfy(part -> assertThat(part.unverifiedHoldPieceIds()).isEmpty()));
  }

  @Test
  void aPreviewWritesNothing() {
    UUID lotA = UUID.randomUUID();
    stock(lot(lotA, "LOT-A", eligible("100"), eligible("100")));
    clearInvocations(proposals);

    service.preview(request("100", "M", false, null, null), actor);

    verify(proposals, never()).save(any());
    verify(quoteHolds, times(1)).find(any());
  }

  // ── Fixtures ─────────────────────────────────────────────────────────────

  private OrderIntakeStockPreviewDto.Evaluation storedEvaluation(SalesOrderLine line) {
    when(access.writableOrder(order.getId(), actor)).thenReturn(order);
    when(access.line(order, line.getId())).thenReturn(line);
    evaluations.evaluate(order.getId(), line.getId(), actor);
    ArgumentCaptor<QuantityProposal> stored = ArgumentCaptor.forClass(QuantityProposal.class);
    verify(proposals).save(stored.capture());
    clearInvocations(stockQuery, proposals);
    return OrderIntakeStockPreviewDto.Evaluation.from(stored.getValue().getResult());
  }

  private SalesOrderLine savedLine(
      String quantity, String unit, boolean singleLot, String upPct, String downPct) {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .salesOrderId(order.getId())
            .productId(productId)
            .colorId(colorId)
            .requestedQty(new BigDecimal(quantity))
            .unit(unit)
            .singleLotRequired(singleLot)
            .build();
    line.setId(UUID.randomUUID());
    line.recordTolerance(decimal(upPct), decimal(downPct), actor, NOW);
    return line;
  }

  private OrderIntakeStockPreviewRequest request(
      String quantity, String unit, boolean singleLot, String upPct, String downPct) {
    return new OrderIntakeStockPreviewRequest(
        productId,
        colorId,
        null,
        null,
        customerId,
        new BigDecimal(quantity),
        unit,
        singleLot,
        decimal(upPct),
        decimal(downPct));
  }

  private static BigDecimal decimal(String value) {
    return value == null ? null : new BigDecimal(value);
  }

  private void stock(ProposalLot... lots) {
    when(stockQuery.find(any())).thenReturn(new ProposalStock(List.of(lots), List.of()));
  }

  private static QuoteHolds holds(List<LotIntentView> intents, List<HeldPieceView> heldPieces) {
    return new QuoteHolds(intents, heldPieces);
  }

  private static LotIntentView intent(
      UUID batchId, UUID quoteId, UUID quoteLineId, String quantity, String canonical) {
    return new LotIntentView(
        batchId,
        quoteId,
        quoteLineId,
        "Q-0001",
        "Sales Rep",
        new BigDecimal(quantity),
        canonical == null ? "YD" : "M",
        decimal(canonical),
        EXPIRES);
  }

  private static HeldPieceView hold(UUID stockUnitId, UUID batchId, UUID quoteLineId) {
    return new HeldPieceView(stockUnitId, batchId, quoteLineId);
  }

  private static OrderIntakeStockPreviewDto.Lot lotView(
      OrderIntakeStockPreviewDto preview, UUID batchId) {
    return preview.lots().stream()
        .filter(lot -> lot.batchId().equals(batchId))
        .findFirst()
        .orElseThrow();
  }

  private static OptionLotCheck partOf(OrderIntakeStockPreviewDto.OptionCheck check, UUID batchId) {
    return check.lots().stream()
        .filter(part -> part.batchId().equals(batchId))
        .findFirst()
        .orElseThrow();
  }

  private ProductSalesDefinitionDto definition(ProductType type) {
    return new ProductSalesDefinitionDto(
        productId, "PRD-1", "Demo product", type, "M", true, List.of(), List.of());
  }

  private static ProposalLot lot(UUID batchId, String lotNo, ProposalPiece... pieces) {
    return new ProposalLot(
        batchId,
        lotNo,
        Instant.parse("2026-07-01T00:00:00Z"),
        PrimaryMeasure.LENGTH,
        "M",
        WidthEvidence.NOT_REQUIRED,
        List.of(pieces));
  }

  private static ProposalPiece unmeasured() {
    return new ProposalPiece(
        UUID.randomUUID(), "P-UNMEASURED", null, PieceState.UNKNOWN, "MEASURE_MISSING", 0L);
  }

  private static ProposalPiece eligible(String measure) {
    return piece(measure, PieceState.ELIGIBLE);
  }

  private static ProposalPiece piece(String measure, PieceState state) {
    UUID id = UUID.randomUUID();
    return new ProposalPiece(
        id,
        "P-" + id.toString().substring(0, 8),
        new BigDecimal(measure),
        state,
        state == PieceState.ELIGIBLE ? null : "TEST",
        0L);
  }
}
