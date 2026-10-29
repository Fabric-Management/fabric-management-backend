package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.serialization.CanonicalJsonFingerprint;
import com.fabricmanagement.common.infrastructure.tenant.TenantBusinessCalendarPort;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.CustomerApproval;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalChannel;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowEvent;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionContent;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionKind;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlan;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlanRevision;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlanSettlementAck;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanInput;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.State;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Save;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Submit;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderFlowEventRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderPaymentPlanRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderPaymentPlanRevisionRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderPaymentPlanSettlementAckRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalesOrderPaymentPlanServiceTest {
  @Mock PaymentPlanStore store;
  @Mock OrderPaymentPlanRepository plans;
  @Mock OrderPaymentPlanRevisionRepository revisions;
  @Mock SalesOrderEditAccess access;
  @Mock SalesOrderRevision versions;
  @Mock OrderApprovalInvalidator invalidator;
  @Mock EntityManager em;
  @Mock DomainEventPublisher events;
  @Mock TenantBusinessCalendarPort calendar;
  @Mock OrderFlowEventRepository flowEvents;
  @Mock PaymentPlanSettlementGuard settlement;
  @Mock OrderPaymentPlanSettlementAckRepository acknowledgements;
  final UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID(), orderId = UUID.randomUUID();
  SalesOrderPaymentPlanService service;
  SalesOrder order;
  OrderPaymentPlan root;
  private jakarta.validation.ValidatorFactory validation;

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    validation = Validation.buildDefaultValidatorFactory();
    service =
        new SalesOrderPaymentPlanService(
            store,
            plans,
            revisions,
            access,
            versions,
            invalidator,
            em,
            events,
            Clock.fixed(Instant.parse("2026-10-21T09:00:00Z"), ZoneOffset.UTC),
            calendar,
            validation.getValidator(),
            flowEvents,
            settlement,
            acknowledgements);
    order =
        SalesOrder.builder().orderDate(LocalDate.of(2026, 10, 20)).orderNumber("SO-001").build();
    order.setId(orderId);
    order.setTenantId(tenant);
    root = OrderPaymentPlan.create(orderId);
    root.setId(UUID.randomUUID());
    root.setTenantId(tenant);
    when(access.requireWritable(orderId, actor)).thenReturn(order);
    when(store.plan(orderId)).thenReturn(Optional.of(root));
  }

  @AfterEach
  void cleanup() {
    validation.close();
    TenantContext.clear();
  }

  Save request() {
    return new Save(
        UUID.randomUUID(), 0L, 0L, new PaymentPlanInput(List.of()), "Customer revised dates");
  }

  @Test
  void replayIsCheckedBeforeVersionsAndNeverCreatesAnotherRevision() {
    var request = request();
    var revision =
        OrderPaymentPlanRevision.create(
            root.getId(),
            1,
            0,
            "a".repeat(64),
            request.operationId(),
            CanonicalJsonFingerprint.of(request),
            actor,
            request.changeReason(),
            List.of("PLAN_EMPTY"));
    when(revisions.findByTenantIdAndPlanIdAndOperationId(
            tenant, root.getId(), request.operationId()))
        .thenReturn(Optional.of(revision));
    order.setVersion(7L);
    service.save(orderId, request, actor);
    verify(store).dto(revision);
    verify(revisions, never()).save(any());
    verify(versions, never()).advanceOnce(any(), anyLong());
  }

  @Test
  void aReusedKeyWithDifferentInputOrActorIsRejected() {
    var request = request();
    var previous =
        OrderPaymentPlanRevision.create(
            root.getId(),
            1,
            0,
            "a".repeat(64),
            request.operationId(),
            "f".repeat(64),
            actor,
            request.changeReason(),
            List.of());
    when(revisions.findByTenantIdAndPlanIdAndOperationId(
            tenant, root.getId(), request.operationId()))
        .thenReturn(Optional.of(previous));
    assertThatThrownBy(() -> service.save(orderId, request, actor))
        .hasMessageContaining("different content");
    verify(revisions, never()).save(any());
  }

  @Test
  void staleVersionsDoNotOverwriteAnything() {
    var request = request();
    order.setVersion(3L);
    assertThatThrownBy(() -> service.save(orderId, request, actor))
        .hasMessageContaining("Reload the payment plan");
    verify(revisions, never()).save(any());
    verifyNoInteractions(invalidator);
  }

  @Test
  void incompleteDraftIsStoredAndInvalidatesTheOldOpenApproval() {
    var request = request();
    when(store.generate(order, request.paymentPlan()))
        .thenReturn(new PaymentPlanContent(null, 0, null, List.of(), List.of("PLAN_EMPTY")));
    when(store.fingerprint(order)).thenReturn("a".repeat(64));
    when(revisions.save(any()))
        .thenAnswer(
            invocation -> {
              OrderPaymentPlanRevision value = invocation.getArgument(0);
              value.setId(UUID.randomUUID());
              return value;
            });
    when(versions.advanceOnce(order, 0)).thenReturn(1L);
    service.save(orderId, request, actor);
    verify(invalidator).withdrawOpen(orderId, "The payment plan changed", actor);
    verify(store)
        .storeContent(any(), argThat(content -> content.validationIssues().contains("PLAN_EMPTY")));
    assertThat(root.getActiveAgreedRevisionId()).isNull();
    assertThat(order.isPaymentPlanManaged()).isTrue();
  }

  @Test
  void savingANewRevisionSupersedesTheDraftItReplacesAndNeverTheAgreement() {
    UUID replacedId = UUID.randomUUID();
    root.draft(replacedId);
    root.nextRevision();
    var replaced =
        OrderPaymentPlanRevision.create(
            root.getId(),
            1,
            0,
            "a".repeat(64),
            UUID.randomUUID(),
            "b".repeat(64),
            actor,
            "Earlier draft",
            List.of());
    replaced.propose(UUID.randomUUID());
    when(store.revision(root, replacedId)).thenReturn(replaced);
    when(store.content(replaced))
        .thenReturn(new PaymentPlanContent(replacedId, 1, "a".repeat(64), List.of(), List.of()));
    var request = request();
    when(store.generate(order, request.paymentPlan()))
        .thenReturn(new PaymentPlanContent(null, 0, null, List.of(), List.of("PLAN_EMPTY")));
    when(store.fingerprint(order)).thenReturn("a".repeat(64));
    when(revisions.save(any()))
        .thenAnswer(
            invocation -> {
              OrderPaymentPlanRevision value = invocation.getArgument(0);
              value.setId(UUID.randomUUID());
              return value;
            });
    service.save(orderId, request, actor);
    assertThat(replaced.getState()).isEqualTo(State.SUPERSEDED);
    assertThat(root.getCurrentDraftRevisionId()).isNotEqualTo(replacedId);
    assertThat(root.getActiveAgreedRevisionId()).isNull();
  }

  @Test
  void aPendingRevisionLeavesTheExistingAgreementInForce() {
    UUID previousId = UUID.randomUUID();
    root.draft(previousId);
    root.agree(previousId);
    root.nextRevision();
    order.setPaymentTerms("Original agreed terms");
    var previous =
        OrderPaymentPlanRevision.create(
            root.getId(),
            1,
            0,
            "a".repeat(64),
            UUID.randomUUID(),
            "b".repeat(64),
            actor,
            "Original agreement",
            List.of());
    UUID oldVersion = UUID.randomUUID();
    previous.propose(oldVersion);
    previous.agree(oldVersion, UUID.randomUUID(), Instant.parse("2026-10-20T09:00:00Z"));
    when(store.revision(root, previousId)).thenReturn(previous);
    when(store.content(previous))
        .thenReturn(new PaymentPlanContent(previousId, 1, "a".repeat(64), List.of(), List.of()));
    var request = request();
    when(store.generate(order, request.paymentPlan()))
        .thenReturn(new PaymentPlanContent(null, 0, null, List.of(), List.of("PLAN_EMPTY")));
    when(store.fingerprint(order)).thenReturn("a".repeat(64));
    when(revisions.save(any()))
        .thenAnswer(
            invocation -> {
              OrderPaymentPlanRevision value = invocation.getArgument(0);
              value.setId(UUID.randomUUID());
              return value;
            });
    service.save(orderId, request, actor);
    assertThat(root.getActiveAgreedRevisionId()).isEqualTo(previousId);
    assertThat(order.getPaymentTerms()).isEqualTo("Original agreed terms");
    assertThat(root.getCurrentDraftRevisionId()).isNotEqualTo(previousId);
    assertThat(previous.getState()).isEqualTo(State.AGREED);
    verifyNoInteractions(events);
  }

  @Test
  void agreementUsesTheActualConfirmationDayAndKeepsTheApprovalEvidence() {
    UUID revisionId = UUID.randomUUID(),
        versionId = UUID.randomUUID(),
        approvalId = UUID.randomUUID();
    root.draft(revisionId);
    order.setStatus(OrderStatus.CONFIRMED);
    var revision =
        OrderPaymentPlanRevision.create(
            root.getId(),
            1,
            0,
            "a".repeat(64),
            UUID.randomUUID(),
            "b".repeat(64),
            actor,
            "Initial agreement",
            List.of());
    revision.setId(revisionId);
    revision.propose(versionId);
    when(store.revision(root, revisionId)).thenReturn(revision);
    var content =
        new OrderVersionContent(
            null,
            "SO-001",
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
            null,
            new PaymentPlanContent(revisionId, 1, "a".repeat(64), List.of(), List.of()));
    var version =
        OrderVersion.freeze(
            orderId,
            null,
            OrderVersionKind.PAYMENT_PLAN,
            0,
            0,
            null,
            content,
            "c".repeat(64),
            actor,
            Instant.now());
    version.setId(versionId);
    var approval =
        CustomerApproval.request(
            version,
            UUID.randomUUID(),
            "Harriet Collins",
            "harriet@example.com",
            Instant.parse("2026-10-23T09:00:00Z"),
            48,
            actor,
            Instant.parse("2026-10-21T08:00:00Z"));
    approval.setId(approvalId);
    approval.issueLink("a".repeat(64), actor, Instant.parse("2026-10-21T08:00:00Z"));
    approval.approved(
        new CustomerApproval.Decider(
            CustomerApprovalChannel.EMAIL_LINK,
            "Harriet Collins",
            "harriet@example.com",
            null,
            null,
            null),
        Instant.parse("2026-10-21T09:00:00Z"));
    when(flowEvents.findFirstByTenantIdAndSalesOrderIdAndToStageOrderByOccurredAtAsc(
            tenant, orderId, OrderFlowStage.CUSTOMER_APPROVED))
        .thenReturn(
            Optional.of(
                OrderFlowEvent.of(
                    orderId,
                    OrderFlowStage.AWAITING_CUSTOMER_APPROVAL,
                    OrderFlowStage.CUSTOMER_APPROVED,
                    "Customer approved",
                    actor,
                    Instant.parse("2026-10-19T23:30:00Z"))));
    when(calendar.zone()).thenReturn(ZoneId.of("Europe/London"));
    service.agreed(order, version, approval);
    assertThat(revision.getActivationDate()).isEqualTo(LocalDate.of(2026, 10, 20));
    assertThat(revision.getAgreedApprovalId()).isEqualTo(approvalId);
    assertThat(root.getActiveAgreedRevisionId()).isEqualTo(revisionId);
    verify(events)
        .publish(
            any(
                com.fabricmanagement.sales.salesorder.domain.event.SalesOrderPaymentPlanAgreedEvent
                    .class));
  }

  @Test
  void staleOrLegacyFrozenContentCannotAgreeAChangedStructuredPlan() {
    root.draft(UUID.randomUUID());
    var content =
        new OrderVersionContent(
            null, "SO-001", null, null, null, null, null, null, null, List.of(), List.of(), 0,
            List.of(), null);
    var version =
        OrderVersion.freeze(
            orderId,
            null,
            OrderVersionKind.INFORMATION,
            0,
            0,
            null,
            content,
            "a".repeat(64),
            actor,
            Instant.now());
    assertThatThrownBy(() -> service.assertApprovalCurrent(order, version))
        .hasMessageContaining("changed after");
    verifyNoInteractions(events);
  }

  @Test
  void anAgreedCurrencyCannotBeRegeneratedFromRulesBecauseItsInstalmentsKeepTheirCash() {
    amendment(State.DRAFT);
    var rulesOnly =
        new Save(
            UUID.randomUUID(),
            0L,
            0L,
            new PaymentPlanInput(List.of(new PaymentPlanInput.CurrencyInput("GBP", null, null))),
            "Regenerate from rules");

    assertThatThrownBy(() -> service.save(orderId, rulesOnly, actor))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            refusal -> {
              assertThat(refusal.getErrorCode()).isEqualTo("PAYMENT_PLAN_AGREED_REQUIRES_ROWS");
              assertThat(refusal.getHttpStatus()).isEqualTo(409);
            });
    verify(store, never()).generate(any(), any());
    verify(revisions, never()).save(any());
  }

  @Test
  void anUnauthorisedCallerCannotReplayAnOldSuccessfulCommand() {
    when(access.requireWritable(orderId, actor))
        .thenThrow(new org.springframework.security.access.AccessDeniedException("Denied"));
    assertThatThrownBy(() -> service.save(orderId, request(), actor))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verifyNoInteractions(revisions);
  }

  private static PaymentPlanContent content(UUID line, String amount) {
    return PaymentPlanSettlementGuardTest.plan(
        PaymentPlanSettlementGuardTest.group(
            "GBP", PaymentPlanSettlementGuardTest.line(line, 1, amount)));
  }

  private record Amendment(UUID draftId, PaymentPlanContent agreed, PaymentPlanContent draft) {}

  /** An agreed plan whose only line says 100.00, and a newer revision that lowers it to 60.00. */
  private Amendment amendment(State draftState) {
    UUID agreedId = UUID.randomUUID(),
        draftId = UUID.randomUUID(),
        versionId = UUID.randomUUID(),
        lineId = UUID.randomUUID();
    order.setStatus(OrderStatus.CONFIRMED);
    root.draft(agreedId);
    root.agree(agreedId);
    root.draft(draftId);
    var agreed =
        OrderPaymentPlanRevision.create(
            root.getId(),
            1,
            0,
            "a".repeat(64),
            UUID.randomUUID(),
            "b".repeat(64),
            actor,
            "Original agreement",
            List.of());
    agreed.setId(agreedId);
    agreed.propose(versionId);
    agreed.agree(versionId, UUID.randomUUID(), Instant.parse("2026-10-20T09:00:00Z"));
    var draft =
        OrderPaymentPlanRevision.create(
            root.getId(),
            2,
            0,
            "a".repeat(64),
            UUID.randomUUID(),
            "c".repeat(64),
            actor,
            "Lower the first instalment",
            List.of());
    draft.setId(draftId);
    if (draftState == State.PROPOSED) draft.propose(UUID.randomUUID());
    when(store.revision(root, agreedId)).thenReturn(agreed);
    when(store.revision(root, draftId)).thenReturn(draft);
    var before = content(lineId, "100.00");
    var after = content(lineId, "60.00");
    when(store.content(agreed)).thenReturn(before);
    when(store.content(draft)).thenReturn(after);
    return new Amendment(draftId, before, after);
  }

  private Submit submit(Boolean acknowledged) {
    return new Submit(order.getVersion(), root.getVersion(), null, acknowledged);
  }

  private void financeSays(Amendment change, int shortfalls) {
    var found = new java.util.ArrayList<PaymentPlanSettlementGuard.Shortfall>();
    for (int i = 0; i < shortfalls; i++)
      found.add(
          new PaymentPlanSettlementGuard.Shortfall(
              UUID.randomUUID(),
              "GBP",
              new BigDecimal("80.00"),
              new BigDecimal("100.00"),
              new BigDecimal("60.00")));
    when(settlement.shortfalls(tenant, orderId, change.agreed(), change.draft())).thenReturn(found);
  }

  @Test
  void anAmendmentThatLowersCollectedCashIsRefusedUntilTheSenderAcknowledgesIt() {
    var change = amendment(State.DRAFT);
    financeSays(change, 1);
    for (Boolean missing : new Boolean[] {null, Boolean.FALSE}) {
      var request = submit(missing);
      assertThatThrownBy(() -> service.requireSubmission(orderId, change.draftId(), request, actor))
          .isInstanceOfSatisfying(
              OrderDomainException.class,
              refusal -> {
                assertThat(refusal.getErrorCode()).isEqualTo("PAYMENT_PLAN_REDUCES_SETTLED");
                assertThat(refusal.getHttpStatus()).isEqualTo(409);
              });
    }
  }

  @Test
  void anAcknowledgedAmendmentIsSubmittedAndNeverChangesTheRevision() {
    var change = amendment(State.DRAFT);
    financeSays(change, 2);
    var revision = service.requireSubmission(orderId, change.draftId(), submit(true), actor);
    assertThat(revision.getId()).isEqualTo(change.draftId());
    assertThat(revision.getState()).isEqualTo(State.DRAFT);
  }

  @Test
  void anAcknowledgedAmendmentLeavesADurableRecordOfWhoSaidSoAndWhichObligations() {
    var change = amendment(State.DRAFT);
    financeSays(change, 2);
    service.requireSubmission(orderId, change.draftId(), submit(true), actor);
    var saved = org.mockito.ArgumentCaptor.forClass(OrderPaymentPlanSettlementAck.class);
    verify(acknowledgements).save(saved.capture());
    var ack = saved.getValue();
    assertThat(ack.getSalesOrderId()).isEqualTo(orderId);
    assertThat(ack.getPlanId()).isEqualTo(root.getId());
    assertThat(ack.getRevisionId()).isEqualTo(change.draftId());
    assertThat(ack.getActorId()).isEqualTo(actor);
    assertThat(ack.getAcknowledgedAt()).isEqualTo(Instant.parse("2026-10-21T09:00:00Z"));
    assertThat(ack.getObligationCount()).isEqualTo(2);
    assertThat(ack.getShortfalls())
        .allSatisfy(
            shortfall -> {
              assertThat(shortfall.currency()).isEqualTo("GBP");
              assertThat(shortfall.settled()).isEqualByComparingTo("80.00");
              assertThat(shortfall.agreed()).isEqualByComparingTo("100.00");
              assertThat(shortfall.proposed()).isEqualByComparingTo("60.00");
            });
  }

  @Test
  void aRevisionThatAlreadyHasItsAcknowledgementIsNeverRecordedTwice() {
    var change = amendment(State.DRAFT);
    financeSays(change, 1);
    when(acknowledgements.existsByTenantIdAndRevisionId(tenant, change.draftId())).thenReturn(true);
    service.requireSubmission(orderId, change.draftId(), submit(true), actor);
    verify(acknowledgements, never()).save(any());
  }

  @Test
  void aRefusedOrUnneededAcknowledgementLeavesNoRecord() {
    var change = amendment(State.DRAFT);
    financeSays(change, 1);
    assertThatThrownBy(
            () -> service.requireSubmission(orderId, change.draftId(), submit(null), actor))
        .isInstanceOf(OrderDomainException.class);
    financeSays(change, 0);
    service.requireSubmission(orderId, change.draftId(), submit(true), actor);
    verify(acknowledgements, never()).save(any());
  }

  @Test
  void anAmendmentThatLowersNothingBelowCashNeedsNoAcknowledgement() {
    var change = amendment(State.DRAFT);
    financeSays(change, 0);
    assertThat(service.requireSubmission(orderId, change.draftId(), submit(null), actor).getId())
        .isEqualTo(change.draftId());
  }

  private PaymentPlanSettlementGuard.Shortfall below(UUID obligation, String settled) {
    return new PaymentPlanSettlementGuard.Shortfall(
        obligation,
        "GBP",
        new BigDecimal(settled),
        new BigDecimal("100.00"),
        new BigDecimal("60.00"));
  }

  private void acknowledged(Amendment change, UUID obligation, String settled) {
    when(acknowledgements.findByTenantIdAndRevisionId(tenant, change.draftId()))
        .thenReturn(
            java.util.Optional.of(
                OrderPaymentPlanSettlementAck.create(
                    orderId,
                    root.getId(),
                    change.draftId(),
                    actor,
                    Instant.parse("2026-10-21T09:00:00Z"),
                    List.of(
                        new OrderPaymentPlanSettlementAck.Shortfall(
                            obligation,
                            "GBP",
                            new BigDecimal(settled),
                            new BigDecimal("100.00"),
                            new BigDecimal("60.00"))))));
  }

  private void requireAcknowledged(Amendment change) {
    service.requireAcknowledged(orderId, root, store.revision(root, change.draftId()));
  }

  @Test
  void anAgreementThatLowersNothingBelowCashNeverLooksForAnAcknowledgement() {
    var change = amendment(State.PROPOSED);
    financeSays(change, 0);

    requireAcknowledged(change);

    verifyNoInteractions(acknowledgements);
  }

  @Test
  void cashRecordedAfterSubmissionCannotBeAgreedWithoutTheSendersAcknowledgement() {
    var change = amendment(State.PROPOSED);
    var obligation = UUID.randomUUID();
    when(settlement.shortfalls(tenant, orderId, change.agreed(), change.draft()))
        .thenReturn(List.of(below(obligation, "80.00")));

    assertThatThrownBy(() -> requireAcknowledged(change))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            refusal -> {
              assertThat(refusal.getErrorCode()).isEqualTo("PAYMENT_PLAN_REDUCES_SETTLED");
              assertThat(refusal.getHttpStatus()).isEqualTo(409);
              assertThat(refusal.getDetails()).containsEntry("obligationCount", 1L);
            });
  }

  @Test
  void whatTheSenderAcknowledgedStaysAgreeableWhileTheCashIsNotAboveIt() {
    var change = amendment(State.PROPOSED);
    var obligation = UUID.randomUUID();
    acknowledged(change, obligation, "80.00");
    for (String cash : List.of("80.00", "70.00")) {
      when(settlement.shortfalls(tenant, orderId, change.agreed(), change.draft()))
          .thenReturn(List.of(below(obligation, cash)));

      requireAcknowledged(change);
    }
  }

  @Test
  void cashThatGrewPastWhatWasAcknowledgedOrHitAnotherObligationIsRefused() {
    var change = amendment(State.PROPOSED);
    var obligation = UUID.randomUUID();
    acknowledged(change, obligation, "80.00");
    when(settlement.shortfalls(tenant, orderId, change.agreed(), change.draft()))
        .thenReturn(List.of(below(obligation, "90.00")));
    assertThatThrownBy(() -> requireAcknowledged(change)).isInstanceOf(OrderDomainException.class);

    when(settlement.shortfalls(tenant, orderId, change.agreed(), change.draft()))
        .thenReturn(List.of(below(obligation, "80.00"), below(UUID.randomUUID(), "10.00")));
    assertThatThrownBy(() -> requireAcknowledged(change))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            refusal -> assertThat(refusal.getDetails()).containsEntry("obligationCount", 1L));
  }

  private OrderVersion frozenPlan(PaymentPlanContent plan) {
    var content =
        new OrderVersionContent(
            null, "SO-001", null, null, null, null, null, null, null, List.of(), List.of(), 0,
            List.of(), null, plan);
    var version =
        OrderVersion.freeze(
            orderId,
            null,
            plan == null ? OrderVersionKind.INFORMATION : OrderVersionKind.PAYMENT_PLAN,
            0,
            0,
            null,
            content,
            "c".repeat(64),
            actor,
            Instant.now());
    version.setId(UUID.randomUUID());
    return version;
  }

  @Test
  void theApprovalAskedBeforeItIsRecordedSaysWhyCashHasOvertakenTheAcknowledgement() {
    var change = amendment(State.PROPOSED);
    var obligation = UUID.randomUUID();
    when(settlement.shortfalls(tenant, orderId, change.agreed(), change.draft()))
        .thenReturn(List.of(below(obligation, "80.00")));
    var version = frozenPlan(change.draft());

    assertThat(service.unacknowledgedReduction(order, version))
        .isEqualTo(SalesOrderPaymentPlanService.UNACKNOWLEDGED_REDUCTION);
  }

  @Test
  void theApprovalAskedBeforeItIsRecordedPassesWhenNothingIsLoweredOrEverythingWasAcknowledged() {
    var change = amendment(State.PROPOSED);
    var version = frozenPlan(change.draft());
    financeSays(change, 0);
    assertThat(service.unacknowledgedReduction(order, version)).isNull();

    var obligation = UUID.randomUUID();
    acknowledged(change, obligation, "80.00");
    when(settlement.shortfalls(tenant, orderId, change.agreed(), change.draft()))
        .thenReturn(List.of(below(obligation, "80.00")));
    assertThat(service.unacknowledgedReduction(order, version)).isNull();
  }

  @Test
  void aVersionWithoutAStructuredPlanNeverAsksFinance() {
    var version = frozenPlan(null);

    assertThat(service.unacknowledgedReduction(order, version)).isNull();
    verifyNoInteractions(settlement, acknowledgements);
  }

  @Test
  void aFirstPlanNeverAsksFinanceBecauseNothingCanHaveBeenCollected() {
    UUID draftId = UUID.randomUUID();
    order.setStatus(OrderStatus.CONFIRMED);
    root.draft(draftId);
    var draft =
        OrderPaymentPlanRevision.create(
            root.getId(),
            1,
            0,
            "a".repeat(64),
            UUID.randomUUID(),
            "b".repeat(64),
            actor,
            "First plan",
            List.of());
    draft.setId(draftId);
    when(store.revision(root, draftId)).thenReturn(draft);
    assertThat(service.requireSubmission(orderId, draftId, submit(null), actor)).isSameAs(draft);
    verifyNoInteractions(settlement);
  }

  @Test
  void aRepeatOfAnAlreadySubmittedRevisionObservesItWithoutAskingFinanceAgain() {
    var change = amendment(State.PROPOSED);
    financeSays(change, 1);
    assertThat(service.requireSubmission(orderId, change.draftId(), submit(null), actor).getId())
        .isEqualTo(change.draftId());
    verifyNoInteractions(settlement);
  }

  @Test
  void theSubmitCapabilityNamesTheReductionSoTheClientCanAskForTheAcknowledgement() {
    var change = amendment(State.DRAFT);
    financeSays(change, 1);
    when(em.find(SalesOrder.class, orderId)).thenReturn(order);
    var capability =
        service.read(orderId, actor).capabilities().stream()
            .filter(value -> value.action().equals("SUBMIT_FOR_APPROVAL"))
            .findFirst()
            .orElseThrow();
    assertThat(capability.allowed()).isFalse();
    assertThat(capability.reason()).isEqualTo("PAYMENT_PLAN_REDUCES_SETTLED");
  }

  @Test
  void theSubmitCapabilityIsOpenWhenNothingFallsBelowCash() {
    var change = amendment(State.DRAFT);
    financeSays(change, 0);
    when(em.find(SalesOrder.class, orderId)).thenReturn(order);
    var open =
        service.read(orderId, actor).capabilities().stream()
            .filter(value -> value.action().equals("SUBMIT_FOR_APPROVAL"))
            .findFirst()
            .orElseThrow();
    assertThat(open.allowed()).isTrue();
    assertThat(open.reason()).isNull();
  }

  @Test
  void aRevisionThatWasAlreadySubmittedIsNotCheckedAgainByTheCapability() {
    var change = amendment(State.PROPOSED);
    financeSays(change, 1);
    when(em.find(SalesOrder.class, orderId)).thenReturn(order);
    var submitted =
        service.read(orderId, actor).capabilities().stream()
            .filter(value -> value.action().equals("SUBMIT_FOR_APPROVAL"))
            .findFirst()
            .orElseThrow();
    assertThat(submitted.reason()).isNull();
    verifyNoInteractions(settlement);
  }
}
