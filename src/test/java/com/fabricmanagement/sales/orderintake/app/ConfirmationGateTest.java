package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.WidthEvidence;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.core.stockunit.api.PieceAllocationPort;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.AcceptanceChannel;
import com.fabricmanagement.sales.orderintake.domain.AcceptanceTerms;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptance;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptanceBasis;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptanceStatus;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.infra.repository.QuantityAcceptanceRepository;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** SOI D4: confirmation re-checks accepted pieces and holds them, or refuses as a whole. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfirmationGateTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACTOR = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

  @Mock private QuantityAcceptanceRepository acceptances;
  @Mock private QuantityEvaluationService evaluation;
  @Mock private PieceAllocationPort allocation;
  @Mock private CustomRequestGate customRequests;

  private final java.util.Map<UUID, BigDecimal> measuredPieces = new java.util.HashMap<>();
  private ConfirmationGate gate;
  private SalesOrder order;
  private SalesOrderLine line;
  private final UUID lotA = UUID.randomUUID();
  private final UUID lotB = UUID.randomUUID();
  private final UUID pieceA = UUID.randomUUID();
  private final UUID pieceB = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    gate = new ConfirmationGate(acceptances, evaluation, allocation, customRequests);
    order = SalesOrder.builder().tradingPartnerId(UUID.randomUUID()).orderNumber("SO-1").build();
    order.setId(UUID.randomUUID());
    line =
        SalesOrderLine.builder()
            .salesOrderId(order.getId())
            .productId(UUID.randomUUID())
            .requestedQty(new BigDecimal("514"))
            .unit("M")
            .build();
    line.setId(UUID.randomUUID());
    when(customRequests.blocks(any(), any())).thenReturn(List.of());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("A line without eligible stock needs no stock choice")
  void noChoiceNoBlock() {
    active(null);
    stock(List.of(), List.of());
    assertThat(gate.blocks(order, List.of(line))).isEmpty();
    gate.allocate(order, List.of(line), ACTOR);
    verify(allocation, never()).allocate(any());
  }

  @Test
  @DisplayName("A05: accepted pieces still eligible are held with the version the check saw")
  void allocatesAcceptedPieces() {
    active(acceptance(List.of(lotA), List.of(pieceA)));
    stock(List.of(lot(lotA, pieceA, PieceState.ELIGIBLE)), List.of());
    when(allocation.allocate(any())).thenReturn(PieceAllocationPort.AllocationOutcome.success(1));

    gate.allocate(order, List.of(line), ACTOR);

    ArgumentCaptor<PieceAllocationPort.AllocationRequest> request =
        ArgumentCaptor.forClass(PieceAllocationPort.AllocationRequest.class);
    verify(allocation).allocate(request.capture());
    verify(allocation).lockLots(eq(TENANT), argThat(lots -> lots.contains(lotA)));
    assertThat(request.getValue().salesOrderLineId()).isEqualTo(line.getId());
    assertThat(request.getValue().pieces())
        .containsExactly(new PieceAllocationPort.PieceRef(pieceA, lotA, 7L));
  }

  @Test
  @DisplayName("S21: a line changed after acceptance cannot be confirmed with it")
  void staleAcceptanceBlocks() {
    QuantityAcceptance acceptance = acceptance(List.of(lotA), List.of(pieceA));
    active(acceptance);
    line.setRequestedQty(new BigDecimal("600"));

    assertThat(gate.blocks(order, List.of(line)))
        .extracting(ConfirmationGate.Block::code)
        .containsExactly("ACCEPTANCE_STALE");
  }

  @Test
  @DisplayName("S17: a piece taken after acceptance refuses the confirmation; nothing is held")
  void stockChangedRefuses() {
    active(acceptance(List.of(lotA), List.of(pieceA)));
    stock(List.of(lot(lotA, pieceA, PieceState.EXCLUDED)), List.of());

    assertThatThrownBy(() -> gate.allocate(order, List.of(line), ACTOR))
        .isInstanceOf(OrderIntakeException.class)
        .extracting(error -> ((OrderIntakeException) error).getErrorCode())
        .isEqualTo("ORDER_INTAKE_STOCK_CHANGED");
    verify(allocation, never()).allocate(any());
  }

  @Test
  @DisplayName("IK-13: a conditional multi-lot acceptance does not lift the block")
  void compatibilityRequired() {
    active(acceptance(List.of(lotA, lotB), List.of(pieceA, pieceB)));
    stock(
        List.of(lot(lotA, pieceA, PieceState.ELIGIBLE), lot(lotB, pieceB, PieceState.ELIGIBLE)),
        List.of());

    assertThat(gate.blocks(order, List.of(line)))
        .extracting(ConfirmationGate.Block::code)
        .containsExactly("COMPATIBILITY_REQUIRED");
  }

  @Test
  @DisplayName("A04: a technical confirmation of the lots lets the multi-lot plan confirm")
  void confirmedGroupAllocates() {
    active(acceptance(List.of(lotA, lotB), List.of(pieceA, pieceB)));
    stock(
        List.of(lot(lotA, pieceA, PieceState.ELIGIBLE), lot(lotB, pieceB, PieceState.ELIGIBLE)),
        List.of(Set.of(lotA, lotB)));
    when(allocation.allocate(any())).thenReturn(PieceAllocationPort.AllocationOutcome.success(2));

    assertThat(gate.blocks(order, List.of(line))).isEmpty();
    gate.allocate(order, List.of(line), ACTOR);
    verify(allocation).allocate(any());
  }

  @Test
  @DisplayName("S10: the losing confirmation of a race gets STOCK_JUST_TAKEN")
  void raceLoser() {
    active(acceptance(List.of(lotA), List.of(pieceA)));
    stock(List.of(lot(lotA, pieceA, PieceState.ELIGIBLE)), List.of());
    when(allocation.allocate(any()))
        .thenReturn(
            PieceAllocationPort.AllocationOutcome.failure(
                PieceAllocationPort.Failures.PIECE_TAKEN, List.of(pieceA)));

    assertThatThrownBy(() -> gate.allocate(order, List.of(line), ACTOR))
        .isInstanceOf(OrderIntakeException.class)
        .extracting(error -> ((OrderIntakeException) error).getErrorCode())
        .isEqualTo("ORDER_INTAKE_STOCK_JUST_TAKEN");
  }

  @Test
  @DisplayName("S19/S11: an open custom request blocks confirming the order it rides on")
  void openCustomRequestBlocks() {
    active(null);
    when(customRequests.blocks(any(), any()))
        .thenReturn(
            List.of(new ConfirmationGate.Block(null, "CUSTOM_REQUEST_OPEN", "open", List.of())));

    assertThatThrownBy(() -> gate.allocate(order, List.of(line), ACTOR))
        .isInstanceOf(OrderIntakeException.class)
        .extracting(error -> ((OrderIntakeException) error).getErrorCode())
        .isEqualTo("ORDER_INTAKE_CUSTOM_REQUEST_OPEN");
  }

  private void active(QuantityAcceptance acceptance) {
    when(acceptances.findByTenantIdAndSalesOrderLineIdInAndStatus(
            TENANT, List.of(line.getId()), QuantityAcceptanceStatus.ACTIVE))
        .thenReturn(acceptance == null ? List.of() : List.of(acceptance));
  }

  private void stock(List<ProposalLot> lots, List<Set<UUID>> confirmed) {
    when(evaluation.currentStock(order, line))
        .thenReturn(
            Optional.of(
                new QuantityEvaluationService.LineStock(
                    PrimaryMeasure.LENGTH, lots, confirmed, List.of())));
  }

  @Test
  void eligibleStockCannotSilentlyBecomeFullProduction() {
    active(null);
    stock(List.of(lot(lotA, pieceA, PieceState.ELIGIBLE)), List.of());
    assertThat(gate.blocks(order, List.of(line)))
        .extracting(ConfirmationGate.Block::code)
        .containsExactly("STOCK_CHOICE_MISSING");
    assertThatThrownBy(() -> gate.allocate(order, List.of(line), ACTOR))
        .isInstanceOf(OrderIntakeException.class);
    verify(allocation, never()).allocate(any());
  }

  @Test
  void excludedStockDoesNotForceAStockChoice() {
    active(null);
    stock(List.of(lot(lotA, pieceA, PieceState.EXCLUDED)), List.of());
    assertThat(gate.blocks(order, List.of(line))).isEmpty();
  }

  @Test
  void aPieceRemeasuredAfterAcceptanceNeedsANewDecision() {
    active(acceptance(List.of(lotA), List.of(pieceA)));
    ProposalLot changed =
        new ProposalLot(
            lotA,
            "LOT",
            null,
            PrimaryMeasure.LENGTH,
            "M",
            WidthEvidence.NOT_REQUIRED,
            List.of(
                new ProposalPiece(
                    pieceA, "R1", new BigDecimal("256"), PieceState.ELIGIBLE, null, 8L)));
    stock(List.of(changed), List.of());
    assertThat(gate.blocks(order, List.of(line)))
        .extracting(ConfirmationGate.Block::code)
        .containsExactly("STOCK_CHANGED");
  }

  private QuantityAcceptance acceptance(List<UUID> lots, List<UUID> pieces) {
    pieces.forEach(
        id ->
            measuredPieces.put(
                id, new BigDecimal("514").divide(BigDecimal.valueOf(pieces.size()))));
    List<QuantityOption.LotPart> parts =
        java.util.stream.IntStream.range(0, lots.size())
            .mapToObj(
                index ->
                    new QuantityOption.LotPart(
                        lots.get(index),
                        "LOT" + index,
                        List.of(pieces.get(index)),
                        new BigDecimal("257"),
                        0,
                        0,
                        false,
                        null,
                        null))
            .toList();
    QuantityOption option =
        new QuantityOption(
            "ABOVE:0123456789abcdef",
            QuantityOption.OptionKind.ABOVE,
            QuantityOption.Compatibility.SINGLE_LOT,
            new BigDecimal("514"),
            new BigDecimal("514"),
            new BigDecimal("14"),
            new BigDecimal("2.8"),
            false,
            parts);
    QuantityAcceptance acceptance =
        QuantityAcceptance.record(
            order.getId(),
            line.getId(),
            UUID.randomUUID(),
            option,
            lots.size() > 1
                ? QuantityOption.Compatibility.PENDING_CONFIRMATION
                : QuantityOption.Compatibility.SINGLE_LOT,
            new BigDecimal("514"),
            "M",
            QuantityAcceptanceBasis.CUSTOMER_ACCEPTED,
            false,
            null,
            null,
            new QuantityAcceptance.CustomerEvidence(
                "Ali", AcceptanceChannel.PHONE, NOW, null, null, true),
            ACTOR,
            NOW,
            null);
    acceptance.coverTerms(AcceptanceTerms.fingerprint(line));
    return acceptance;
  }

  private ProposalLot lot(UUID lotId, UUID pieceId, PieceState state) {
    return new ProposalLot(
        lotId,
        "LOT",
        null,
        PrimaryMeasure.LENGTH,
        "M",
        WidthEvidence.NOT_REQUIRED,
        List.of(
            new ProposalPiece(
                pieceId,
                "R1",
                measuredPieces.getOrDefault(pieceId, new BigDecimal("257")),
                state,
                null,
                7L)));
  }
}
