package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.DeliveryVerificationPolicy;
import com.fabricmanagement.sales.salesorder.domain.OrderDelivery;
import com.fabricmanagement.sales.salesorder.domain.OrderType;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import com.fabricmanagement.sales.salesorder.domain.PartyMode;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.DeliveryContentInput;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.SaveDeliveryRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderDeliveryDtos.VerifyDeliveryRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.AddressInput;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.PartyInput;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderDeliveryRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderLineAllocationRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * CONSIGNEE-1 S3 (C8): the verification work follows the order's deliveries. It opens when a
 * delivery is left unverified, stays open while any is, and closes when the last one is verified or
 * removed, whichever way the change came.
 */
@ExtendWith(MockitoExtension.class)
class OrderDeliveryVerificationWorkTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID ACTOR = UUID.randomUUID();
  private static final UUID CUSTOMER = UUID.randomUUID();
  private static final long VERSION = 3L;
  private static final OrderWorkKind KIND = OrderWorkKind.DELIVERY_VERIFICATION;

  @Mock private OrderDraftAccess access;
  @Mock private OrderDeliveryRepository deliveries;
  @Mock private OrderLineAllocationRepository allocations;
  @Mock private SalesOrderLineRepository lines;
  @Mock private SalesOrderRevision revision;
  @Mock private DeliveryVerificationPolicy policy;
  @Mock private OrderWorkService work;
  @InjectMocks private OrderDeliveryService service;

  private final List<OrderDelivery> stored = new ArrayList<>();
  private SalesOrder order;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    TenantContext.setCurrentUserId(ACTOR);
    order =
        SalesOrder.builder()
            .tradingPartnerId(CUSTOMER)
            .orderNumber("SO-1")
            .orderType(OrderType.SALES)
            .build();
    ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(order, "version", VERSION);
    lenient().when(access.writable(order.getId(), VERSION, ACTOR)).thenReturn(order);
    lenient().when(access.verifiable(order.getId(), VERSION)).thenReturn(order);
    lenient()
        .when(deliveries.findByTenantIdAndSalesOrderIdOrderBySequenceNoAsc(TENANT, order.getId()))
        .thenAnswer(invocation -> List.copyOf(stored));
    lenient()
        .when(deliveries.save(any(OrderDelivery.class)))
        .thenAnswer(
            invocation -> {
              OrderDelivery saved = invocation.getArgument(0);
              if (!stored.contains(saved)) {
                stored.add(saved);
              }
              return saved;
            });
    lenient()
        .doAnswer(
            invocation -> {
              stored.remove(invocation.<OrderDelivery>getArgument(0));
              return null;
            })
        .when(deliveries)
        .delete(any(OrderDelivery.class));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private static DeliveryContentInput complete() {
    return new DeliveryContentInput(
        new PartyInput(PartyMode.CUSTOMER, null, null),
        new AddressInput("1 Mill Lane", null, "Leeds", null, "LS1 1AA", "GB", null),
        null,
        null,
        null,
        null,
        null,
        false,
        null);
  }

  /** A delivery already on the order, waiting for verification. */
  private OrderDelivery waiting(int sequenceNo) {
    OrderDelivery delivery =
        OrderDelivery.create(
            order.getId(),
            sequenceNo,
            new OrderDelivery.Content(
                new PartyInput(PartyMode.CUSTOMER, null, null).toReference(CUSTOMER, "consignee"),
                complete().shipTo().toSnapshot(),
                null,
                null,
                null,
                null,
                null,
                false,
                null));
    ReflectionTestUtils.setField(delivery, "id", UUID.randomUUID());
    lenient()
        .when(
            deliveries.findByTenantIdAndSalesOrderIdAndId(TENANT, order.getId(), delivery.getId()))
        .thenReturn(Optional.of(delivery));
    stored.add(delivery);
    return delivery;
  }

  private void verified(OrderDelivery delivery) {
    delivery.verify(order, ACTOR, Instant.parse("2026-10-16T09:00:00Z"));
  }

  @Test
  void aDeliverySavedByAUserWhoCannotVerifyOpensTheWork() {
    service.create(order.getId(), new SaveDeliveryRequest(VERSION, complete()), ACTOR);

    verify(work).open(order.getId(), KIND, ACTOR);
    verify(work, never()).close(any(), any(), any());
  }

  @Test
  void aCompleteDeliverySavedByAVerifierLeavesNothingToDo() {
    when(policy.actorMayVerify()).thenReturn(true);

    service.create(order.getId(), new SaveDeliveryRequest(VERSION, complete()), ACTOR);

    verify(work).close(order.getId(), KIND, ACTOR);
    verify(work, never()).open(any(), any(), any());
  }

  @Test
  void editingAVerifiedDeliveryWithoutThePermissionOpensTheWorkAgain() {
    OrderDelivery delivery = waiting(1);
    verified(delivery);

    service.update(
        order.getId(), delivery.getId(), new SaveDeliveryRequest(VERSION, complete()), ACTOR);

    verify(work).open(order.getId(), KIND, ACTOR);
  }

  @Test
  void verifyingTheLastWaitingDeliveryClosesTheWork() {
    OrderDelivery delivery = waiting(1);

    service.verify(
        order.getId(), delivery.getId(), new VerifyDeliveryRequest(VERSION, null), ACTOR);

    verify(work).close(order.getId(), KIND, ACTOR);
    verify(work, never()).open(any(), any(), any());
  }

  @Test
  void verifyingOneOfTwoWaitingDeliveriesKeepsTheWorkOpen() {
    OrderDelivery first = waiting(1);
    waiting(2);

    service.verify(order.getId(), first.getId(), new VerifyDeliveryRequest(VERSION, null), ACTOR);

    verify(work).open(order.getId(), KIND, ACTOR);
    verify(work, never()).close(any(), any(), any());
  }

  @Test
  void removingTheLastWaitingDeliveryClosesTheWork() {
    OrderDelivery waitingOne = waiting(1);
    OrderDelivery done = waiting(2);
    verified(done);

    service.delete(order.getId(), waitingOne.getId(), VERSION, ACTOR);

    verify(work).close(order.getId(), KIND, ACTOR);
  }

  @Test
  void removingAVerifiedDeliveryWhileAnotherWaitsKeepsTheWorkOpen() {
    OrderDelivery waitingOne = waiting(1);
    OrderDelivery done = waiting(2);
    verified(done);

    service.delete(order.getId(), done.getId(), VERSION, ACTOR);

    verify(work).open(order.getId(), KIND, ACTOR);
    verify(work, never()).close(any(), any(), any());
    assertThat(stored).containsExactly(waitingOne);
  }
}
