package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.orderintake.infra.repository.IntakeAttachmentRepository;
import com.fabricmanagement.sales.salesorder.domain.DeliveryProposal;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerms;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowEvent;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkEventType;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.OrderFlowDtos;
import com.fabricmanagement.sales.salesorder.dto.OrderWorkDtos;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Planning work needs the permission, scope over the order's planning and the right flow stage —
 * all three. Responsibility comes from an explicit claim or an assignment with a reason, never from
 * a write.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderPlanningAccessTest {

  private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

  @Mock private SalesOrderRepository orders;
  @Mock private SalesOrderLineRepository lines;
  @Mock private DeliveryProposalRepository proposals;
  @Mock private OrderFlowEventRepository events;
  @Mock private SalesOrderAccessPolicy accessPolicy;
  @Mock private TradingPartnerService partners;
  @Mock private IntakeAttachmentRepository attachments;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID sales = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private final List<DeliveryProposal> storedProposals = new ArrayList<>();
  private SalesOrder order;
  private WorkFixture work;
  private OrderFlowService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    work = new WorkFixture(Clock.fixed(NOW, ZoneOffset.UTC));
    order =
        SalesOrder.builder()
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-9")
            .status(OrderStatus.DRAFT)
            .orderDate(LocalDate.of(2026, 9, 30))
            .build();
    ReflectionTestUtils.setField(order, "id", orderId);
    work.order(order);
    order.applyDeliveryTerms(DeliveryTerms.of(DeliveryTerm.FCA, "Bradford mill", null));
    order.applyDeliveryTermStatus(null, null);
    when(orders.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(orders.lockByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(orders.findByTenantIdAndFlowStageInAndIsActiveTrueOrderByCreatedAtAsc(any(), any()))
        .thenAnswer(
            invocation ->
                invocation
                        .<java.util.Set<OrderFlowStage>>getArgument(1)
                        .contains(order.getFlowStage())
                    ? List.of(order)
                    : List.of());
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
    when(proposals.findByTenantIdAndSalesOrderIdInOrderBySequenceDesc(any(), anyCollection()))
        .thenAnswer(invocation -> List.copyOf(storedProposals).reversed());
    when(events.save(any(OrderFlowEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
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
            Clock.fixed(NOW, ZoneOffset.UTC));
    service.submit(orderId, sales);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void handingOverRoutesTheOrderToThePlanningTeamUnassigned() {
    var routed = work.stored(orderId, OrderWorkKind.PLANNING);

    assertThat(routed.getDepartmentCode()).isEqualTo("PLANNING");
    assertThat(routed.isAssigned()).isFalse();
    assertThat(work.events).extracting("type").containsExactly(OrderWorkEventType.ROUTED);
  }

  @Test
  void theModulePermissionWithoutScopeOverTheOrderDoesNotReachIt() {
    // Production write in another team: the module permission alone is not access to this order.
    UUID weaving =
        work.user(
            "WEAVING",
            Map.of(
                PermissionKey.PRODUCTION_READ, DataScope.DEPARTMENT,
                PermissionKey.PRODUCTION_WRITE, DataScope.DEPARTMENT,
                PermissionKey.PRODUCTION_CLAIM, DataScope.DEPARTMENT));
    UUID planner = work.planner();
    service.claim(orderId, planner);

    assertThat(service.planningQueue(weaving)).isEmpty();
    assertThatThrownBy(() -> service.propose(orderId, proposal(), weaving))
        .isInstanceOf(NotFoundException.class);
    assertThat(storedProposals).isEmpty();
  }

  @Test
  void ownScopeCoversOnlyTheOrdersThePersonTook() {
    UUID first = work.planner();
    UUID second = work.planner();
    service.claim(orderId, first);

    assertThat(service.planningQueue(first)).hasSize(1);
    assertThat(service.planningQueue(second)).isEmpty();
    assertThatThrownBy(() -> service.propose(orderId, proposal(), second))
        .isInstanceOf(NotFoundException.class);
    service.propose(orderId, proposal(), first);
    assertThat(storedProposals).hasSize(1);
  }

  @Test
  void theWorkPermissionAloneDoesNotTakeAnOrder() {
    UUID writerWithoutClaim =
        work.user(
            "PLANNING",
            Map.of(
                PermissionKey.PRODUCTION_READ, DataScope.ORGANIZATION,
                PermissionKey.PRODUCTION_WRITE, DataScope.ORGANIZATION));

    // Unassigned work is not even listed without the claim or assign permission.
    assertThat(service.planningQueue(writerWithoutClaim)).isEmpty();
    assertThatThrownBy(() -> service.claim(orderId, writerWithoutClaim))
        .isInstanceOf(NotFoundException.class);
    // Nor does a write on unassigned work make the writer responsible.
    assertThatThrownBy(() -> service.startEvaluation(orderId, writerWithoutClaim))
        .isInstanceOf(NotFoundException.class);
    assertThat(work.stored(orderId, OrderWorkKind.PLANNING).isAssigned()).isFalse();
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.AWAITING_PLANNING);
  }

  @Test
  void ofTwoPlannersTakingTheSameOrderOnlyOneSucceeds() {
    UUID first = work.planner();
    UUID second = work.planner();
    service.claim(orderId, first);

    assertThatThrownBy(() -> service.claim(orderId, second))
        .isInstanceOf(OrderDomainException.class)
        .extracting(error -> ((OrderDomainException) error).getErrorCode())
        .isEqualTo("WORK_ALREADY_TAKEN");
    assertThat(work.stored(orderId, OrderWorkKind.PLANNING).getAssigneeId()).isEqualTo(first);
    assertThat(work.events)
        .extracting("type")
        .containsExactly(OrderWorkEventType.ROUTED, OrderWorkEventType.CLAIMED);
  }

  @Test
  void aSupervisorAssignsWithAReasonToSomeoneWhoCanDoTheWork() {
    UUID supervisor = work.planningSupervisor();
    UUID planner = work.planner();
    UUID salesPerson =
        work.user("SALES", Map.of(PermissionKey.SALES_WRITE, DataScope.ORGANIZATION));

    assertThatThrownBy(
            () ->
                service.assignPlanner(orderId, new OrderWorkDtos.Assign(planner, " "), supervisor))
        .isInstanceOf(OrderDomainException.class);
    assertThatThrownBy(
            () ->
                service.assignPlanner(
                    orderId, new OrderWorkDtos.Assign(salesPerson, "Cover"), supervisor))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("cannot do this work");
    assertThatThrownBy(
            () -> service.assignPlanner(orderId, new OrderWorkDtos.Assign(planner, "X"), planner))
        .isInstanceOf(AccessDeniedException.class);

    service.assignPlanner(
        orderId, new OrderWorkDtos.Assign(planner, "Knows this customer"), supervisor);
    service.startEvaluation(orderId, planner);

    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.IN_PLANNING);
    assertThat(work.events.getLast().getReason()).isEqualTo("Knows this customer");
    assertThat(work.events.getLast().getActorId()).isEqualTo(supervisor);
  }

  @Test
  void releasingGivesTheWorkBackToTheQueueWithAReason() {
    UUID planner = work.planner();
    UUID other = work.planner();
    service.claim(orderId, planner);

    assertThatThrownBy(() -> service.releasePlanner(orderId, "Busy", other))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.releasePlanner(orderId, "", planner))
        .isInstanceOf(OrderDomainException.class);
    service.releasePlanner(orderId, "On leave from tomorrow", planner);

    assertThat(work.stored(orderId, OrderWorkKind.PLANNING).isAssigned()).isFalse();
    assertThat(service.planningQueue(other)).hasSize(1);
  }

  @Test
  void stepsOutsideTheirStageAreRefused() {
    UUID supervisor = work.planningSupervisor();
    UUID planner = work.planner();
    service.assignPlanner(orderId, new OrderWorkDtos.Assign(planner, "Cover"), supervisor);

    assertThatThrownBy(() -> service.complete(orderId, planner))
        .isInstanceOf(OrderDomainException.class)
        .extracting(error -> ((OrderDomainException) error).getErrorCode())
        .isEqualTo("WRONG_STAGE");
    service.startEvaluation(orderId, planner);
    assertThatThrownBy(() -> service.reopen(orderId, "New capacity", planner))
        .isInstanceOf(OrderDomainException.class)
        .extracting(error -> ((OrderDomainException) error).getErrorCode())
        .isEqualTo("WRONG_STAGE");

    service.propose(orderId, proposal(), planner);
    service.complete(orderId, planner);
    assertThatThrownBy(() -> service.propose(orderId, proposal(), planner))
        .isInstanceOf(OrderDomainException.class);
    assertThatThrownBy(() -> service.reopen(orderId, " ", planner))
        .isInstanceOf(OrderDomainException.class);
    service.reopen(orderId, "Dyehouse slot moved", planner);
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.IN_PLANNING);
  }

  @Test
  void theQueueTellsEachPlannerWhatTheyMayDo() {
    UUID planner = work.planner();
    Map<OrderWorkDtos.Action, String> beforeClaim = actions(service.planningQueue(planner));
    assertThat(beforeClaim)
        .containsEntry(OrderWorkDtos.Action.CLAIM, "")
        .containsEntry(OrderWorkDtos.Action.PROPOSE, "WRONG_STAGE")
        .containsEntry(OrderWorkDtos.Action.START_EVALUATION, "WORK_NOT_CLAIMED")
        .containsEntry(OrderWorkDtos.Action.ASSIGN, "PERMISSION_DENIED");

    service.claim(orderId, planner);
    Map<OrderWorkDtos.Action, String> afterClaim = actions(service.planningQueue(planner));
    assertThat(afterClaim)
        .containsEntry(OrderWorkDtos.Action.CLAIM, "WORK_ALREADY_TAKEN")
        .containsEntry(OrderWorkDtos.Action.PROPOSE, "")
        .containsEntry(OrderWorkDtos.Action.COMPLETE, "PROPOSAL_MISSING")
        .containsEntry(OrderWorkDtos.Action.RELEASE, "");
    assertThat(service.planningQueue(planner).getFirst().assignment().mine()).isTrue();
  }

  @Test
  void aReopenedEvaluationNeedsTheDateProposedOrConfirmedAgain() {
    UUID planner = work.planner();
    service.claim(orderId, planner);
    service.propose(orderId, proposal(), planner);
    service.complete(orderId, planner);
    service.reopen(orderId, "Dyehouse slot moved", planner);

    assertThat(actions(service.planningQueue(planner)))
        .containsEntry(OrderWorkDtos.Action.COMPLETE, "PROPOSAL_BEFORE_REOPEN");
    assertThat(service.planningQueue(planner).getFirst().proposal().madeBeforeReopen()).isTrue();
    assertThatThrownBy(() -> service.complete(orderId, planner))
        .isInstanceOf(OrderDomainException.class)
        .extracting(error -> ((OrderDomainException) error).getErrorCode())
        .isEqualTo("PROPOSAL_BEFORE_REOPEN");
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.IN_PLANNING);

    // Confirming the same date again is a new proposal of the reopened evaluation.
    service.propose(orderId, proposal(), planner);
    service.complete(orderId, planner);
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.PLANNED);
  }

  @Test
  void aCancelledOrderTakesNoPlanningWorkThroughTheApi() {
    UUID planner = work.planner();
    UUID supervisor = work.planningSupervisor();
    service.claim(orderId, planner);
    ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELLED);

    for (Runnable step :
        List.<Runnable>of(
            () -> service.propose(orderId, proposal(), planner),
            () -> service.complete(orderId, planner),
            () -> service.returnToSales(orderId, "No longer needed", planner),
            () ->
                service.assignPlanner(
                    orderId, new OrderWorkDtos.Assign(planner, "Cover"), supervisor))) {
      assertThatThrownBy(step::run)
          .isInstanceOf(OrderDomainException.class)
          .extracting(error -> ((OrderDomainException) error).getErrorCode())
          .isEqualTo("ORDER_CLOSED");
    }
    assertThat(storedProposals).isEmpty();
    // Releasing the person stays possible; taking it again does not.
    service.releasePlanner(orderId, "Order cancelled", planner);
    assertThatThrownBy(() -> service.claim(orderId, planner))
        .isInstanceOf(OrderDomainException.class)
        .extracting(error -> ((OrderDomainException) error).getErrorCode())
        .isEqualTo("ORDER_CLOSED");
    assertThat(work.stored(orderId, OrderWorkKind.PLANNING).isAssigned()).isFalse();
  }

  @Test
  void anotherPlannerTakesOverAReleasedEvaluationWhereItStands() {
    UUID first = work.planner();
    UUID second = work.planner();
    service.claim(orderId, first);
    service.propose(orderId, proposal(), first);
    service.releasePlanner(orderId, "On leave", first);

    assertThat(actions(service.planningQueue(second)))
        .containsEntry(OrderWorkDtos.Action.CLAIM, "");
    service.claim(orderId, second);

    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.IN_PLANNING);
    assertThat(work.stored(orderId, OrderWorkKind.PLANNING).getAssigneeId()).isEqualTo(second);
    service.complete(orderId, second);
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.PLANNED);
  }

  @Test
  void workNobodyInTheTeamCanTakeIsShownAsSuch() {
    UUID manager =
        work.user(
            "MANAGEMENT_PLANNING",
            Map.of(
                PermissionKey.PRODUCTION_READ, DataScope.ORGANIZATION,
                PermissionKey.PRODUCTION_ASSIGN, DataScope.ORGANIZATION));

    assertThat(service.planningQueue(manager).getFirst().assignment().teamCanTake()).isFalse();
    UUID planner = work.planner();
    assertThat(service.planningQueue(manager).getFirst().assignment().teamCanTake()).isTrue();

    work.deactivate(planner);
    assertThat(service.planningQueue(manager).getFirst().assignment().teamCanTake()).isFalse();
    assertThatThrownBy(
            () ->
                service.assignPlanner(
                    orderId, new OrderWorkDtos.Assign(planner, "Only planner"), manager))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("active");
  }

  private static Map<OrderWorkDtos.Action, String> actions(List<OrderFlowDtos.QueueItem> queue) {
    assertThat(queue).hasSize(1);
    return queue.getFirst().actions().stream()
        .collect(
            Collectors.toMap(
                OrderWorkDtos.Capability::action,
                capability -> capability.reason() == null ? "" : capability.reason()));
  }

  private static OrderFlowDtos.ProposeDelivery proposal() {
    return new OrderFlowDtos.ProposeDelivery(
        LocalDate.of(2026, 10, 20), NOW.plusSeconds(48 * 3600), null);
  }
}
