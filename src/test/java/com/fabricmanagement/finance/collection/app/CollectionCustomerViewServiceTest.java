package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.collection.domain.*;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.*;
import com.fabricmanagement.finance.collection.infra.repository.*;
import com.fabricmanagement.finance.collection.security.CollectionAccessService;
import com.fabricmanagement.finance.payment.domain.*;
import com.fabricmanagement.finance.payment.infra.repository.PaymentRepository;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;

/** PAYMENT-PLAN-9 R5: two separate lines per currency, the shared availability, R6 visibility. */
@ExtendWith(MockitoExtension.class)
class CollectionCustomerViewServiceTest {
  @Mock CollectionAccessService access;
  @Mock CollectionChequeDrawerResolver customers;
  @Mock CollectionPaymentMatchRepository matches;
  @Mock PaymentRepository payments;
  @Mock CollectionPaymentAvailability availabilities;
  @Mock CollectionChequeRepository cheques;
  @Mock CollectionChequeApplicationRepository applications;
  @Mock CollectionObligationRepository obligations;
  @InjectMocks CollectionCustomerViewService view;
  final UUID tenant = UUID.randomUUID(),
      actor = UUID.randomUUID(),
      partner = UUID.randomUUID(),
      colleague = UUID.randomUUID();
  final LocalDate date = LocalDate.of(2026, 10, 20);

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    lenient()
        .when(access.reach(actor, "read"))
        .thenReturn(
            new CollectionAccessService.Reach(tenant, DataScope.ORGANIZATION, Set.of(actor)));
    lenient()
        .when(
            cheques.findByTenantIdAndPartnerIdAndStateNotIn(eq(tenant), eq(partner), any(), any()))
        .thenReturn(new PageImpl<>(List.of()));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  private Payment payment(String currency, String amount, UUID creator) {
    var value =
        Payment.builder()
            .tradingPartnerId(partner)
            .paymentNumber("PAY-GB-" + UUID.randomUUID().toString().substring(0, 4))
            .direction(PaymentDirection.INBOUND)
            .method(PaymentMethod.BANK_TRANSFER)
            .amount(Money.of(new BigDecimal(amount), currency))
            .paymentDate(date)
            .build();
    value.setId(UUID.randomUUID());
    value.setCreatedBy(creator);
    return value;
  }

  private CollectionCheque cheque(String amount, UUID creator) {
    var value =
        CollectionCheque.receive(
            partner,
            "GBP",
            new BigDecimal(amount),
            date,
            date.plusMonths(3),
            "NORTHBANK-" + UUID.randomUUID().toString().substring(0, 4),
            "Signed instrument scan",
            new CollectionCheque.Drawer(DrawerKind.OWN, null, "Northbridge Cloth"));
    value.setId(UUID.randomUUID());
    value.setTenantId(tenant);
    value.setCreatedBy(creator);
    return value;
  }

  private void paymentsAre(List<Payment> list, Map<UUID, String> available) {
    var ids = list.stream().map(Payment::getId).toList();
    when(matches.unmatchedPaymentIds(eq(tenant), eq(partner), anyBoolean(), any(), anyInt()))
        .thenReturn(ids);
    lenient().when(payments.findByTenantIdAndIdIn(tenant, ids)).thenReturn(list);
    var result = new LinkedHashMap<UUID, CollectionPaymentAvailability.Availability>();
    for (var p : list)
      result.put(
          p.getId(),
          new CollectionPaymentAvailability.Availability(
              p.getId(),
              true,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              new BigDecimal(available.get(p.getId())),
              null));
    lenient().when(availabilities.of(eq(tenant), anyList())).thenReturn(result);
  }

