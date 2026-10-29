package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.finance.collection.app.CollectionProjectionService.Refresh;
import com.fabricmanagement.finance.collection.domain.CollectionObligation;
import com.fabricmanagement.finance.collection.domain.CollectionRefund;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Kind;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Method;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.RefundState;
import com.fabricmanagement.finance.collection.dto.CollectionDtos;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeApplicationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeEventRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionChequeRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionExpectationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionObligationRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionPaymentMatchRepository;
import com.fabricmanagement.finance.collection.infra.repository.CollectionProjectionRepository;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class CollectionQueryServiceRefundTest {
  final UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID(), partner = UUID.randomUUID();
  final LocalDate on = LocalDate.of(2026, 10, 20);
  final Instant now = Instant.parse("2026-10-20T12:00:00Z");
  CollectionAccessService access;
  CollectionObligationRepository obligations;
  CollectionPaymentMatchRepository matches;
  CollectionProjectionRepository projections;
  CollectionChequeApplicationRepository applications;
  CollectionRefundLedger refundLedger;
  CollectionProjectionService projectionService;
  CollectionQueryService queries;
  CollectionObligation row;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    access = mock(CollectionAccessService.class);
    obligations = mock(CollectionObligationRepository.class);
    matches = mock(CollectionPaymentMatchRepository.class);
    projections = mock(CollectionProjectionRepository.class);
    applications = mock(CollectionChequeApplicationRepository.class);
    refundLedger = mock(CollectionRefundLedger.class);
    projectionService = mock(CollectionProjectionService.class);
    when(projectionService.refreshIfFree(any())).thenReturn(Refresh.REFRESHED);
    queries =
        new CollectionQueryService(
            access,
            obligations,
            matches,
            projections,
            projectionService,
            mock(CollectionChequeRepository.class),
            applications,
            mock(CollectionChequeEventRepository.class),
            mock(CollectionExpectationRepository.class),
            mock(PaymentRepository.class),
            refundLedger);
    row =
        CollectionObligation.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), partner, "GBP");
    row.setId(UUID.randomUUID());
    row.setTenantId(tenant);
    row.revise(
        UUID.randomUUID(),
        new BigDecimal("1000.00"),
        Kind.INSTALMENT,
        Method.BANK_TRANSFER,
        on,
        false,
        null,
        now,
        false);
    var reach = new CollectionAccessService.Reach(tenant, DataScope.GLOBAL, Set.of(actor));
    when(access.reach(actor, "read")).thenReturn(reach);
    when(access.reach(actor, "write")).thenReturn(reach);
    when(access.reach(actor, "reverse")).thenReturn(reach);
    when(obligations.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(row)));
    when(obligations.findByTenantIdAndId(tenant, row.getId())).thenReturn(Optional.of(row));
    when(matches.nonChequeSettledFor(any(), any())).thenReturn(List.of());
    when(applications.received(any(), any())).thenReturn(List.of());
    when(projections.findByTenantIdAndOrderIdIn(any(), any())).thenReturn(List.of());
    when(refundLedger.pendingByObligation(any(), any())).thenReturn(Map.of());
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  void settled(String amount) {
    var balance = mock(CollectionPaymentMatchRepository.Balance.class);
    when(balance.getId()).thenReturn(row.getId());
    when(balance.getAmount()).thenReturn(new BigDecimal(amount));
    when(matches.settled(any(), any())).thenReturn(List.of(balance));
  }

  CollectionDtos.Obligation listed() {
    return queries
        .list(actor, null, null, null, null, null, null, PageRequest.of(0, 10))
        .getContent()
        .getFirst();
  }

  CollectionDtos.Capability capability(CollectionDtos.Obligation value, String action) {
    return value.capabilities().stream()
        .filter(c -> c.action().equals(action))
        .findFirst()
        .orElseThrow();
  }

  CollectionDtos.Obligation detailed(Refresh refresh) {
    when(projectionService.refreshIfFree(row.getOrderId())).thenReturn(refresh);
    when(matches.nonChequeSettled(any(), any())).thenReturn(BigDecimal.ZERO);
    when(matches.findByTenantIdAndObligationId(any(), any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));
    return queries.detail(row.getId(), actor, PageRequest.of(0, 10)).obligation();
  }

  @Test
  void aDetailFromARefreshedProjectionCarriesNoRefreshNotice() {
    assertThat(detailed(Refresh.REFRESHED).warnings())
        .doesNotContain("SOURCE_REFRESH_DEFERRED", "SOURCE_UNREADABLE");
  }

  @Test
  void aDetailOfAnOrderBeingEditedShowsTheLastRecordedPlanAndSaysSo() {
    var value = detailed(Refresh.BUSY);

    assertThat(value.warnings()).contains("SOURCE_REFRESH_DEFERRED");
    assertThat(value.warnings()).doesNotContain("SOURCE_UNREADABLE");
    assertThat(value.expectedAmount()).isEqualTo("1000.00");
  }

  @Test
  void aSourceThatCannotBeReadStillOpensTheDetailWithANoticeOfItsOwn() {
    var value = detailed(Refresh.UNREADABLE);

    assertThat(value.warnings()).contains("SOURCE_UNREADABLE");
    assertThat(value.warnings()).doesNotContain("SOURCE_REFRESH_DEFERRED");
    assertThat(value.expectedAmount()).isEqualTo("1000.00");
  }

  @Test
  void theDetailPageOnlyEverAsksForARefreshThatWaitsForNothing() {
    detailed(Refresh.BUSY);

    verify(projectionService).refreshIfFree(row.getOrderId());
    verify(projectionService, never()).synchronize(any());
    verify(projectionService, never()).synchronizeIfFree(any());
  }

  @Test
  void aListNeverRefreshesAndNeverCarriesARefreshNotice() {
    assertThat(listed().warnings()).doesNotContain("SOURCE_REFRESH_DEFERRED", "SOURCE_UNREADABLE");
    verify(projectionService, never()).refreshIfFree(any());
  }

  @Test
  void cashAboveTheAgreedAmountMakesTheResolutionAvailable() {
    settled("1300.00");

    var value = listed();

    assertThat(value.overmatchedAmount()).isEqualTo("300.00");
    assertThat(capability(value, "RESOLVE_OVER_SETTLEMENT").allowed()).isTrue();
    assertThat(value.warnings()).contains("RECEIPTS_REQUIRE_RECONCILIATION");
  }

  @Test
  void nothingAboveTheAgreedAmountMeansNothingToResolve() {
    settled("1000.00");

    var capability = capability(listed(), "RESOLVE_OVER_SETTLEMENT");

    assertThat(capability.allowed()).isFalse();
    assertThat(capability.reason()).isEqualTo("NO_OVER_SETTLEMENT");
  }

  @Test
  void aColleagueWithoutWriteReachSeesTheResolutionButCannotUseIt() {
    settled("1300.00");
    when(access.reach(actor, "write"))
        .thenReturn(new CollectionAccessService.Reach(tenant, DataScope.OWN, Set.of(actor)));

    var capability = capability(listed(), "RESOLVE_OVER_SETTLEMENT");

    assertThat(capability.allowed()).isFalse();
    assertThat(capability.reason()).isEqualTo("PERMISSION_DENIED");
  }

  @Test
  void theOtherCapabilitiesKeepTheirMeaning() {
    settled("1300.00");

    var value = listed();

    assertThat(capability(value, "REVERSE_MATCH").allowed()).isTrue();
    assertThat(capability(value, "MATCH_PAYMENT").allowed()).isFalse();
    assertThat(capability(value, "RECORD_RECEIPT").reason()).isEqualTo("EXPECTATION_NOT_OPEN");
  }

  @Test
  void moneyStillOwedBackIsShownSeparatelyFromTheSurplus() {
    settled("1000.00");
    when(refundLedger.pendingByObligation(any(), any()))
        .thenReturn(Map.of(row.getId(), new BigDecimal("200.00")));

    var value = listed();

    assertThat(value.refundPendingAmount()).isEqualTo("200.00");
    assertThat(value.overmatchedAmount()).isEqualTo("0.00");
    assertThat(value.warnings()).contains("REFUND_PENDING");
  }

  @Test
  void anExpectationWithNoRefundsShowsZeroOwedBack() {
    settled("500.00");

    var value = listed();

    // Every amount on the obligation is at the currency's scale, zero included.
    assertThat(value.refundPendingAmount()).isEqualTo("0.00");
    assertThat(value.warnings()).doesNotContain("REFUND_PENDING");
  }

  @Test
  void refundsAreListedWithTheirStateAndWhatTheCallerMayDoWithThem() {
    var refund =
        CollectionRefund.open(
            row,
            UUID.randomUUID(),
            new BigDecimal("200.00"),
            on,
            now,
            actor,
            "Overpaid",
            List.of());
    refund.setId(UUID.randomUUID());
    var open = new CollectionRefundLedger.View(refund, RefundState.OPEN, 0, on, null);
    var paid =
        new CollectionRefundLedger.View(refund, RefundState.SETTLED, 1, on, UUID.randomUUID());
    when(refundLedger.forObligation(tenant, row.getId())).thenReturn(List.of(open, paid));

    var result = queries.refunds(row.getId(), actor);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).state()).isEqualTo(RefundState.OPEN);
    assertThat(result.get(0).step()).isZero();
    assertThat(result.get(0).amount()).isEqualTo("200.00");
    assertThat(result.get(0).capabilities())
        .extracting(c -> c.action() + ":" + c.allowed())
        .containsExactly("SETTLE_REFUND:true", "CANCEL_REFUND:true");
    assertThat(result.get(1).settlementPaymentId()).isNotNull();
    assertThat(result.get(1).capabilities())
        .allSatisfy(
            c -> {
              assertThat(c.allowed()).isFalse();
              assertThat(c.reason()).isEqualTo("WRONG_STATE");
            });
  }

  @Test
  void refundsOfAnExpectationTheCallerCannotReadAreNotFound() {
    doThrow(new NotFoundException("Collection obligation not found"))
        .when(access)
        .require(actor, "read", row);

    assertThatThrownBy(() -> queries.refunds(row.getId(), actor))
        .isInstanceOf(NotFoundException.class);
  }
}
