package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.CommitmentChannel;
import com.fabricmanagement.sales.salesorder.domain.CustomerApproval;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalChannel;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalStatus;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerms;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionContent;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionKind;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.CustomerApprovalRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderFlowEventRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderVersionRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The customer's decision on a sent version. Each transaction reads the request and the order
 * afresh, as the database would after a rollback, so a failed confirmation leaves nothing behind.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerApprovalDecisionServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-02T09:00:00Z");
  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ORDER = UUID.randomUUID();
  private static final UUID APPROVAL = UUID.randomUUID();
  private static final UUID VERSION = UUID.randomUUID();
  private static final LocalDate PROPOSED = LocalDate.of(2026, 11, 20);
  private static final CustomerApprovalDecisionService.Authority ANYONE = (approval, now) -> {};

  @Mock private CustomerApprovalRepository approvals;
  @Mock private OrderVersionRepository versions;
  @Mock private SalesOrderRepository orders;
  @Mock private SalesOrderLineRepository lines;
  @Mock private OrderIntakeHooks intake;
  @Mock private DeliveryCommitmentService commitments;
  @Mock private SalesOrderService salesOrders;
  @Mock private OrderWorkService work;
  @Mock private TransactionTemplate transactions;
  @Mock private EntityManager entityManager;
  @Mock private OrderFlowEventRepository events;
  @Mock private ApproverAuthorities approvers;

  /** What each transaction loaded, newest last. */
  private final List<CustomerApproval> loadedApprovals = new ArrayList<>();

  private final List<SalesOrder> loadedOrders = new ArrayList<>();
  private CustomerApprovalStatus storedStatus = CustomerApprovalStatus.SENT;
  private CustomerApprovalDecisionService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service =
        new CustomerApprovalDecisionService(
            approvals,
            versions,
            orders,
            lines,
            intake,
            commitments,
            salesOrders,
            new OrderFlowRecorder(events, clock),
            work,
            transactions,
            entityManager,
            clock,
            approvers);
    when(transactions.execute(any()))
        .thenAnswer(
            invocation ->
                ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null));
    when(approvals.findByTenantIdAndId(TENANT, APPROVAL))
        .thenAnswer(
            invocation -> {
              CustomerApproval fresh = sentApproval();
              if (storedStatus != CustomerApprovalStatus.SENT) {
                ReflectionTestUtils.setField(fresh, "status", storedStatus);
              }
              loadedApprovals.add(fresh);
              return Optional.of(fresh);
            });
    // A save "commits" only what the transaction that saved it leaves behind; see commit().
    when(orders.lockByTenantIdAndId(TENANT, ORDER))
        .thenAnswer(
            invocation -> {
              SalesOrder fresh = awaitingOrder();
              loadedOrders.add(fresh);
              return Optional.of(fresh);
            });
    when(versions.findByTenantIdAndId(TENANT, VERSION)).thenReturn(Optional.of(version()));
    when(lines.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(ORDER)).thenReturn(List.of());
    when(intake.confirmationBlockers(any(), any())).thenReturn(List.of());
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  private static SalesOrder awaitingOrder() {
    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-1")
            .flowStage(OrderFlowStage.AWAITING_CUSTOMER_APPROVAL)
            .planningRound(1)
            .build();
    ReflectionTestUtils.setField(order, "id", ORDER);
    ReflectionTestUtils.setField(order, "isActive", true);
    order.applyDeliveryTerms(
        DeliveryTerms.of(DeliveryTerm.FCA, "Felixstowe", IncotermsVersion.INCOTERMS_2020));
    return order;
  }

  private static OrderVersion version() {
    OrderVersion version =
        OrderVersion.freeze(
            ORDER,
            null,
            OrderVersionKind.APPROVAL,
            1,
            0,
            UUID.randomUUID(),
            new OrderVersionContent(
                "Bradford Mills",
                "SO-1",
                "Northern Garments",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                0,
                List.of(),
                new OrderVersionContent.Proposal(UUID.randomUUID(), PROPOSED, null, null)),
            "f".repeat(64),
            UUID.randomUUID(),
            NOW.minusSeconds(3600));
    ReflectionTestUtils.setField(version, "id", VERSION);
    return version;
  }

  private static CustomerApproval sentApproval() {
    CustomerApproval approval =
        CustomerApproval.request(
            version(),
            UUID.randomUUID(),
            "Jane Smith",
            "jane@example.co.uk",
            NOW.plus(Duration.ofDays(5)),
            null,
            UUID.randomUUID(),
            NOW.minusSeconds(3600));
    ReflectionTestUtils.setField(approval, "id", APPROVAL);
    approval.issueLink("a".repeat(64), UUID.randomUUID(), NOW.minusSeconds(3600));
    return approval;
  }

  private static CustomerApproval.Decider jane() {
    return new CustomerApproval.Decider(
        CustomerApprovalChannel.EMAIL_LINK,
        "Jane Smith",
        "jane@example.co.uk",
        null,
        "203.0.113.7",
        "Mozilla/5.0");
  }

  private CustomerApproval lastApproval() {
    return loadedApprovals.getLast();
  }

  private SalesOrder lastOrder() {
    return loadedOrders.getLast();
  }

  @Test
  void approvalConfirmsTheOrderAndRecordsTheAgreedDate() {
    CustomerApprovalDecisionService.Outcome outcome = service.approve(APPROVAL, jane(), ANYONE);

    assertThat(outcome.status()).isEqualTo(CustomerApprovalStatus.APPROVED);
    assertThat(lastApproval().getDecidedByEmail()).isEqualTo("jane@example.co.uk");
    assertThat(lastApproval().getIpAddress()).isEqualTo("203.0.113.7");
    assertThat(lastOrder().getFlowStage()).isEqualTo(OrderFlowStage.CUSTOMER_APPROVED);
    verify(commitments)
        .recordCustomerApproval(
            eq(lastOrder()),
            eq(PROPOSED),
            eq("Jane Smith <jane@example.co.uk> via the e-mailed link"),
            eq(CommitmentChannel.APPROVAL_LINK),
            eq(1),
            eq(SystemUser.ID),
            eq(NOW));
    verify(salesOrders).confirmApprovedByCustomer(lastOrder());
  }

  @Test
  void approvingTwiceGivesTheSameResultAndConfirmsOnce() {
    service.approve(APPROVAL, jane(), ANYONE);
    storedStatus = CustomerApprovalStatus.APPROVED;

    CustomerApprovalDecisionService.Outcome again = service.approve(APPROVAL, jane(), ANYONE);

    assertThat(again.status()).isEqualTo(CustomerApprovalStatus.APPROVED);
    verify(salesOrders, times(1)).confirmApprovedByCustomer(any());
    verify(commitments, times(1))
        .recordCustomerApproval(any(), any(), any(), any(), anyInt(), any(), any());
  }

  @Test
  void whenTheTermsCanNoLongerBeMetTheOrderGoesBackToPlanning() {
    when(intake.confirmationBlockers(any(), any())).thenReturn(List.of("PIECE_TAKEN"));

    CustomerApprovalDecisionService.Outcome outcome = service.approve(APPROVAL, jane(), ANYONE);

    assertThat(outcome.status()).isEqualTo(CustomerApprovalStatus.APPROVED_NOT_FULFILLABLE);
    assertThat(lastApproval().getDecisionDetail()).isEqualTo("PIECE_TAKEN");
    assertThat(lastOrder().getFlowStage()).isEqualTo(OrderFlowStage.IN_PLANNING);
    assertThat(lastOrder().getPlanningEvaluation()).isEqualTo(1);
    assertThat(lastOrder().getStatus()).isEqualTo(OrderStatus.DRAFT);
    verify(salesOrders, never()).confirmApprovedByCustomer(any());
    verify(commitments, never())
        .recordCustomerApproval(any(), any(), any(), any(), anyInt(), any(), any());
  }

  @Test
  void aFailedConfirmationIsRolledBackAndTheApprovalStillRecorded() {
    doThrow(OrderDomainException.stage("PIECE_NOT_AVAILABLE", "The piece was taken"))
        .when(salesOrders)
        .confirmApprovedByCustomer(any());

    CustomerApprovalDecisionService.Outcome outcome = service.approve(APPROVAL, jane(), ANYONE);

    // The second transaction read the request and the order afresh, as after the rollback.
    assertThat(loadedApprovals).hasSize(2);
    assertThat(outcome.status()).isEqualTo(CustomerApprovalStatus.APPROVED_NOT_FULFILLABLE);
    assertThat(lastApproval().getDecisionDetail()).isEqualTo("The piece was taken");
    assertThat(lastOrder().getFlowStage()).isEqualTo(OrderFlowStage.IN_PLANNING);
    assertThat(lastOrder().getStatus()).isEqualTo(OrderStatus.DRAFT);
  }

  @Test
  void withoutTheVerifiedSessionNothingIsDecided() {
    CustomerApprovalDecisionService.Authority refuse =
        (approval, now) -> {
          throw OrderDomainException.stage("SESSION_REQUIRED", "Enter the code first");
        };

    assertThatThrownBy(() -> service.approve(APPROVAL, jane(), refuse))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("SESSION_REQUIRED"));

    assertThat(lastApproval().getStatus()).isEqualTo(CustomerApprovalStatus.SENT);
    assertThat(lastOrder().getFlowStage()).isEqualTo(OrderFlowStage.AWAITING_CUSTOMER_APPROVAL);
    verify(salesOrders, never()).confirmApprovedByCustomer(any());
  }

  @Test
  void aWithdrawnRequestCannotBeApproved() {
    storedStatus = CustomerApprovalStatus.WITHDRAWN;

    assertThatThrownBy(() -> service.approve(APPROVAL, jane(), ANYONE))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("APPROVAL_CLOSED"));
    verify(salesOrders, never()).confirmApprovedByCustomer(any());
  }

  @Test
  void aVersionTheOrderNoLongerRestsOnCannotBeApproved() {
    when(orders.lockByTenantIdAndId(TENANT, ORDER))
        .thenAnswer(
            invocation -> {
              SalesOrder fresh = awaitingOrder();
              fresh.startNewEvaluation();
              loadedOrders.add(fresh);
              return Optional.of(fresh);
            });

    assertThatThrownBy(() -> service.approve(APPROVAL, jane(), ANYONE))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("VERSION_STALE"));
  }

  @Test
  void onceTheRepresentativesAuthorityIsNoLongerInForceNothingIsDecided() {
    for (String reason :
        new String[] {
          ApproverAuthorities.AUTHORITY_INACTIVE, ApproverAuthorities.APPROVER_EMAIL_CHANGED
        }) {
      org.mockito.Mockito.reset(approvers);
      doThrow(ApproverAuthorities.refusal(reason)).when(approvers).holdValid(any(), any());

      // Even a check that accepts any session cannot decide past the authority.
      assertThatThrownBy(() -> service.approve(APPROVAL, jane(), ANYONE))
          .isInstanceOfSatisfying(
              OrderDomainException.class,
              exception -> assertThat(exception.getErrorCode()).isEqualTo(reason));
      assertThatThrownBy(() -> service.requestChanges(APPROVAL, jane(), "Earlier please", ANYONE))
          .isInstanceOfSatisfying(
              OrderDomainException.class,
              exception -> assertThat(exception.getErrorCode()).isEqualTo(reason));

      assertThat(lastApproval().getStatus()).isEqualTo(CustomerApprovalStatus.SENT);
      assertThat(lastOrder().getFlowStage()).isEqualTo(OrderFlowStage.AWAITING_CUSTOMER_APPROVAL);
    }
    verify(salesOrders, never()).confirmApprovedByCustomer(any());
  }

  @Test
  void theAuthorityIsHeldLockedAfterTheOrderAndBeforeTheDecision() {
    service.approve(APPROVAL, jane(), ANYONE);

    org.mockito.InOrder order = org.mockito.Mockito.inOrder(orders, approvers, salesOrders);
    order.verify(orders).lockByTenantIdAndId(TENANT, ORDER);
    order.verify(approvers).holdValid(any(), eq(NOW));
    order.verify(salesOrders).confirmApprovedByCustomer(any());
    // The unlocked check is never what a decision relies on.
    verify(approvers, never()).requireValid(any(), any());
  }

  @Test
  void aDecisionFromACustomerAccountIsRefusedUntilPersonAndAuthorityAreMatched() {
    CustomerApproval.Decider account =
        new CustomerApproval.Decider(
            CustomerApprovalChannel.CUSTOMER_ACCOUNT,
            "Jane Smith",
            "jane@example.co.uk",
            UUID.randomUUID(),
            null,
            null);

    assertThatThrownBy(() -> service.approve(APPROVAL, account, ANYONE))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CustomerApprovalDecisionService.ACCOUNT_CHANNEL_NOT_AVAILABLE));
    assertThatThrownBy(() -> service.requestChanges(APPROVAL, account, "Earlier please", ANYONE))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(CustomerApprovalDecisionService.ACCOUNT_CHANNEL_NOT_AVAILABLE));

    // Refused before anything is read or locked.
    assertThat(loadedApprovals).isEmpty();
    verify(orders, never()).lockByTenantIdAndId(any(), any());
    verify(commitments, never())
        .recordCustomerApproval(any(), any(), any(), any(), anyInt(), any(), any());
  }

  @Test
  void aChangeRequestSendsTheOrderBackToSalesWithTheNote() {
    CustomerApprovalDecisionService.Outcome outcome =
        service.requestChanges(APPROVAL, jane(), "Could the date be a week earlier?", ANYONE);

    assertThat(outcome.status()).isEqualTo(CustomerApprovalStatus.CHANGES_REQUESTED);
    assertThat(lastApproval().getCustomerNote()).isEqualTo("Could the date be a week earlier?");
    assertThat(lastApproval().hasOpenChangeRequest()).isTrue();
    assertThat(lastOrder().getFlowStage()).isEqualTo(OrderFlowStage.DRAFT);
    assertThat(lastOrder().getStatus()).isEqualTo(OrderStatus.DRAFT);
    verify(work)
        .releaseForFlow(
            ORDER, OrderWorkKind.PLANNING, "The customer asked for changes", SystemUser.ID);
    verify(salesOrders, never()).confirmApprovedByCustomer(any());
  }

  @Test
  void anApprovedVersionCannotBeTurnedIntoAChangeRequest() {
    storedStatus = CustomerApprovalStatus.APPROVED;

    assertThatThrownBy(() -> service.requestChanges(APPROVAL, jane(), "Too late", ANYONE))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("APPROVAL_CLOSED"));
  }

  @Test
  void anOrderNoLongerAwaitingTheCustomerTakesNoDecision() {
    when(orders.lockByTenantIdAndId(TENANT, ORDER))
        .thenAnswer(
            invocation -> {
              SalesOrder fresh = awaitingOrder();
              fresh.cancel();
              loadedOrders.add(fresh);
              return Optional.of(fresh);
            });

    assertThatThrownBy(() -> service.approve(APPROVAL, jane(), ANYONE))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("APPROVAL_CLOSED"));
  }
}
