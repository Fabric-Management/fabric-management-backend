package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.DeliveryVerificationPolicy;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerms;
import com.fabricmanagement.sales.salesorder.domain.OrderDelivery;
import com.fabricmanagement.sales.salesorder.domain.OrderType;
import com.fabricmanagement.sales.salesorder.domain.PartyMode;
import com.fabricmanagement.sales.salesorder.domain.PartyReference;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.AddressInput;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.PartyInput;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderDeliveryRepository;
import java.time.Instant;
import java.util.List;
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
 * CONSIGNEE-1 S3: sending an order for the customer's approval needs a sender who may verify
 * delivery details and every delivery verified; the information draft is not gated at all.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryVerificationGateTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID CUSTOMER = UUID.randomUUID();
  private static final UUID VERIFIER = UUID.randomUUID();
  private static final Instant AT = Instant.parse("2026-10-16T09:00:00Z");

  @Mock private OrderDeliveryRepository deliveries;
  @Mock private DeliveryVerificationPolicy policy;
  @InjectMocks private DeliveryVerificationGate gate;

  private SalesOrder order;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    order = order(null);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private static SalesOrder order(DeliveryTerm term) {
    SalesOrder value =
        SalesOrder.builder()
            .tradingPartnerId(CUSTOMER)
            .orderNumber("SO-1")
            .orderType(OrderType.SALES)
            .build();
    ReflectionTestUtils.setField(value, "id", UUID.randomUUID());
    if (term != null) {
      value.applyDeliveryTerms(DeliveryTerms.of(term, "Mill gate", null));
      value.applyDeliveryTermStatus(null, null);
    }
    return value;
  }

  private OrderDelivery delivery(int sequenceNo, boolean address, String note, boolean verified) {
    PartyReference consignee =
        new PartyInput(PartyMode.CUSTOMER, null, null).toReference(CUSTOMER, "consignee");
    OrderDelivery value =
        OrderDelivery.create(
            order.getId(),
            sequenceNo,
            new OrderDelivery.Content(
                consignee,
                address
                    ? new AddressInput("1 Mill Lane", null, "Leeds", null, "LS1 1AA", "GB", null)
                        .toSnapshot()
                    : null,
                null,
                null,
                null,
                null,
                null,
                false,
                note));
    if (verified) {
      value.verify(order, VERIFIER, AT);
    }
    return value;
  }

  private void theOrderHas(OrderDelivery... values) {
    when(deliveries.findByTenantIdAndSalesOrderIdOrderBySequenceNoAsc(TENANT, order.getId()))
        .thenReturn(List.of(values));
  }

  @Test
  void aSenderWhoMayNotVerifyIsRefusedWithoutLookingAtTheDeliveries() {
    when(policy.actorMayVerify()).thenReturn(false);

    assertThat(gate.sendBlock(order)).isEqualTo(DeliveryVerificationGate.PERMISSION_REQUIRED);
    verifyNoInteractions(deliveries);
  }

  @Test
  void anOrderWithoutDeliveriesHasNothingToVerify() {
    when(policy.actorMayVerify()).thenReturn(true);
    theOrderHas();

    assertThat(gate.sendBlock(order)).isNull();
  }

  @Test
  void verifiedDeliveriesLetTheOrderGoToTheCustomer() {
    when(policy.actorMayVerify()).thenReturn(true);
    theOrderHas(delivery(1, true, null, true), delivery(2, true, "Gate 3", true));

    assertThat(gate.sendBlock(order)).isNull();
  }

  @Test
  void oneDeliveryStillWaitingHoldsTheOrderBack() {
    when(policy.actorMayVerify()).thenReturn(true);
    theOrderHas(delivery(1, true, null, true), delivery(2, true, null, false));

    assertThat(gate.sendBlock(order)).isEqualTo(DeliveryVerificationGate.NOT_VERIFIED);
  }

  @Test
  void aDeliveryVerifiedWithoutAnAddressBecauseTheCustomerCollectsPasses() {
    order = order(DeliveryTerm.EXW);
    when(policy.actorMayVerify()).thenReturn(true);
    theOrderHas(delivery(1, false, "Customer collects", true));

    assertThat(gate.sendBlock(order)).isNull();
  }

  @Test
  void aNoteOnlyDeliveryUnderAnotherTermStaysUnverified() {
    order = order(DeliveryTerm.FCA);
    when(policy.actorMayVerify()).thenReturn(true);
    theOrderHas(delivery(1, false, "Hackney workshop", false));

    assertThat(gate.sendBlock(order)).isEqualTo(DeliveryVerificationGate.NOT_VERIFIED);
  }

  @Test
  void theRefusalNamesTheDeliveriesAndWhatEachOneLacks() {
    OrderDelivery noteOnly = delivery(2, false, "Hackney workshop", false);
    theOrderHas(delivery(1, true, null, true), noteOnly, delivery(3, true, null, false));

    assertThat(gate.describe(order))
        .isEqualTo(
            "delivery 2 ("
                + noteOnly.verificationBlocker(order).orElseThrow().message()
                + "); delivery 3");
  }
}