  @Test
  void paymentsAndHeldChequesAreSeparateLinesPerCurrencyAndNeverAddedTogether() {
    var gbp = payment("GBP", "10000.00", actor);
    var eur = payment("EUR", "500.00", actor);
    paymentsAre(List.of(gbp, eur), Map.of(gbp.getId(), "3000.00", eur.getId(), "500.00"));
    var held = cheque("6000.00", actor);
    when(cheques.findByTenantIdAndPartnerIdAndStateIn(eq(tenant), eq(partner), any()))
        .thenReturn(List.of(held));
    var result = view.availability(partner, actor);
    assertThat(result.currencies()).extracting(c -> c.currency()).containsExactly("EUR", "GBP");
    var gbpLine = result.currencies().get(1);
    assertThat(gbpLine.paymentsAvailable()).isEqualTo("3000.00");
    assertThat(gbpLine.chequesAvailable()).isEqualTo("6000.00");
    assertThat(gbpLine.cheques())
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.availableAmount()).isEqualTo("6000.00");
              assertThat(c.version()).isEqualTo(held.getVersion());
            });
    assertThat(result.currencies().get(0).chequesAvailable()).isEqualTo("0.00");
    assertThat(result.paymentsTruncated()).isFalse();
  }

  @Test
  void aFullyAppliedHeldChequeIsHistoryNotAvailableValue() {
    paymentsAre(List.of(), Map.of());
    var held = cheque("1000.00", actor);
    when(cheques.findByTenantIdAndPartnerIdAndStateIn(eq(tenant), eq(partner), any()))
        .thenReturn(List.of(held));
    var obligation =
        CollectionObligation.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), partner, "GBP");
    obligation.setId(UUID.randomUUID());
    obligation.setTenantId(tenant);
    when(applications.activeOf(eq(tenant), any()))
        .thenReturn(
            List.of(
                CollectionChequeApplication.create(
                    held.getId(),
                    obligation.getId(),
                    new BigDecimal("1000.00"),
                    date,
                    Instant.parse("2026-10-20T12:00:00Z"),
                    actor,
                    "Applied at receipt")));
    when(obligations.findByTenantIdAndIdIn(tenant, List.of(obligation.getId())))
        .thenReturn(List.of(obligation));
    var result = view.availability(partner, actor);
    assertThat(result.currencies()).isEmpty();
    assertThat(result.chequeHistory())
        .singleElement()
        .satisfies(c -> assertThat(c.availableAmount()).isEqualTo("0.00"));
  }

  /** R6: under a narrow scope, unlinked items are reached through their creator only. */
  @Test
  void aNarrowScopeSeesOnlyItsCreatorsItemsAndNoChequeOverAnUnreadableObligation() {
    when(access.reach(actor, "read"))
        .thenReturn(
            new CollectionAccessService.Reach(
                tenant, DataScope.DEPARTMENT, Set.of(actor, colleague)));
    var mine = payment("GBP", "100.00", colleague);
    var foreign = payment("GBP", "200.00", UUID.randomUUID());
    paymentsAre(List.of(mine, foreign), Map.of(mine.getId(), "100.00", foreign.getId(), "200.00"));
    var own = cheque("300.00", colleague);
    var outsider = cheque("400.00", UUID.randomUUID());
    var linked = cheque("500.00", colleague);
    var hidden =
        CollectionObligation.create(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), partner, "GBP");
    hidden.setId(UUID.randomUUID());
    hidden.setTenantId(tenant);
    hidden.assignResponsible(UUID.randomUUID());
    when(cheques.findByTenantIdAndPartnerIdAndStateIn(eq(tenant), eq(partner), any()))
        .thenReturn(List.of(own, outsider, linked));
    when(applications.activeOf(eq(tenant), any()))
        .thenReturn(
            List.of(
                CollectionChequeApplication.create(
                    linked.getId(),
                    hidden.getId(),
                    new BigDecimal("100.00"),
                    date,
                    Instant.parse("2026-10-20T12:00:00Z"),
                    actor,
                    "Applied at receipt")));
    when(obligations.findByTenantIdAndIdIn(tenant, List.of(hidden.getId())))
        .thenReturn(List.of(hidden));
    var result = view.availability(partner, actor);
    verify(matches)
        .unmatchedPaymentIds(
            eq(tenant), eq(partner), eq(false), eq(Set.of(actor, colleague)), anyInt());
    var line = result.currencies().getFirst();
    assertThat(line.payments()).extracting(p -> p.id()).containsExactly(mine.getId());
    assertThat(line.cheques()).extracting(c -> c.id()).containsExactly(own.getId());
    assertThat(result.chequeHistory()).isEmpty();
  }

  @Test
  void moreCandidatesThanTheLimitAreFlaggedAndCapped() {
    var ids = new ArrayList<UUID>();
    for (int i = 0; i <= CollectionCustomerViewService.PAYMENT_LIMIT; i++)
      ids.add(UUID.randomUUID());
    when(matches.unmatchedPaymentIds(eq(tenant), eq(partner), anyBoolean(), any(), anyInt()))
        .thenReturn(ids);
    when(payments.findByTenantIdAndIdIn(eq(tenant), anyList())).thenReturn(List.of());
    when(availabilities.of(eq(tenant), anyList())).thenReturn(Map.of());
    when(cheques.findByTenantIdAndPartnerIdAndStateIn(eq(tenant), eq(partner), any()))
        .thenReturn(List.of());
    var result = view.availability(partner, actor);
    assertThat(result.paymentsTruncated()).isTrue();
    verify(payments)
        .findByTenantIdAndIdIn(
            eq(tenant),
            argThat(list -> list.size() == CollectionCustomerViewService.PAYMENT_LIMIT));
  }

  @Test
  void noReadGrantIsRefusedAndAnUnknownCustomerIsNotFound() {
    when(access.reach(actor, "read"))
        .thenReturn(new CollectionAccessService.Reach(tenant, null, Set.of(actor)));
    assertThatThrownBy(() -> view.availability(partner, actor))
        .isInstanceOf(AccessDeniedException.class);
    when(access.reach(actor, "read"))
        .thenReturn(
            new CollectionAccessService.Reach(tenant, DataScope.ORGANIZATION, Set.of(actor)));
    // An unknown partner and one that is not a customer read alike: 404, nothing else is read.
    UUID unknown = UUID.randomUUID();
    doThrow(new NotFoundException("Customer not found"))
        .when(customers)
        .requireCustomerToRead(unknown);
    assertThatThrownBy(() -> view.availability(unknown, actor))
        .isInstanceOf(NotFoundException.class);
    verify(customers).requireCustomerToRead(unknown);
    verifyNoInteractions(payments, cheques);
  }
}
