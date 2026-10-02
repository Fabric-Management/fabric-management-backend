package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.DeliveryProposal;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerms;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowEvent;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.OrderFlowDtos;
import com.fabricmanagement.sales.salesorder.infra.repository.DeliveryProposalRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderFlowEventRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderFlowServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

  @Mock private SalesOrderRepository orders;
  @Mock private SalesOrderLineRepository lines;
  @Mock private DeliveryProposalRepository proposals;
  @Mock private OrderFlowEventRepository events;
  @Mock private SalesOrderAccessPolicy accessPolicy;
  @Mock private TradingPartnerService partners;
  @Mock private OrderApprovalInvalidator approvals;

  @Mock
  private com.fabricmanagement.sales.orderintake.infra.repository.IntakeAttachmentRepository
      attachments;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID sales = UUID.randomUUID();
  private WorkFixture work;
  private UUID planner;
  private final UUID orderId = UUID.randomUUID();
  private final List<DeliveryProposal> storedProposals = new ArrayList<>();
  private final List<OrderFlowEvent> storedEvents = new ArrayList<>();
  private SalesOrder order;
  private OrderFlowService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    work = new WorkFixture(Clock.fixed(NOW, ZoneOffset.UTC));
    planner = work.planner();
    order =
        SalesOrder.builder()
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-1")
            .status(OrderStatus.DRAFT)
            .orderDate(LocalDate.of(2026, 9, 30))
            .build();
    ReflectionTestUtils.setField(order, "id", orderId);
    work.order(order);
    order.applyDeliveryTerms(DeliveryTerms.of(DeliveryTerm.FCA, "Bradford mill", null));
    order.applyDeliveryTermStatus(null, null);
    when(orders.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(orders.lockByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(accessPolicy.canRead(tenantId, sales, order)).thenReturn(true);
    when(accessPolicy.canWrite(tenantId, sales, order)).thenReturn(true);
    when(partners.findById(any(), any())).thenReturn(Optional.empty());
    when(lines.findByTenantIdAndSalesOrderIdInAndIsActiveTrue(any(), anyCollection()))
        .thenReturn(List.of());
    when(proposals.save(any(DeliveryProposal.class)))
        .thenAnswer(
            invocation -> {
              DeliveryProposal value = invocation.getArgument(0);
              ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
              storedProposals.add(value);
              return value;
            });
    when(proposals.findFirstByTenantIdAndSalesOrderIdOrderBySequenceDesc(tenantId, orderId))
        .thenAnswer(
            invocation ->
                storedProposals.stream()
                    .max(Comparator.comparingInt(DeliveryProposal::getSequence)));
    when(events.save(any(OrderFlowEvent.class)))
        .thenAnswer(
            invocation -> {
              storedEvents.add(invocation.getArgument(0));
              return invocation.getArgument(0);
            });
    when(events.findByTenantIdAndSalesOrderIdOrderByOccurredAtDesc(tenantId, orderId))
        .thenAnswer(invocation -> List.copyOf(storedEvents).reversed());
    service =
        new OrderFlowService(
            orders,
            lines,
            proposals,
            events,
            accessPolicy,
            partners,
            attachments,
            work.service,
            approvals,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  private OrderFlowDtos.ProposeDelivery proposal(LocalDate on) {
    return new OrderFlowDtos.ProposeDelivery(on, NOW.plusSeconds(48 * 3600), null);
  }

  @Test
  void salesHandsOverPlanningProposesAndCompletesEveryMoveIsKept() {
    service.submit(orderId, sales);
    service.claim(orderId, planner);
    OrderFlowDtos.QueueItem proposed =
        service.propose(orderId, proposal(LocalDate.of(2026, 10, 20)), planner);
    OrderFlowDtos.QueueItem completed = service.complete(orderId, planner);

    assertThat(proposed.proposal().appliesToCurrentTerms()).isTrue();
    assertThat(completed.stage()).isEqualTo(OrderFlowStage.PLANNED);
    assertThat(storedEvents)
        .extracting(OrderFlowEvent::getToStage)
        .containsExactly(
            OrderFlowStage.AWAITING_PLANNING, OrderFlowStage.IN_PLANNING, OrderFlowStage.PLANNED);
    // A proposal is not a promise: nothing is committed by planning.
    assertThat(order.getCommittedOn()).isNull();
  }

  @Test
  void planningReopensAnOrderOutForTheCustomersApprovalAndTheLinkIsWithdrawn() {
    service.submit(orderId, sales);
    service.claim(orderId, planner);
    service.propose(orderId, proposal(LocalDate.of(2026, 10, 20)), planner);
    service.complete(orderId, planner);
    order.moveFlowTo(OrderFlowStage.AWAITING_CUSTOMER_APPROVAL);

    service.reopen(orderId, "Dyehouse slot moved", planner);

    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.IN_PLANNING);
    assertThat(order.getPlanningEvaluation()).isEqualTo(1);
    verify(approvals)
        .withdrawOpen(orderId, "Planning reopened the evaluation: Dyehouse slot moved", planner);
  }

  @Test
  void salesTakingBackAnOrderOutForApprovalWithdrawsItsLink() {
    service.submit(orderId, sales);
    service.claim(orderId, planner);
    service.propose(orderId, proposal(LocalDate.of(2026, 10, 20)), planner);
    service.complete(orderId, planner);
    order.moveFlowTo(OrderFlowStage.AWAITING_INTERNAL_APPROVAL);

    service.withdraw(orderId, "Customer wants another colour", sales);

    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.DRAFT);
    verify(approvals)
        .withdrawOpen(orderId, "Sales took the order back: Customer wants another colour", sales);
  }

  @Test
  void anApprovedOrderIsNeitherReopenedNorTakenBack() {
    service.submit(orderId, sales);
    service.claim(orderId, planner);
    service.propose(orderId, proposal(LocalDate.of(2026, 10, 20)), planner);
    service.complete(orderId, planner);
    order.moveFlowTo(OrderFlowStage.AWAITING_CUSTOMER_APPROVAL);
    order.moveFlowTo(OrderFlowStage.CUSTOMER_APPROVED);

    assertThatThrownBy(() -> service.reopen(orderId, "Late change", planner))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("WRONG_STAGE"));
    assertThatThrownBy(() -> service.withdraw(orderId, "Late change", sales))
        .isInstanceOf(OrderDomainException.class);
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.CUSTOMER_APPROVED);
  }

  @Test
  void aProposalMadeUnderAnotherTermCannotCompletePlanning() {
    service.submit(orderId, sales);
    service.claim(orderId, planner);
    service.propose(orderId, proposal(LocalDate.of(2026, 10, 20)), planner);
    order.applyDeliveryTerms(DeliveryTerms.of(DeliveryTerm.DAP, "Buyer DC, Leicester", null));

    assertThatThrownBy(() -> service.complete(orderId, planner))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("propose the date again");
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.IN_PLANNING);
  }

  @Test
  void anExpiredProposalCannotCompletePlanning() {
    service.submit(orderId, sales);
    service.claim(orderId, planner);
    proposals.save(
        DeliveryProposal.propose(
            orderId,
            null,
            order.getPlanningRound(),
            order.getPlanningEvaluation(),
            LocalDate.of(2026, 10, 20),
            NOW.minusSeconds(3600),
            order.getDeliveryTerms(),
            null,
            planner,
            NOW.minusSeconds(7200),
            LocalDate.of(2026, 10, 1)));

    assertThatThrownBy(() -> service.complete(orderId, planner))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("no longer valid");
  }

  @Test
  void planningReturnsWithAReasonAndSalesWithdrawsWithOneOnceItStarted() {
    service.submit(orderId, sales);
    service.claim(orderId, planner);
    assertThatThrownBy(() -> service.returnToSales(orderId, " ", planner))
        .isInstanceOf(OrderDomainException.class);
    service.returnToSales(orderId, "Width missing on line 2", planner);
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.DRAFT);
    assertThat(storedEvents.getLast().getReason()).isEqualTo("Width missing on line 2");
    // Returned work leaves the planner: the next hand-over goes to the team's queue again.
    assertThat(work.stored(orderId, OrderWorkKind.PLANNING).isAssigned()).isFalse();

    service.submit(orderId, sales);
    service.withdraw(orderId, null, sales);
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.DRAFT);

    service.submit(orderId, sales);
    service.claim(orderId, planner);
    assertThatThrownBy(() -> service.withdraw(orderId, null, sales))
        .isInstanceOf(OrderDomainException.class);
  }

  @Test
  void aProposalNeedsTheEvaluationToHaveStarted() {
    service.submit(orderId, sales);

    assertThatThrownBy(
            () -> service.propose(orderId, proposal(LocalDate.of(2026, 10, 20)), planner))
        .isInstanceOf(OrderDomainException.class);
    verify(proposals, never()).save(any());
  }

  @Test
  void aProposalFromAnEarlierHandOverIsNotReused() {
    service.submit(orderId, sales);
    service.claim(orderId, planner);
    service.propose(orderId, proposal(LocalDate.of(2026, 10, 20)), planner);
    service.withdraw(orderId, "Customer added a colour", sales);
    service.submit(orderId, sales);
    service.claim(orderId, planner);

    assertThat(service.view(orderId, sales).proposal()).isNull();
    assertThatThrownBy(() -> service.complete(orderId, planner))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("Propose a date");
  }

  @Test
  void documentsAddedDuringPlanningAreShownToPlanning() {
    when(attachments.countByTenantIdAndSalesOrderIdAndUploadedAtAfter(any(), any(), any()))
        .thenReturn(2L);
    service.submit(orderId, sales);

    assertThat(service.claim(orderId, planner).documentsAddedDuringPlanning()).isEqualTo(2);
  }

  @Test
  void whileWithPlanningWhatWasEvaluatedCannotChange() {
    service.submit(orderId, sales);

    assertThatThrownBy(order::assertCommercialContentEditable)
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("take it back to the draft")
        .extracting(error -> ((OrderDomainException) error).getErrorCode())
        .isEqualTo("ORDER_WITH_PLANNING");
    service.withdraw(orderId, null, sales);
    order.assertCommercialContentEditable();
  }
}
