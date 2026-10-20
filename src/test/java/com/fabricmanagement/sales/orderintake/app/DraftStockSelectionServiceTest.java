package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.app.*;
import com.fabricmanagement.platform.realtime.domain.*;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.core.stockunit.api.*;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.*;
import com.fabricmanagement.sales.orderintake.domain.proposal.*;
import com.fabricmanagement.sales.orderintake.dto.*;
import com.fabricmanagement.sales.orderintake.infra.repository.*;
import com.fabricmanagement.sales.salesorder.app.*;
import com.fabricmanagement.sales.salesorder.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DraftStockSelectionServiceTest {
  @Mock DraftStockSelectionRepository selections;
  @Mock DraftStockSelectionOperationRepository operations;
  @Mock LiveEditSessionService sessions;
  @Mock LiveEditLeaseService leases;
  @Mock StockUnitOwnershipPort ownership;
  @Mock StockUnitSoftHoldPort holds;
  @Mock QuantityEvaluationService evaluation;
  @Mock ProductSalesDefinitionQueryService products;
  @Mock CatalogLineValidator catalogue;
  @Mock SalesAccessScopeResolver permissions;
  @Mock com.fabricmanagement.platform.user.app.UserQueryService users;
  @Mock OrderIntakeAccess access;
  @Mock SalesOrderRevision revision;
  @Mock SalesOrderLeaseGuard editLeases;
  @Mock LineAdjustmentGuard adjustments;
  @Mock EntityManager entityManager;
  final Instant now = Instant.parse("2026-10-19T09:00:00Z");
  final UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID(), product = UUID.randomUUID();
  final UUID customer = UUID.randomUUID(), batch = UUID.randomUUID(), piece = UUID.randomUUID();
  DraftStockSelection row;
  LiveEditLease lease;
  QuantityOption option;
  DraftStockSelectionService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenant);
    row =
        DraftStockSelection.open(
            UUID.randomUUID(), UUID.randomUUID(), product, actor, UUID.randomUUID());
    row.setId(UUID.randomUUID());
    row.setTenantId(tenant);
    row.setVersion(0L);
    option = option("790");
    lease =
        LiveEditLease.grant(
            StockUnitLeaseKeys.resource(product),
            StockUnitLeaseKeys.key(piece),
            1,
            row.getSessionId(),
            actor,
            now,
            now.plusSeconds(90));
    row.hold(request(), option, List.of(lease.getToken()), lease.getExpiresAt());
    var limits = new LiveEditLeaseProperties();
    service =
        new DraftStockSelectionService(
            selections,
            operations,
            sessions,
            leases,
            limits,
            ownership,
            holds,
            evaluation,
            products,
            catalogue,
            permissions,
            users,
            access,
            revision,
            editLeases,
            adjustments,
            entityManager,
            new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(now, ZoneOffset.UTC));
    when(users.isActive(eq(tenant), any())).thenReturn(true);
    when(permissions.resolve(eq(tenant), eq(actor), eq("write"), any()))
        .thenReturn(new SalesAccessScopeResolver.AccessScope(DataScope.GLOBAL, Set.of()));
    when(selections.findByTenantIdAndId(tenant, row.getId())).thenReturn(Optional.of(row));
    when(selections.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    when(leases.held(any(), eq(1L))).thenReturn(List.of(lease));
    when(leases.verify(any(), eq(1L), any(), any(), eq(actor), any()))
        .thenReturn(new LiveEditLeaseService.Verification(List.of(lease), List.of(), Set.of()));
    var session =
        LiveEditSession.open(
            StockUnitLeaseKeys.resource(product), actor, now, Duration.ofSeconds(90));
    session.setId(row.getSessionId());
    when(sessions.live(any())).thenReturn(List.of(session));
    when(leases.replace(any(), eq(1L), any(), eq(actor), any()))
        .thenReturn(new LiveEditLeaseService.Granted(List.of(lease)));
    stock(option);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void presentedProposalAcquiresBeforeAnyCommercialApply() {
    row =
        DraftStockSelection.open(
            row.getFormInstanceId(), row.getClientLineId(), product, actor, row.getSessionId());
    row.setId(UUID.randomUUID());
    row.setVersion(0L);
    when(selections.findByTenantIdAndId(tenant, row.getId())).thenReturn(Optional.of(row));
    var result =
        service.acquire(
            row.getId(),
            new DraftStockSelectionRequests.Acquire(
                UUID.randomUUID(), 0L, request(), "chosen", List.of()),
            actor);
    assertThat(result.state()).isEqualTo(DraftStockSelectionDto.State.HELD);
    verify(leases).replace(any(), eq(1L), eq(row.getSessionId()), eq(actor), any());
    verifyNoInteractions(holds, revision);
  }

  @Test
  void refusedSwapPreservesThePreviousSelection() {
    var original = row.getOption();
    when(leases.replace(any(), eq(1L), any(), eq(actor), any()))
        .thenReturn(new LiveEditLeaseService.Refused(List.of()));
    assertThatThrownBy(() -> service.acquire(row.getId(), acquire(), actor))
        .isInstanceOf(OrderIntakeException.class);
    assertThat(row.getOption()).isSameAs(original);
    assertThat(row.getTokens()).containsExactly(lease.getToken());
    verify(selections, never()).saveAndFlush(any());
  }

  @Test
  void expiredProofCannotTransferEvenIfTheRollListStillLooksAvailable() {
    when(leases.verify(any(), eq(1L), any(), any(), eq(actor), any()))
        .thenReturn(
            new LiveEditLeaseService.Verification(
                List.of(),
                List.of(new LiveEditLeaseService.Missing(StockUnitLeaseKeys.key(piece), null)),
                Set.of()));
    assertThatThrownBy(
            () -> service.transfer(order(), line(), proof(), row.getClientLineId(), actor))
        .isInstanceOf(OrderIntakeException.class);
    verifyNoInteractions(holds);
  }

  @Test
  void belowProposalSetsCommercialQuantityWithoutManufacturingAnOpenBalance() {
    option = option("665");
    row.hold(request(), option, List.of(lease.getToken()), lease.getExpiresAt());
    stock(option);
    var line = line();
    service.transfer(order(), line, proof(), row.getClientLineId(), actor);
    assertThat(line.getRequestedQty()).isEqualByComparingTo("665");
    assertThat(line.getCustomerRequestQty()).isEqualByComparingTo("700");
    assertThat(row.getState()).isEqualTo(DraftStockSelection.State.TRANSFERRED);
    assertThat(row.getTokens()).isEmpty();
    verify(holds)
        .replaceOrderHolds(
            any(),
            any(),
            eq(line.getId()),
            eq(actor),
            eq(row.getSessionId()),
            eq(List.of(piece)),
            any());
  }

  @Test
  void transferIsWrittenBeforeTemporaryProofIsReleased() {
    var line = line();
    service.transfer(order(), line, proof(), row.getClientLineId(), actor);
    assertThat(line.getRequestedQty()).isEqualByComparingTo("790");
    assertThat(line.getCustomerRequestQty()).isEqualByComparingTo("700");
    var sequence = inOrder(holds, leases);
    sequence.verify(leases).verify(any(), eq(1L), any(), any(), eq(actor), any());
    sequence.verify(holds).replaceOrderHolds(any(), any(), any(), eq(actor), any(), any(), any());
    sequence.verify(leases).release(any(), any(), eq(actor), any());
  }

  @Test
  void lateReleaseCannotAffectACompletedTransfer() {
    row.transfer(UUID.randomUUID(), UUID.randomUUID(), "terms");
    service.release(
        row.getId(), new DraftStockSelectionRequests.Tokens(0L, List.of(lease.getToken())), actor);
    verify(leases, never()).release(any(), any(), any(), any());
    verifyNoInteractions(holds);
  }

  @Test
  void anotherActorCannotReadOrReleaseTheSession() {
    UUID other = UUID.randomUUID();
    when(permissions.resolve(eq(tenant), eq(other), eq("write"), any()))
        .thenReturn(new SalesAccessScopeResolver.AccessScope(DataScope.GLOBAL, Set.of()));
    assertThatThrownBy(() -> service.status(row.getId(), other))
        .isInstanceOf(OrderIntakeException.class);
    verify(leases, never()).release(any(), any(), any(), any());
  }

  @Test
  void readOnlyPermissionDoesNotAcquireStock() {
    when(permissions.resolve(eq(tenant), eq(actor), eq("write"), any()))
        .thenReturn(SalesAccessScopeResolver.AccessScope.denied());
    assertThatThrownBy(() -> service.acquire(row.getId(), acquire(), actor))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verifyNoInteractions(ownership, holds);
  }

  @Test
  void aDeactivatedActorCannotKeepStockAlive() {
    when(users.isActive(tenant, actor)).thenReturn(false);
    assertThatThrownBy(
            () ->
                service.renew(
                    row.getId(),
                    new DraftStockSelectionRequests.Tokens(0L, List.of(lease.getToken())),
                    actor))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verifyNoInteractions(ownership, holds);
    verify(sessions, never()).renew(any(), any(), any());
  }

  @Test
  void oldRevisionCannotChangeASelection() {
    row.setVersion(2L);
    assertThatThrownBy(() -> service.acquire(row.getId(), acquire(), actor))
        .isInstanceOf(OrderIntakeException.class);
    verify(leases, never()).replace(any(), anyLong(), any(), any(), any());
  }

  @Test
  void quantityDifferenceStillRequiresBackendAcknowledgement() {
    var unconfirmed =
        new DraftStockSelectionRequests.Proof(row.getId(), 0L, List.of(lease.getToken()), false);
    assertThatThrownBy(
            () -> service.transfer(order(), line(), unconfirmed, row.getClientLineId(), actor))
        .isInstanceOf(OrderIntakeException.class)
        .extracting(error -> ((OrderIntakeException) error).getErrorCode())
        .isEqualTo("ORDER_INTAKE_CONFIRMATION_REQUIRED");
    verifyNoInteractions(holds);
  }

  @Test
  void savedApplyRespectsAnotherEditorsQuantityLease() {
    var order = order();
    var line = line();
    when(access.writableOrderFresh(order.getId(), actor)).thenReturn(order);
    doThrow(
            new org.springframework.security.access.AccessDeniedException(
                "Quantity is being edited"))
        .when(editLeases)
        .assertLineFieldFree(order, line.getId(), OrderEditKey.LINE_QUANTITY, actor);
    assertThatThrownBy(
            () ->
                service.apply(
                    order.getId(),
                    line.getId(),
                    new DraftStockSelectionRequests.Apply(UUID.randomUUID(), 0L, proof()),
                    actor))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verifyNoInteractions(holds);
  }

  @Test
  void savedApplyRejectsAnOldOrderVersionBeforeChangingStock() {
    var order = order();
    order.setVersion(3L);
    when(access.writableOrderFresh(order.getId(), actor)).thenReturn(order);
    assertThatThrownBy(
            () ->
                service.apply(
                    order.getId(),
                    UUID.randomUUID(),
                    new DraftStockSelectionRequests.Apply(UUID.randomUUID(), 0L, proof()),
                    actor))
        .isInstanceOf(OrderIntakeException.class);
    verifyNoInteractions(holds, editLeases);
  }

  @Test
  void savedRemoveRestoresTheOriginalRequestAndReleasesOnlyThisLine() {
    var order = order();
    var line = line();
    line.applyStockQuantity(new BigDecimal("700"), new BigDecimal("790"));
    row.transfer(order.getId(), line.getId(), StockSelectionTerms.fingerprint(order, line));
    when(access.writableOrderFresh(order.getId(), actor)).thenReturn(order);
    when(access.line(order, line.getId())).thenReturn(line);
    when(selections.findByTenantIdAndSalesOrderLineIdInAndState(
            tenant, List.of(line.getId()), DraftStockSelection.State.TRANSFERRED))
        .thenReturn(List.of(row));
    var result =
        service.remove(
            order.getId(),
            line.getId(),
            new DraftStockSelectionRequests.Remove(UUID.randomUUID(), 0L),
            actor);
    assertThat(line.getRequestedQty()).isEqualByComparingTo("700");
    assertThat(result.state()).isEqualTo(DraftStockSelectionDto.State.RELEASED);
    verify(holds).releaseHolds(line.getId());
    verify(editLeases).assertLineFieldFree(order, line.getId(), OrderEditKey.LINE_QUANTITY, actor);
    verify(revision).linesChanged(order);
    verify(leases, never()).release(any(), any(), any(), any());
  }

  @Test
  void endedCommercialOwnershipCannotBeReusedAsAnotherDraftSelection() {
    row.transfer(UUID.randomUUID(), UUID.randomUUID(), "terms");
    row.release();
    assertThatThrownBy(() -> service.acquire(row.getId(), acquire(), actor))
        .isInstanceOf(OrderIntakeException.class);
    verify(leases, never()).replace(any(), anyLong(), any(), any(), any());
  }

  private DraftStockSelectionRequests.Acquire acquire() {
    return new DraftStockSelectionRequests.Acquire(
        UUID.randomUUID(), 0L, request(), "chosen", List.of(lease.getToken()));
  }

  private DraftStockSelectionRequests.Proof proof() {
    return new DraftStockSelectionRequests.Proof(row.getId(), 0L, List.of(lease.getToken()), true);
  }

  private OrderIntakeStockPreviewRequest request() {
    return new OrderIntakeStockPreviewRequest(
        product,
        null,
        null,
        null,
        customer,
        new BigDecimal("700"),
        "M",
        false,
        new BigDecimal("5"),
        new BigDecimal("5"),
        null);
  }

  private SalesOrder order() {
    var value = SalesOrder.builder().tradingPartnerId(customer).orderNumber("SO-2026-001").build();
    value.setId(UUID.randomUUID());
    value.setVersion(0L);
    return value;
  }

  private SalesOrderLine line() {
    var value =
        SalesOrderLine.builder()
            .productId(product)
            .requestedQty(new BigDecimal("700"))
            .unit("M")
            .singleLotRequired(false)
            .toleranceUpPct(new BigDecimal("5"))
            .toleranceDownPct(new BigDecimal("5"))
            .build();
    value.setId(UUID.randomUUID());
    return value;
  }

  private QuantityOption option(String metres) {
    var amount = new BigDecimal(metres);
    return QuantityOptionFixtures.option(
        "chosen",
        amount.compareTo(new BigDecimal("700")) > 0
            ? QuantityOption.OptionKind.ABOVE
            : QuantityOption.OptionKind.BELOW,
        QuantityOption.Compatibility.SINGLE_LOT,
        amount,
        amount,
        amount.subtract(new BigDecimal("700")),
        BigDecimal.ZERO,
        amount.compareTo(new BigDecimal("700")) > 0,
        List.of(
            QuantityOptionFixtures.lot(
                batch,
                "LOT-2026-001",
                List.of(piece),
                amount,
                8,
                0,
                false,
                null,
                BigDecimal.ZERO)));
  }

  private void stock(QuantityOption option) {
    var result =
        QuantityOptionFixtures.result(
            QuantityEvaluationResult.EvaluationStatus.OPTIONS,
            new BigDecimal("700"),
            "M",
            "M",
            List.of(option),
            1,
            0,
            0,
            List.of(),
            false,
            0);
    when(evaluation.run(any()))
        .thenReturn(
            new QuantityEvaluationService.EvaluationRun(
                new QuantityEvaluator.Evaluation(result, "evidence", List.of()),
                PrimaryMeasure.LENGTH,
                "M",
                null,
                null));
  }
}
