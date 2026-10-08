package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.production.core.batch.api.LotCompatibilityRequestPort;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.WidthEvidence;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.AcceptanceChannel;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptance;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptanceBasis;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptanceStatus;
import com.fabricmanagement.sales.orderintake.domain.QuantityProposal;
import com.fabricmanagement.sales.orderintake.domain.RemainingNeed;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeRequests;
import com.fabricmanagement.sales.orderintake.dto.QuantityAcceptanceDto;
import com.fabricmanagement.sales.orderintake.infra.repository.QuantityAcceptanceRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.QuantityProposalRepository;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** SOI D3: stock choice and customer acceptance of a whole-piece option. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuantityAcceptanceServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACTOR = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

  @Mock private OrderIntakeAccess access;
  @Mock private QuantityProposalRepository proposals;
  @Mock private QuantityAcceptanceRepository acceptances;
  @Mock private QuantityEvaluationService evaluation;
  @Mock private SalesOrderLineRepository lines;

  @org.mockito.Spy
  private LineAdjustmentGuard adjustments =
      new LineAdjustmentGuard(
          org.mockito.Mockito.mock(
              com.fabricmanagement.sales.salesorder.app.LineAllocationPolicy.class));

  @Mock private com.fabricmanagement.sales.salesorder.app.SalesOrderRevision revision;
  @Mock private com.fabricmanagement.sales.salesorder.app.SalesOrderLeaseGuard leaseGuard;
  @Mock private LotCompatibilityRequestPort compatibilityRequests;

  private final java.util.Map<UUID, BigDecimal> measuredPieces = new java.util.HashMap<>();
  private QuantityAcceptanceService service;
  private SalesOrder order;
  private SalesOrderLine line;
  private final UUID lotA = UUID.randomUUID();
  private final UUID lotB = UUID.randomUUID();
  private final UUID pieceA = UUID.randomUUID();
  private final UUID pieceB = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    service =
        new QuantityAcceptanceService(
            access,
            proposals,
            acceptances,
            evaluation,
            lines,
            adjustments,
            revision,
            leaseGuard,
            compatibilityRequests,
            Clock.fixed(NOW, ZoneOffset.UTC));
    order = SalesOrder.builder().tradingPartnerId(UUID.randomUUID()).orderNumber("SO-1").build();
    order.setId(UUID.randomUUID());
    line =
        SalesOrderLine.builder()
            .salesOrderId(order.getId())
            .productId(UUID.randomUUID())
            .requestedQty(new BigDecimal("500"))
            .unit("M")
            .unitPrice(Money.of(new BigDecimal("4.10"), "EUR"))
            .build();
    line.setId(UUID.randomUUID());
    when(access.writableOrder(order.getId(), ACTOR)).thenReturn(order);
    when(access.readableOrder(order.getId(), ACTOR)).thenReturn(order);
    when(access.line(order, line.getId())).thenReturn(line);
    when(acceptances.save(any(QuantityAcceptance.class))).thenAnswer(call -> call.getArgument(0));
    when(acceptances.findFirstByTenantIdAndSalesOrderLineIdAndStatus(
            TENANT, line.getId(), QuantityAcceptanceStatus.ACTIVE))
        .thenReturn(Optional.empty());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("S06: 212 m accepted from stock, the rest stays open: the line keeps 500 m")
  void belowWithRemainingOpenKeepsTheLine() {
    QuantityOption below =
        option(QuantityOption.OptionKind.BELOW, "212", List.of(lotA), List.of(pieceA));
    QuantityProposal proposal = latest(below);
    stock(List.of(lot(lotA, pieceA, PieceState.ELIGIBLE)), List.of());

    QuantityAcceptanceDto dto =
        service.record(
            order.getId(),
            line.getId(),
            request(proposal, below, RemainingNeed.REMAINS_OPEN, null),
            ACTOR);

    assertThat(dto.basis()).isEqualTo(QuantityAcceptanceBasis.CUSTOMER_ACCEPTED);
    assertThat(dto.remainingQuantity()).isEqualByComparingTo("288");
    assertThat(line.getRequestedQty()).isEqualByComparingTo("500");
    assertThat(dto.coversCurrentTerms()).isTrue();
    verify(adjustments).assertFits(line);
    verify(revision).linesChanged(order);
  }

  @Test
  @DisplayName("While planning evaluates the order, the quantity is not changed by an acceptance")
  void anOrderWithPlanningKeepsItsQuantity() {
    QuantityOption above =
        option(QuantityOption.OptionKind.ABOVE, "514", List.of(lotA), List.of(pieceA));
    QuantityProposal proposal = latest(above);
    stock(List.of(lot(lotA, pieceA, PieceState.ELIGIBLE)), List.of());
    order.moveFlowTo(com.fabricmanagement.sales.salesorder.domain.OrderFlowStage.AWAITING_PLANNING);

    assertThatThrownBy(
            () ->
                service.record(
                    order.getId(), line.getId(), request(proposal, above, null, null), ACTOR))
        .isInstanceOf(com.fabricmanagement.sales.common.exception.OrderDomainException.class);
    assertThat(line.getRequestedQty()).isNotEqualByComparingTo("514");
  }

  @Test
  @DisplayName(
      "CEDIT-07 L15: a leased quantity refuses the acceptance after the order lock, before the"
          + " line is read; nothing changes")
  void aLeasedQuantityRefusesTheAcceptance() {
    QuantityOption above =
        option(QuantityOption.OptionKind.ABOVE, "514", List.of(lotA), List.of(pieceA));
    QuantityProposal proposal = latest(above);
    stock(List.of(lot(lotA, pieceA, PieceState.ELIGIBLE)), List.of());
    com.fabricmanagement.sales.common.exception.OrderDomainException held =
        com.fabricmanagement.sales.common.exception.OrderDomainException.conflict(
            "EDIT_LEASE_HELD", "Someone is editing the quantity");
    org.mockito.Mockito.doThrow(held)
        .when(leaseGuard)
        .assertLineFieldFree(
            order,
            line.getId(),
            com.fabricmanagement.sales.salesorder.domain.OrderEditKey.LINE_QUANTITY,
            ACTOR);

    assertThatThrownBy(
            () ->
                service.record(
                    order.getId(), line.getId(), request(proposal, above, null, null), ACTOR))
        .isSameAs(held);
    org.mockito.InOrder sequence = org.mockito.Mockito.inOrder(revision, leaseGuard, access);
    sequence.verify(revision).lockFresh(order);
    sequence.verify(leaseGuard).assertLineFieldFree(any(), any(), any(), any());
    verify(access, never()).line(any(), any());
    assertThat(line.getRequestedQty()).isEqualByComparingTo("500");
    verify(acceptances, never()).save(any());

    // Withdrawing may move the quantity back to the request: refused the same way.
    assertThatThrownBy(() -> service.withdraw(order.getId(), line.getId(), ACTOR)).isSameAs(held);
    verify(acceptances, never()).save(any());
  }

  @Test
  @DisplayName("K08: an accepted larger quantity changes the line quantity with its amount")
  void aboveChangesTheLineQuantity() {
    QuantityOption above =
        option(QuantityOption.OptionKind.ABOVE, "514", List.of(lotA), List.of(pieceA));
    QuantityProposal proposal = latest(above);
    stock(List.of(lot(lotA, pieceA, PieceState.ELIGIBLE)), List.of());

    service.record(order.getId(), line.getId(), request(proposal, above, null, null), ACTOR);

    assertThat(line.getRequestedQty()).isEqualByComparingTo("514");
    verify(lines).save(line);
    verify(adjustments).assertFits(line);
  }

  @Test
  @DisplayName("S17: pieces taken since the proposal make it stale; nothing is recorded")
  void takenPiecesAreStale() {
    QuantityOption above =
        option(QuantityOption.OptionKind.ABOVE, "514", List.of(lotA), List.of(pieceA));
    QuantityProposal proposal = latest(above);
    stock(List.of(lot(lotA, pieceA, PieceState.EXCLUDED)), List.of());

    assertThatThrownBy(
            () ->
                service.record(
                    order.getId(), line.getId(), request(proposal, above, null, null), ACTOR))
        .isInstanceOf(OrderIntakeException.class)
        .extracting(error -> ((OrderIntakeException) error).getErrorCode())
        .isEqualTo("ORDER_INTAKE_PROPOSAL_STALE");
    verify(acceptances, never()).save(any());
  }

  @Test
  @DisplayName(
      "CEDIT-03 S17.5: a proposal evaluated for the line's earlier product is stale, even when the"
          + " same pieces are offered now")
  void proposalOfTheEarlierProductIsStale() {
    QuantityOption above =
        option(QuantityOption.OptionKind.ABOVE, "514", List.of(lotA), List.of(pieceA));
    QuantityProposal proposal = latest(above);
    stock(List.of(lot(lotA, pieceA, PieceState.ELIGIBLE)), List.of());
    // A product correction committed after the proposal.
    line.setProductId(UUID.randomUUID());

    assertThatThrownBy(
            () ->
                service.record(
                    order.getId(), line.getId(), request(proposal, above, null, null), ACTOR))
        .isInstanceOf(OrderIntakeException.class)
        .extracting(error -> ((OrderIntakeException) error).getErrorCode())
        .isEqualTo("ORDER_INTAKE_PROPOSAL_STALE");
    verify(acceptances, never()).save(any());
    assertThat(line.getRequestedQty()).isEqualByComparingTo("500");
  }

  @Test
  @DisplayName("A newer proposal for the line makes the older one stale")
  void olderProposalIsStale() {
    QuantityOption above =
        option(QuantityOption.OptionKind.ABOVE, "514", List.of(lotA), List.of(pieceA));
    QuantityProposal proposal = proposal(above);
    QuantityProposal newer = proposal(above);
    when(proposals.findByTenantIdAndIdAndSalesOrderLineId(TENANT, proposal.getId(), line.getId()))
        .thenReturn(Optional.of(proposal));
    when(proposals.findFirstByTenantIdAndSalesOrderLineIdOrderByEvaluatedAtDescIdDesc(
            TENANT, line.getId()))
        .thenReturn(Optional.of(newer));

    assertThatThrownBy(
            () ->
                service.record(
                    order.getId(), line.getId(), request(proposal, above, null, null), ACTOR))
        .isInstanceOf(OrderIntakeException.class)
        .hasMessageContaining("newer proposal");
  }

  @Test
  @DisplayName("IK-13: a multi-lot answer without compatibility is conditional and asks production")
  void conditionalAcceptanceOpensACompatibilityRequest() {
    QuantityOption twoLots =
        option(
            QuantityOption.OptionKind.ABOVE, "514", List.of(lotA, lotB), List.of(pieceA, pieceB));
    QuantityProposal proposal = latest(twoLots);
    stock(
        List.of(lot(lotA, pieceA, PieceState.ELIGIBLE), lot(lotB, pieceB, PieceState.ELIGIBLE)),
        List.of());

    QuantityAcceptanceDto dto =
        service.record(order.getId(), line.getId(), request(proposal, twoLots, null, null), ACTOR);

    assertThat(dto.conditional()).isTrue();
    assertThat(dto.compatibility()).isEqualTo(QuantityOption.Compatibility.PENDING_CONFIRMATION);
    verify(compatibilityRequests)
        .request(
            eq(TENANT),
            eq(Set.of(lotA, lotB)),
            eq(line.getProductId()),
            eq(order.getTradingPartnerId()),
            eq(QuantityAcceptanceService.COMPATIBILITY_SOURCE),
            eq(line.getId()),
            anyString(),
            eq(ACTOR));
  }

  @Test
  @DisplayName("A04: a confirmed compatibility group makes the same answer definite")
  void confirmedGroupIsNotConditional() {
    QuantityOption twoLots =
        option(
            QuantityOption.OptionKind.ABOVE, "514", List.of(lotA, lotB), List.of(pieceA, pieceB));
    QuantityProposal proposal = latest(twoLots);
    stock(
        List.of(lot(lotA, pieceA, PieceState.ELIGIBLE), lot(lotB, pieceB, PieceState.ELIGIBLE)),
        List.of(Set.of(lotA, lotB)));

    QuantityAcceptanceDto dto =
        service.record(order.getId(), line.getId(), request(proposal, twoLots, null, null), ACTOR);

    assertThat(dto.conditional()).isFalse();
    assertThat(dto.compatibility()).isEqualTo(QuantityOption.Compatibility.CONFIRMED);
  }

  @Test
  @DisplayName("S22: without contact, channel and time a customer acceptance is refused")
  void customerEvidenceRequired() {
    QuantityOption above =
        option(QuantityOption.OptionKind.ABOVE, "514", List.of(lotA), List.of(pieceA));
    QuantityProposal proposal = latest(above);
    stock(List.of(lot(lotA, pieceA, PieceState.ELIGIBLE)), List.of());
    OrderIntakeRequests.RecordQuantityAcceptance noContact =
        new OrderIntakeRequests.RecordQuantityAcceptance(
            proposal.getId(),
            above.optionKey(),
            false,
            null,
            null,
            AcceptanceChannel.PHONE,
            NOW,
            null,
            null,
            true,
            null);

    assertThatThrownBy(() -> service.record(order.getId(), line.getId(), noContact, ACTOR))
        .isInstanceOf(OrderIntakeException.class)
        .extracting(error -> ((OrderIntakeException) error).getErrorCode())
        .isEqualTo("ORDER_INTAKE_CUSTOMER_EVIDENCE_REQUIRED");
  }

  @Test
  @DisplayName(
      "IK-22: withdrawing an acceptance that changed the line restores the customer's request")
  void withdrawRestoresTheRequestedQuantity() {
    SalesOrderLine requested =
        SalesOrderLine.builder()
            .salesOrderId(order.getId())
            .productId(line.getProductId())
            .requestedQty(new BigDecimal("500"))
            .initialRequestedQty(new BigDecimal("500"))
            .unit("M")
            .unitPrice(Money.of(new BigDecimal("4.10"), "EUR"))
            .build();
    requested.setId(line.getId());
    when(access.line(order, line.getId())).thenReturn(requested);
    QuantityOption above =
        option(QuantityOption.OptionKind.ABOVE, "514", List.of(lotA), List.of(pieceA));
    QuantityProposal proposal = latest(above);
    stock(List.of(lot(lotA, pieceA, PieceState.ELIGIBLE)), List.of());
    java.util.concurrent.atomic.AtomicReference<QuantityAcceptance> saved =
        new java.util.concurrent.atomic.AtomicReference<>();
    when(acceptances.save(any(QuantityAcceptance.class)))
        .thenAnswer(
            call -> {
              saved.set(call.getArgument(0));
              return call.getArgument(0);
            });
    service.record(order.getId(), requested.getId(), request(proposal, above, null, null), ACTOR);
    assertThat(requested.getRequestedQty()).isEqualByComparingTo("514");
    when(acceptances.findFirstByTenantIdAndSalesOrderLineIdAndStatus(
            TENANT, requested.getId(), QuantityAcceptanceStatus.ACTIVE))
        .thenReturn(Optional.of(saved.get()));

    service.withdraw(order.getId(), requested.getId(), ACTOR);

    assertThat(requested.getRequestedQty()).isEqualByComparingTo("500");
    assertThat(saved.get().getStatus()).isEqualTo(QuantityAcceptanceStatus.WITHDRAWN);
    verify(adjustments, org.mockito.Mockito.times(2)).assertFits(requested);
    verify(revision, org.mockito.Mockito.times(2)).linesChanged(order);
  }

  @Test
  @DisplayName(
      "A01: taking the requested quantity with a remnant is the salesperson's decision, no customer evidence")
  void requestedWithRemnantNeedsNoCustomerEvidence() {
    QuantityOption withRemnant =
        option(
            QuantityOption.OptionKind.REQUESTED_WITH_REMNANT,
            "500",
            List.of(lotA),
            List.of(pieceA));
    QuantityProposal proposal = latest(withRemnant);
    stock(List.of(lot(lotA, pieceA, PieceState.ELIGIBLE)), List.of());
    OrderIntakeRequests.RecordQuantityAcceptance noCustomer =
        new OrderIntakeRequests.RecordQuantityAcceptance(
            proposal.getId(),
            withRemnant.optionKey(),
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null);

    QuantityAcceptanceDto dto = service.record(order.getId(), line.getId(), noCustomer, ACTOR);

    assertThat(dto.basis()).isEqualTo(QuantityAcceptanceBasis.EXACT_MATCH);
    assertThat(line.getRequestedQty()).isEqualByComparingTo("500");
  }

  @Test
  @DisplayName("S10: repeating the command with its idempotency key returns the first record")
  void idempotentRepeat() {
    QuantityOption above =
        option(QuantityOption.OptionKind.ABOVE, "514", List.of(lotA), List.of(pieceA));
    QuantityAcceptance first =
        QuantityAcceptance.record(
            order.getId(),
            line.getId(),
            UUID.randomUUID(),
            above,
            QuantityOption.Compatibility.SINGLE_LOT,
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
            "key-1");
    first.setId(UUID.randomUUID());
    first.coverTerms("0".repeat(64));
    when(acceptances.findByTenantIdAndIdempotencyKey(TENANT, "key-1"))
        .thenReturn(Optional.of(first));

    QuantityAcceptanceDto dto =
        service.record(
            order.getId(),
            line.getId(),
            new OrderIntakeRequests.RecordQuantityAcceptance(
                UUID.randomUUID(),
                above.optionKey(),
                false,
                null,
                "Ali",
                AcceptanceChannel.PHONE,
                NOW,
                null,
                null,
                true,
                "key-1"),
            ACTOR);

    assertThat(dto.id()).isEqualTo(first.getId());
    verify(acceptances, never()).save(any());
    verify(lines, never()).save(any());
  }

  @Test
  @DisplayName("CEDIT-03 §5: the order row is locked and reloaded before the line is read")
  void locksTheOrderBeforeTheLine() {
    QuantityOption below =
        option(QuantityOption.OptionKind.BELOW, "212", List.of(lotA), List.of(pieceA));
    QuantityProposal proposal = latest(below);
    stock(List.of(lot(lotA, pieceA, PieceState.ELIGIBLE)), List.of());

    service.record(
        order.getId(),
        line.getId(),
        request(proposal, below, RemainingNeed.REMAINS_OPEN, null),
        ACTOR);

    org.mockito.InOrder locks = org.mockito.Mockito.inOrder(access, revision);
    locks.verify(access).writableOrder(order.getId(), ACTOR);
    locks.verify(revision).lockFresh(order);
    locks.verify(access).line(order, line.getId());
    locks.verify(revision).lockFresh(line);
    locks.verify(revision).linesChanged(order);
  }

  @Test
  @DisplayName(
      "CEDIT-03 §5: a recording of the same key committed while this one waited is answered from it")
  void repeatCommittedWhileWaitingForTheLock() {
    QuantityOption above =
        option(QuantityOption.OptionKind.ABOVE, "514", List.of(lotA), List.of(pieceA));
    QuantityAcceptance first =
        QuantityAcceptance.record(
            order.getId(),
            line.getId(),
            UUID.randomUUID(),
            above,
            QuantityOption.Compatibility.SINGLE_LOT,
            new BigDecimal("514"),
            "M",
            QuantityAcceptanceBasis.CUSTOMER_ACCEPTED,
            false,
            null,
            null,
            new QuantityAcceptance.CustomerEvidence(
                "Morgan", AcceptanceChannel.PHONE, NOW, null, null, true),
            ACTOR,
            NOW,
            "key-2");
    first.setId(UUID.randomUUID());
    first.coverTerms("0".repeat(64));
    // Not there before the lock; committed by the other recording by the time the lock is held.
    when(acceptances.findByTenantIdAndIdempotencyKey(TENANT, "key-2"))
        .thenReturn(Optional.empty(), Optional.of(first));

    QuantityAcceptanceDto dto =
        service.record(
            order.getId(),
            line.getId(),
            new OrderIntakeRequests.RecordQuantityAcceptance(
                UUID.randomUUID(),
                above.optionKey(),
                false,
                null,
                "Morgan",
                AcceptanceChannel.PHONE,
                NOW,
                null,
                null,
                true,
                "key-2"),
            ACTOR);

    assertThat(dto.id()).isEqualTo(first.getId());
    verify(revision).lockFresh(order);
    verify(acceptances, never()).save(any());
    verify(lines, never()).save(any());
    verify(revision, never()).linesChanged(any());
  }

  @Test
  void aRemeasuredPieceCannotBeAcceptedAtItsOldMetres() {
    QuantityOption below =
        option(QuantityOption.OptionKind.BELOW, "212", List.of(lotA), List.of(pieceA));
    QuantityProposal proposal = latest(below);
    measuredPieces.put(pieceA, new BigDecimal("211"));
    stock(List.of(lot(lotA, pieceA, PieceState.ELIGIBLE)), List.of());
    assertThatThrownBy(
            () ->
                service.record(
                    order.getId(),
                    line.getId(),
                    request(proposal, below, RemainingNeed.REMAINS_OPEN, null),
                    ACTOR))
        .isInstanceOf(OrderIntakeException.class);
    verify(acceptances, never()).save(any());
  }

  private OrderIntakeRequests.RecordQuantityAcceptance request(
      QuantityProposal proposal, QuantityOption option, RemainingNeed remaining, String key) {
    return new OrderIntakeRequests.RecordQuantityAcceptance(
        proposal.getId(),
        option.optionKey(),
        false,
        remaining,
        "Jane (buyer)",
        AcceptanceChannel.PHONE,
        NOW,
        null,
        null,
        true,
        key);
  }

  private QuantityProposal latest(QuantityOption option) {
    option
        .pieceIds()
        .forEach(
            id ->
                measuredPieces.put(
                    id,
                    option
                        .canonicalQuantity()
                        .divide(BigDecimal.valueOf(option.pieceIds().size()))));
    QuantityProposal proposal = proposal(option);
    when(proposals.findByTenantIdAndIdAndSalesOrderLineId(TENANT, proposal.getId(), line.getId()))
        .thenReturn(Optional.of(proposal));
    when(proposals.findFirstByTenantIdAndSalesOrderLineIdOrderByEvaluatedAtDescIdDesc(
            TENANT, line.getId()))
        .thenReturn(Optional.of(proposal));
    return proposal;
  }

  private QuantityProposal proposal(QuantityOption option) {
    QuantityEvaluationResult result =
        new QuantityEvaluationResult(
            QuantityEvaluationResult.EvaluationStatus.OPTIONS,
            new BigDecimal("500"),
            "M",
            "M",
            List.of(option),
            2,
            0,
            0,
            List.of(),
            false,
            0);
    QuantityProposal proposal =
        QuantityProposal.record(
            order.getId(),
            line.getId(),
            line.getProductId(),
            new BigDecimal("500"),
            "M",
            result,
            "f".repeat(64),
            ACTOR,
            NOW);
    proposal.setId(UUID.randomUUID());
    return proposal;
  }

  private void stock(List<ProposalLot> lots, List<Set<UUID>> confirmed) {
    when(evaluation.currentStock(order, line))
        .thenReturn(
            Optional.of(
                new QuantityEvaluationService.LineStock(
                    PrimaryMeasure.LENGTH, lots, confirmed, List.of())));
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
                3L)));
  }

  private static QuantityOption option(
      QuantityOption.OptionKind kind, String quantity, List<UUID> lots, List<UUID> pieces) {
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
    return new QuantityOption(
        kind + ":0123456789abcdef",
        kind,
        lots.size() > 1
            ? QuantityOption.Compatibility.PENDING_CONFIRMATION
            : QuantityOption.Compatibility.SINGLE_LOT,
        new BigDecimal(quantity),
        new BigDecimal(quantity),
        new BigDecimal(quantity).subtract(new BigDecimal("500")),
        BigDecimal.ZERO,
        false,
        parts);
  }
}
