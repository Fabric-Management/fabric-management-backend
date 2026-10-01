package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.CommitmentChangeOrigin;
import com.fabricmanagement.sales.salesorder.domain.CommitmentChannel;
import com.fabricmanagement.sales.salesorder.domain.DeliveryCommitment;
import com.fabricmanagement.sales.salesorder.domain.DeliveryEvent;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerms;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.DeliveryCommitmentDtos.CommitmentHistory;
import com.fabricmanagement.sales.salesorder.dto.DeliveryCommitmentDtos.RecordDeliveryCommitment;
import com.fabricmanagement.sales.salesorder.infra.repository.DeliveryCommitmentRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveryCommitmentServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
  private static final Instant AGREED = NOW.minusSeconds(600);

  @Mock private SalesOrderRepository orders;
  @Mock private DeliveryCommitmentRepository commitments;
  @Mock private SalesOrderAccessPolicy accessPolicy;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID actor = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private final List<DeliveryCommitment> stored = new ArrayList<>();
  private SalesOrder order;
  private DeliveryCommitmentService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    order =
        SalesOrder.builder()
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-1")
            .status(OrderStatus.CONFIRMED)
            .orderDate(LocalDate.of(2026, 9, 30))
            .requestedDeliveryDate(LocalDate.of(2026, 10, 20))
            .build();
    ReflectionTestUtils.setField(order, "id", orderId);
    order.applyDeliveryTerms(DeliveryTerms.of(DeliveryTerm.FCA, "Mill gate, Uşak", null));
    when(orders.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(orders.lockByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(accessPolicy.canRead(tenantId, actor, order)).thenReturn(true);
    when(accessPolicy.canWrite(tenantId, actor, order)).thenReturn(true);
    when(commitments.save(any(DeliveryCommitment.class)))
        .thenAnswer(
            invocation -> {
              DeliveryCommitment value = invocation.getArgument(0);
              ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
              stored.add(value);
              return value;
            });
    when(commitments.findFirstByTenantIdAndSalesOrderIdOrderBySequenceDesc(tenantId, orderId))
        .thenAnswer(
            invocation ->
                stored.stream().max(Comparator.comparingInt(DeliveryCommitment::getSequence)));
    when(commitments.findByTenantIdAndSalesOrderIdOrderBySequenceAsc(tenantId, orderId))
        .thenAnswer(invocation -> List.copyOf(stored));
    service =
        new DeliveryCommitmentService(
            orders, commitments, accessPolicy, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  private RecordDeliveryCommitment input(
      UUID basedOn, LocalDate on, CommitmentChangeOrigin origin, String reason) {
    return new RecordDeliveryCommitment(
        basedOn,
        on,
        origin,
        reason,
        "Ayşe Demir",
        CommitmentChannel.EMAIL,
        AGREED,
        null,
        null,
        null);
  }

  @Test
  void theFirstPromiseIsRecordedUnderTheOrdersTermAndBecomesTheCommittedDate() {
    CommitmentHistory history =
        service.record(orderId, input(null, LocalDate.of(2026, 10, 21), null, null), actor);

    assertThat(order.getCommittedOn()).isEqualTo(LocalDate.of(2026, 10, 21));
    assertThat(history.current()).isEqualTo(history.initial());
    assertThat(history.current().origin()).isEqualTo(CommitmentChangeOrigin.INITIAL);
    assertThat(history.current().deliveryEvent()).isEqualTo(DeliveryEvent.HANDED_TO_CARRIER);
    assertThat(history.current().deliveryPlace()).isEqualTo("Mill gate, Uşak");
    assertThat(history.shiftFromInitialDays()).isZero();
  }

  @Test
  void withoutAnAgreedTermNoDateCanBePromised() {
    order.applyDeliveryTerms(DeliveryTerms.NONE);

    assertThatThrownBy(
            () ->
                service.record(orderId, input(null, LocalDate.of(2026, 10, 21), null, null), actor))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("delivery term");
    verify(commitments, never()).save(any());
    assertThat(order.getCommittedOn()).isNull();
  }

  @Test
  void aChangeBasedOnAnOldCommitmentIsRejected() {
    service.record(orderId, input(null, LocalDate.of(2026, 10, 21), null, null), actor);

    assertThatThrownBy(
            () ->
                service.record(
                    orderId,
                    input(
                        null,
                        LocalDate.of(2026, 10, 24),
                        CommitmentChangeOrigin.SELLER_REVISION,
                        "Dyehouse delay"),
                    actor))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("changed meanwhile");
    assertThat(stored).hasSize(1);
    assertThat(order.getCommittedOn()).isEqualTo(LocalDate.of(2026, 10, 21));
  }

  @Test
  void buyerAndSellerShiftsAreMeasuredApartFromTheFirstPromise() {
    UUID first =
        service
            .record(orderId, input(null, LocalDate.of(2026, 10, 21), null, null), actor)
            .current()
            .id();
    UUID second =
        service
            .record(
                orderId,
                input(
                    first,
                    LocalDate.of(2026, 10, 24),
                    CommitmentChangeOrigin.SELLER_REVISION,
                    "Dyehouse delay"),
                actor)
            .current()
            .id();
    CommitmentHistory history =
        service.record(
            orderId,
            input(
                second,
                LocalDate.of(2026, 10, 29),
                CommitmentChangeOrigin.BUYER_REQUEST,
                "Buyer added 2,000 m"),
            actor);

    assertThat(history.initial().committedOn()).isEqualTo(LocalDate.of(2026, 10, 21));
    assertThat(history.current().committedOn()).isEqualTo(LocalDate.of(2026, 10, 29));
    assertThat(history.shiftFromInitialDays()).isEqualTo(8);
    assertThat(history.sellerRevisionShiftDays()).isEqualTo(3);
    assertThat(history.buyerRequestedShiftDays()).isEqualTo(5);
    assertThat(history.sameEventThroughout()).isTrue();
    assertThat(history.revisions())
        .extracting(view -> view.shiftFromPreviousDays())
        .containsExactly(null, 3L, 5L);
    assertThat(order.getCommittedOn()).isEqualTo(LocalDate.of(2026, 10, 29));
  }

  @Test
  void aRenegotiatedTermIsPartOfTheChangeAndMovesTheOrdersEvent() {
    UUID first =
        service
            .record(orderId, input(null, LocalDate.of(2026, 10, 21), null, null), actor)
            .current()
            .id();

    CommitmentHistory history =
        service.record(
            orderId,
            new RecordDeliveryCommitment(
                first,
                LocalDate.of(2026, 10, 23),
                CommitmentChangeOrigin.BUYER_REQUEST,
                "Buyer asked us to deliver",
                "Ayşe Demir",
                CommitmentChannel.PHONE,
                AGREED,
                DeliveryTerm.DAP,
                "Buyer DC, Leicester",
                null),
            actor);

    assertThat(order.getDeliveryTerm()).isEqualTo(DeliveryTerm.DAP);
    assertThat(order.getDeliveryEvent())
        .isEqualTo(DeliveryEvent.READY_FOR_UNLOADING_AT_DESTINATION);
    assertThat(history.sameEventThroughout()).isFalse();
  }

  @Test
  void aClosedOrderTakesNoNewCommitment() {
    ReflectionTestUtils.setField(order, "status", OrderStatus.CANCELLED);

    assertThatThrownBy(
            () ->
                service.record(orderId, input(null, LocalDate.of(2026, 10, 21), null, null), actor))
        .isInstanceOf(OrderDomainException.class);
    verify(commitments, never()).save(any());
  }

  @Test
  void onlyReadersSeeTheHistoryAndOnlyWritersRecord() {
    when(accessPolicy.canWrite(tenantId, actor, order)).thenReturn(false);
    assertThatThrownBy(
            () ->
                service.record(orderId, input(null, LocalDate.of(2026, 10, 21), null, null), actor))
        .isInstanceOf(AccessDeniedException.class);

    when(accessPolicy.canRead(tenantId, actor, order)).thenReturn(false);
    assertThatThrownBy(() -> service.history(orderId, actor)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void aDraftEditCannotChangeTheTermOfAnAgreedCommitment() {
    service.assertTermsEditable(order, DeliveryTerms.of(DeliveryTerm.EXW, "Mill gate", null));

    service.record(orderId, input(null, LocalDate.of(2026, 10, 21), null, null), actor);

    service.assertTermsEditable(order, DeliveryTerms.of(DeliveryTerm.FCA, "Mill gate, Uşak", null));
    assertThatThrownBy(
            () ->
                service.assertTermsEditable(
                    order, DeliveryTerms.of(DeliveryTerm.EXW, "Mill gate, Uşak", null)))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("new commitment");
  }

  @Test
  void theCatalogueListsEveryRuleWithItsEvent() {
    assertThat(service.deliveryTerms())
        .hasSize(DeliveryTerm.values().length)
        .anySatisfy(
            option -> {
              assertThat(option.term()).isEqualTo(DeliveryTerm.EXW);
              assertThat(option.event()).isEqualTo(DeliveryEvent.AVAILABLE_FOR_COLLECTION);
              assertThat(option.eventAtDestination()).isFalse();
            });
  }
}
