package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.orderintake.domain.CustomerProductRequest;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestStatus;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerProductRequestRepository;
import com.fabricmanagement.sales.salesorder.domain.CustomerApproval;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalStatus;
import com.fabricmanagement.sales.salesorder.domain.DeliveryProposal;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerms;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionContent;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionKind;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.CustomerApprovalDtos;
import com.fabricmanagement.sales.salesorder.infra.repository.CustomerApprovalRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.DeliveryProposalRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderFlowEventRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderVersionRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.math.BigDecimal;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/** Sales sends the order for the customer's approval; the internal approval comes first. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerApprovalServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-02T09:00:00Z");
  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID SALES = UUID.randomUUID();

  @Mock private SalesOrderRepository orders;
  @Mock private SalesOrderLineRepository lines;
  @Mock private DeliveryProposalRepository proposals;
  @Mock private OrderVersionRepository versions;
  @Mock private CustomerApprovalRepository approvals;
  @Mock private CustomerProductRequestRepository requests;
  @Mock private SalesOrderAccessPolicy accessPolicy;
  @Mock private SpELPermissionEvaluator permissions;
  @Mock private TradingPartnerService partners;
  @Mock private OrderVersionSnapshotter snapshotter;
  @Mock private CustomerApprovalMailer mailer;
  @Mock private ApprovalPort approvalPort;
  @Mock private OrderIntakeHooks intake;
  @Mock private OrderApprovalInvalidator invalidator;
  @Mock private OrderFlowEventRepository events;
  @Mock private ApproverAuthorities approvers;

  private static final UUID AUTHORITY = UUID.randomUUID();

  private final List<CustomerApproval> saved = new ArrayList<>();
  private CustomerApprovalService service;
  private SalesOrder order;
  private DeliveryProposal proposal;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service =
        new CustomerApprovalService(
            orders,
            lines,
            proposals,
            versions,
            approvals,
            requests,
            accessPolicy,
            permissions,
            partners,
            snapshotter,
            mailer,
            approvalPort,
            intake,
            invalidator,
            new OrderFlowRecorder(events, clock),
            clock,
            approvers);
    order =
        SalesOrder.builder()
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-1")
            .flowStage(OrderFlowStage.PLANNED)
            .planningRound(1)
            .contactName("Jane Smith")
            .contactEmail("jane@example.co.uk")
            .orderDate(LocalDate.of(2026, 10, 1))
            .build();
    ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(order, "isActive", true);
    order.applyDeliveryTerms(
        DeliveryTerms.of(DeliveryTerm.FCA, "Felixstowe", IncotermsVersion.INCOTERMS_2020));
    proposal = proposal(NOW.plus(Duration.ofDays(5)));

    // The order's designated approver holds a valid authority (ADR-0014 D4).
    when(approvers.resolve(any()))
        .thenReturn(
            new ApproverAuthorities.Resolution(
                new ApproverAuthorities.Approver(AUTHORITY, "Jane Smith", "jane@example.co.uk"),
                null));
    // The request's authority stays in force unless a test says otherwise.
    when(approvers.block(any(), any())).thenReturn(null);
    when(orders.findByTenantIdAndId(TENANT, order.getId())).thenReturn(Optional.of(order));
    when(orders.lockByTenantIdAndId(TENANT, order.getId())).thenReturn(Optional.of(order));
    when(accessPolicy.canRead(TENANT, SALES, order)).thenReturn(true);
    when(accessPolicy.canWrite(TENANT, SALES, order)).thenReturn(true);
    when(lines.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId()))
        .thenReturn(List.of(line()));
    when(proposals.findFirstByTenantIdAndSalesOrderIdOrderBySequenceDesc(TENANT, order.getId()))
        .thenAnswer(invocation -> Optional.of(proposal));
    when(intake.confirmationBlockers(any(), any())).thenReturn(List.of());
    when(snapshotter.snapshot(eq(TENANT), eq(order), any()))
        .thenReturn(new OrderVersionSnapshotter.Snapshot(content(), "f".repeat(64)));
    when(versions.save(any(OrderVersion.class)))
        .thenAnswer(
            invocation -> {
              OrderVersion version = invocation.getArgument(0);
              ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
              when(versions.findByTenantIdAndId(TENANT, version.getId()))
                  .thenReturn(Optional.of(version));
              return version;
            });
    when(approvals.save(any(CustomerApproval.class)))
        .thenAnswer(
            invocation -> {
              CustomerApproval approval = invocation.getArgument(0);
              if (approval.getId() == null) {
                ReflectionTestUtils.setField(approval, "id", UUID.randomUUID());
              }
              if (!saved.contains(approval)) {
                saved.add(approval);
              }
              return approval;
            });
    when(approvals.findByTenantIdAndSalesOrderIdAndStatusIn(eq(TENANT), eq(order.getId()), any()))
        .thenAnswer(
            invocation -> saved.stream().filter(value -> !value.getStatus().isClosed()).toList());
    when(approvals.findByTenantIdAndSalesOrderIdOrderByRequestedAtDesc(TENANT, order.getId()))
        .thenAnswer(invocation -> List.copyOf(saved));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  private DeliveryProposal proposal(Instant validUntil) {
    DeliveryProposal value =
        DeliveryProposal.propose(
            order.getId(),
            null,
            1,
            0,
            LocalDate.of(2026, 11, 20),
            validUntil,
            order.getDeliveryTerms(),
            null,
            UUID.randomUUID(),
            NOW.minusSeconds(60),
            LocalDate.of(2026, 10, 2));
    ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
    return value;
  }

  private SalesOrderLine line() {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .salesOrderId(order.getId())
            .productId(UUID.randomUUID())
            .requestedQty(BigDecimal.TEN)
            .unit("MT")
            .build();
    ReflectionTestUtils.setField(line, "isActive", true);
    return line;
  }

  private static OrderVersionContent content() {
    return new OrderVersionContent(
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
        new OrderVersionContent.Proposal(
            UUID.randomUUID(), LocalDate.of(2026, 11, 20), null, null));
  }

  private void policyRequiresApproval(boolean required) {
    when(approvalPort.requiresApproval(
            any(),
            any(),
            any(),
            any(),
            org.mockito.ArgumentMatchers
                .<java.util.List<com.fabricmanagement.common.util.Money>>any()))
        .thenReturn(required);
  }

  private CustomerApproval only() {
    assertThat(saved).hasSize(1);
    return saved.getFirst();
  }

  @Test
  void withoutAnInternalApprovalTheLinkGoesToTheContactAtOnce() {
    policyRequiresApproval(false);

    service.sendForApproval(order.getId(), new CustomerApprovalDtos.SendForApproval(24), SALES);

    CustomerApproval approval = only();
    assertThat(approval.getStatus()).isEqualTo(CustomerApprovalStatus.SENT);
    assertThat(approval.getRecipientEmail()).isEqualTo("jane@example.co.uk");
    assertThat(approval.getLinkExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.AWAITING_CUSTOMER_APPROVAL);
    ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
    verify(mailer)
        .sendApprovalRequest(
            eq(TENANT),
            eq("jane@example.co.uk"),
            eq("Jane Smith"),
            any(),
            token.capture(),
            eq(approval.getLinkExpiresAt()));
    // Only the hash of the link is kept.
    assertThat(approval.getTokenHash()).isEqualTo(OrderVersionSnapshotter.sha256(token.getValue()));
    verify(invalidator).resolveChangeRequests(order.getId());
    ArgumentCaptor<OrderVersion> version = ArgumentCaptor.forClass(OrderVersion.class);
    verify(versions).save(version.capture());
    assertThat(version.getValue().getKind()).isEqualTo(OrderVersionKind.APPROVAL);
    assertThat(version.getValue().getDeliveryProposalId()).isEqualTo(proposal.getId());
  }

  @Test
  void theInternalApprovalComesBeforeAnythingReachesTheCustomer() {
    policyRequiresApproval(true);
    UUID request = UUID.randomUUID();
    when(approvalPort.pendingRequestId(TENANT, "SALES_ORDER", order.getId()))
        .thenReturn(Optional.of(request));

    service.sendForApproval(order.getId(), null, SALES);

    CustomerApproval approval = only();
    assertThat(approval.getStatus()).isEqualTo(CustomerApprovalStatus.AWAITING_INTERNAL_APPROVAL);
    assertThat(approval.getInternalApprovalRequestId()).isEqualTo(request);
    assertThat(approval.getTokenHash()).isNull();
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.AWAITING_INTERNAL_APPROVAL);
    verify(mailer, never()).sendApprovalRequest(any(), any(), any(), any(), any(), any());
    // An earlier version's pending approval never carries over.
    verify(approvalPort).cancelPending(TENANT, "SALES_ORDER", order.getId());
  }

  @Test
  void theApprovedVersionIsSentWhenTheManagerApproves() {
    policyRequiresApproval(true);
    UUID request = UUID.randomUUID();
    when(approvalPort.pendingRequestId(TENANT, "SALES_ORDER", order.getId()))
        .thenReturn(Optional.of(request));
    service.sendForApproval(order.getId(), null, SALES);
    when(approvals.findByTenantIdAndInternalApprovalRequestId(TENANT, request))
        .thenReturn(Optional.of(only()));

    service.onInternalApproval(request);

    assertThat(only().getStatus()).isEqualTo(CustomerApprovalStatus.SENT);
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.AWAITING_CUSTOMER_APPROVAL);
    verify(mailer)
        .sendApprovalRequest(
            eq(TENANT), eq("jane@example.co.uk"), any(), any(), anyString(), any());
  }

  @Test
  void aProposalThatExpiredDuringTheInternalApprovalGoesBackToPlanning() {
    proposal = proposal(NOW.plus(Duration.ofHours(1)));
    policyRequiresApproval(true);
    UUID request = UUID.randomUUID();
    when(approvalPort.pendingRequestId(TENANT, "SALES_ORDER", order.getId()))
        .thenReturn(Optional.of(request));
    service.sendForApproval(order.getId(), null, SALES);
    when(approvals.findByTenantIdAndInternalApprovalRequestId(TENANT, request))
        .thenReturn(Optional.of(only()));
    Clock later = Clock.fixed(NOW.plus(Duration.ofHours(2)), ZoneOffset.UTC);
    ReflectionTestUtils.setField(service, "clock", later);

    service.onInternalApproval(request);

    assertThat(only().getStatus()).isEqualTo(CustomerApprovalStatus.WITHDRAWN);
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.IN_PLANNING);
    assertThat(order.getPlanningEvaluation()).isEqualTo(1);
    verify(mailer, never()).sendApprovalRequest(any(), any(), any(), any(), any(), any());
  }

  @Test
  void aDeclinedInternalApprovalReturnsTheOrderToPlannedWithoutSending() {
    policyRequiresApproval(true);
    UUID request = UUID.randomUUID();
    when(approvalPort.pendingRequestId(TENANT, "SALES_ORDER", order.getId()))
        .thenReturn(Optional.of(request));
    service.sendForApproval(order.getId(), null, SALES);
    when(approvals.findByTenantIdAndInternalApprovalRequestId(TENANT, request))
        .thenReturn(Optional.of(only()));

    service.onInternalRejection(request, "Discount too deep");

    assertThat(only().getStatus()).isEqualTo(CustomerApprovalStatus.INTERNAL_REJECTED);
    assertThat(only().getInternalRejectionReason()).isEqualTo("Discount too deep");
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.PLANNED);
    verify(mailer, never()).sendApprovalRequest(any(), any(), any(), any(), any(), any());
  }

  @Test
  void anApprovalOfAnUnknownOrCancelledRequestChangesNothing() {
    service.onInternalApproval(UUID.randomUUID());

    assertThat(saved).isEmpty();
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.PLANNED);
  }

  @Test
  void onlyAPlannedOrderIsSentForApproval() {
    ReflectionTestUtils.setField(order, "flowStage", OrderFlowStage.IN_PLANNING);

    assertThatThrownBy(() -> service.sendForApproval(order.getId(), null, SALES))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("WRONG_STAGE"));
    assertThat(saved).isEmpty();
  }

  @Test
  void anExpiredProposalIsNotOfferedToTheCustomer() {
    proposal = proposal(NOW.plus(Duration.ofDays(1)));
    ReflectionTestUtils.setField(proposal, "validUntil", NOW.minusSeconds(1));

    assertThatThrownBy(() -> service.sendForApproval(order.getId(), null, SALES))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("PROPOSAL_EXPIRED"));
    verify(versions, never()).save(any());
  }

  @Test
  void aProposalMadeBeforeTheEvaluationWasReopenedIsStale() {
    order.startNewEvaluation();

    assertThatThrownBy(() -> service.sendForApproval(order.getId(), null, SALES))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("PROPOSAL_STALE"));
  }

  @Test
  void anOrderWithAnOpenCustomRequestIsNotSentForApproval() {
    CustomerProductRequest request = org.mockito.Mockito.mock(CustomerProductRequest.class);
    when(request.getStatus()).thenReturn(CustomerRequestStatus.OPEN);
    when(requests.findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByRecordedAtAscIdAsc(
            TENANT, order.getId()))
        .thenReturn(List.of(request));

    assertThatThrownBy(() -> service.sendForApproval(order.getId(), null, SALES))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("OPEN_CUSTOM_REQUESTS"));
  }

  @Test
  void anOrderThatCannotBeConfirmedIsNotSentForApproval() {
    when(intake.confirmationBlockers(any(), any())).thenReturn(List.of("PIECE_TAKEN"));

    assertThatThrownBy(() -> service.sendForApproval(order.getId(), null, SALES))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("NOT_CONFIRMABLE"));
  }

  @Test
  void aUserWithoutAccessToTheOrderCannotSendIt() {
    when(accessPolicy.canWrite(TENANT, SALES, order)).thenReturn(false);

    assertThatThrownBy(() -> service.sendForApproval(order.getId(), null, SALES))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(saved).isEmpty();
  }

  @Test
  void aResendReplacesTheLinkForTheSameRepresentativeAtTheAuthorisedAddress() {
    policyRequiresApproval(false);
    service.sendForApproval(order.getId(), null, SALES);
    String first = only().getTokenHash();

    service.resend(order.getId(), SALES);

    assertThat(only().getTokenHash()).isNotEqualTo(first);
    assertThat(only().getApprovalAuthorityId()).isEqualTo(AUTHORITY);
    assertThat(only().getRecipientName()).isEqualTo("Jane Smith");
    assertThat(only().getRecipientEmail()).isEqualTo("jane@example.co.uk");
    assertThat(only().getLinksIssued()).isEqualTo(2);
    verify(mailer, times(2))
        .sendApprovalRequest(eq(TENANT), eq("jane@example.co.uk"), any(), any(), any(), any());
    // Each send holds the authority's lock while the link is issued.
    verify(approvers, times(2)).holdValid(any(), any());
  }

  @Test
  void anAddressChangedOnTheCustomersCardStopsTheResendUntilTheAuthorityIsGrantedAgain() {
    policyRequiresApproval(false);
    service.sendForApproval(order.getId(), null, SALES);
    String first = only().getTokenHash();
    // Someone edited Jane's contact point: it now reads another address.
    when(approvers.block(any(), any())).thenReturn(ApproverAuthorities.APPROVER_EMAIL_CHANGED);

    assertThatThrownBy(() -> service.resend(order.getId(), SALES))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ApproverAuthorities.APPROVER_EMAIL_CHANGED));
    assertThat(only().getTokenHash()).isEqualTo(first);
    assertThat(only().getRecipientEmail()).isEqualTo("jane@example.co.uk");
    // Only the first request went out, and never to any other address.
    verify(mailer).sendApprovalRequest(any(), any(), any(), any(), any(), any());
  }

  @Test
  void aRevocationThatCameFirstStopsTheSendHeldUnderTheLock() {
    policyRequiresApproval(false);
    doThrow(ApproverAuthorities.refusal(ApproverAuthorities.AUTHORITY_INACTIVE))
        .when(approvers)
        .holdValid(any(), any());

    assertThatThrownBy(() -> service.sendForApproval(order.getId(), null, SALES))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo("APPROVAL_AUTHORITY_INACTIVE"));
    verify(mailer, never()).sendApprovalRequest(any(), any(), any(), any(), any(), any());
  }

  @Test
  void theRequestGoesToTheAuthorisedApproverNotToTheOrdersContact() {
    policyRequiresApproval(false);
    ReflectionTestUtils.setField(order, "contactEmail", "buyer@example.co.uk");

    service.sendForApproval(order.getId(), null, SALES);

    assertThat(only().getApprovalAuthorityId()).isEqualTo(AUTHORITY);
    assertThat(only().getRecipientEmail()).isEqualTo("jane@example.co.uk");
    verify(mailer, never())
        .sendApprovalRequest(any(), eq("buyer@example.co.uk"), any(), any(), any(), any());
  }

  @Test
  void withoutAnAuthorisedApproverNothingIsSent() {
    policyRequiresApproval(false);
    when(approvers.resolve(any()))
        .thenReturn(ApproverAuthorities.Resolution.blocked(ApproverAuthorities.AUTHORITY_INACTIVE));

    assertThatThrownBy(() -> service.sendForApproval(order.getId(), null, SALES))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo("APPROVAL_AUTHORITY_INACTIVE"));
    assertThat(saved).isEmpty();
    verify(mailer, never()).sendApprovalRequest(any(), any(), any(), any(), any(), any());
  }

  @Test
  void noNewLinkOnceTheRequestsAuthorityHasEnded() {
    policyRequiresApproval(false);
    service.sendForApproval(order.getId(), null, SALES);
    String first = only().getTokenHash();
    when(approvers.block(any(), any())).thenReturn(ApproverAuthorities.AUTHORITY_INACTIVE);

    assertThatThrownBy(() -> service.resend(order.getId(), SALES))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo("APPROVAL_AUTHORITY_INACTIVE"));
    assertThat(only().getTokenHash()).isEqualTo(first);
  }

  @Test
  void anAuthorityThatEndedDuringTheInternalApprovalStopsTheSend() {
    policyRequiresApproval(true);
    UUID request = UUID.randomUUID();
    when(approvalPort.pendingRequestId(TENANT, "SALES_ORDER", order.getId()))
        .thenReturn(Optional.of(request));
    service.sendForApproval(order.getId(), null, SALES);
    when(approvals.findByTenantIdAndInternalApprovalRequestId(TENANT, request))
        .thenReturn(Optional.of(only()));
    when(approvers.block(any(), any())).thenReturn(ApproverAuthorities.AUTHORITY_INACTIVE);

    service.onInternalApproval(request);

    assertThat(only().getStatus()).isEqualTo(CustomerApprovalStatus.WITHDRAWN);
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.PLANNED);
    verify(mailer, never()).sendApprovalRequest(any(), any(), any(), any(), any(), any());
  }

  @Test
  void anAddressChangedDuringTheInternalApprovalStopsTheSend() {
    policyRequiresApproval(true);
    UUID request = UUID.randomUUID();
    when(approvalPort.pendingRequestId(TENANT, "SALES_ORDER", order.getId()))
        .thenReturn(Optional.of(request));
    service.sendForApproval(order.getId(), null, SALES);
    when(approvals.findByTenantIdAndInternalApprovalRequestId(TENANT, request))
        .thenReturn(Optional.of(only()));
    when(approvers.block(any(), any())).thenReturn(ApproverAuthorities.APPROVER_EMAIL_CHANGED);

    service.onInternalApproval(request);

    assertThat(only().getStatus()).isEqualTo(CustomerApprovalStatus.WITHDRAWN);
    assertThat(only().getClosedReason()).contains("e-mail address changed");
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.PLANNED);
    verify(mailer, never()).sendApprovalRequest(any(), any(), any(), any(), any(), any());
  }

  @Test
  void theDraftsDetailsGoOutForInformationWithoutAnyApproval() {
    ReflectionTestUtils.setField(order, "flowStage", OrderFlowStage.DRAFT);

    service.sendInformation(order.getId(), SALES);

    ArgumentCaptor<OrderVersion> version = ArgumentCaptor.forClass(OrderVersion.class);
    verify(versions).save(version.capture());
    assertThat(version.getValue().getKind()).isEqualTo(OrderVersionKind.INFORMATION);
    assertThat(version.getValue().getDeliveryProposalId()).isNull();
    verify(mailer).sendInformation(eq(TENANT), eq("jane@example.co.uk"), eq("Jane Smith"), any());
    assertThat(saved).isEmpty();
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.DRAFT);
  }

  @Test
  void theStateOffersOnlyWhatTheBackendAllows() {
    when(permissions.can(any(), eq("sales"), eq("write"))).thenReturn(true);
    policyRequiresApproval(false);

    CustomerApprovalDtos.State before = service.state(order.getId(), SALES);
    service.sendForApproval(order.getId(), null, SALES);
    CustomerApprovalDtos.State after = service.state(order.getId(), SALES);

    assertThat(reason(before, CustomerApprovalDtos.Action.SEND_FOR_APPROVAL)).isNull();
    assertThat(reason(before, CustomerApprovalDtos.Action.RESEND_LINK)).isEqualTo("WRONG_STAGE");
    assertThat(reason(after, CustomerApprovalDtos.Action.SEND_FOR_APPROVAL))
        .isEqualTo("WRONG_STAGE");
    assertThat(reason(after, CustomerApprovalDtos.Action.SEND_INFORMATION))
        .isEqualTo("WRONG_STAGE");
    assertThat(reason(after, CustomerApprovalDtos.Action.RESEND_LINK)).isNull();
    assertThat(after.approvals())
        .singleElement()
        .satisfies(
            view -> {
              assertThat(view.status()).isEqualTo(CustomerApprovalStatus.SENT);
              assertThat(view.linkExpired()).isFalse();
            });
  }

  @Test
  void withoutTheWritePermissionNothingIsOffered() {
    when(permissions.can(any(), eq("sales"), eq("write"))).thenReturn(false);

    CustomerApprovalDtos.State state = service.state(order.getId(), SALES);

    assertThat(state.actions())
        .allSatisfy(action -> assertThat(action.reason()).isEqualTo("PERMISSION_DENIED"));
  }

  private static String reason(CustomerApprovalDtos.State state, CustomerApprovalDtos.Action of) {
    return state.actions().stream()
        .filter(action -> action.action() == of)
        .findFirst()
        .orElseThrow()
        .reason();
  }
}
