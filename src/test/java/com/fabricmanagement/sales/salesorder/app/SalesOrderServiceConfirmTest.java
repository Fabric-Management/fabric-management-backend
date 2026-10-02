package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.DocumentNumberGenerator;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.app.ruleengine.SalesOrderRuleEngine;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermStatus;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerms;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderType;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Confirmation: only the customer's approval of a sent version confirms an order (plus seeded demo
 * data). What a confirmation does (held pieces, cover regime, the confirmed event) is checked here.
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderServiceConfirmTest {

  @Mock private SalesOrderRepository orderRepository;
  @Mock private TradingPartnerResolver partnerResolver;
  @Mock private TradingPartnerService partnerService;
  @Mock private SalesOrderLineRepository lineRepository;
  @Mock private SalesOrderRuleEngine ruleEngine;
  @Mock private ModuleSpecsValidator moduleSpecsValidator;
  @Mock private DomainEventPublisher domainEventPublisher;
  @Mock private DocumentNumberGenerator documentNumberGenerator;
  @Mock private SalesOrderAccessPolicy accessPolicy;

  @Mock
  private com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverActivationRepository
      orderCoverActivationRepository;

  @Mock private OrderCoverEnrolmentService orderCoverEnrolmentService;

  // SOI intake checks; a mock is a no-op that reports no blockers.
  @Mock private OrderIntakeHooks orderIntakeHooks;

  @Mock private DeliveryCommitmentService deliveryCommitments;
  @Mock private OrderApprovalInvalidator approvalInvalidator;
  @InjectMocks private SalesOrderService salesOrderService;

  @Captor private ArgumentCaptor<SalesOrderConfirmedEvent> eventCaptor;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private final UUID partnerId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);
    org.mockito.Mockito.lenient()
        .when(orderCoverEnrolmentService.decide(any(), any()))
        .thenReturn(com.fabricmanagement.sales.salesorder.domain.OrderCoverRegime.LEGACY);
    org.mockito.Mockito.lenient()
        .when(orderRepository.save(any(SalesOrder.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  /** A draft the customer approved: the flow stands at the customer's approval. */
  private SalesOrder approvedDraft() {
    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(partnerId)
            .orderNumber("SO-001")
            .orderType(OrderType.SALES)
            .build();
    ReflectionTestUtils.setField(order, "id", orderId);
    order.applyDeliveryTerms(
        DeliveryTerms.of(DeliveryTerm.FCA, "Felixstowe", IncotermsVersion.INCOTERMS_2020));
    order.applyDeliveryTermStatus(null, null);
    ReflectionTestUtils.setField(order, "flowStage", OrderFlowStage.CUSTOMER_APPROVED);
    return order;
  }

  private SalesOrderLine line(String unit, BigDecimal qty) {
    SalesOrderLine line =
        SalesOrderLine.builder().salesOrderId(orderId).productId(UUID.randomUUID()).build();
    ReflectionTestUtils.setField(line, "unit", unit);
    ReflectionTestUtils.setField(line, "requestedQty", qty);
    ReflectionTestUtils.setField(line, "isActive", true);
    return line;
  }

  private SalesOrderConfirmedEvent confirmWith(SalesOrder order, List<SalesOrderLine> lines) {
    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(lines);
    salesOrderService.confirmApprovedByCustomer(order);
    verify(domainEventPublisher).publish(eventCaptor.capture());
    return eventCaptor.getValue();
  }

  @Test
  void customerApprovalConfirmsTheOrderAndAgreesItsDeliveryTerm() {
    SalesOrder order = approvedDraft();

    confirmWith(order, List.of(line("KG", BigDecimal.TEN)));

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getDeliveryTermStatus()).isEqualTo(DeliveryTermStatus.AGREED_BY_CUSTOMER);
    verify(orderIntakeHooks).allocateAtConfirmation(any(), any(), any());
  }

  @Test
  void anOrderTheCustomerHasNotApprovedIsNeverConfirmed() {
    SalesOrder order = approvedDraft();
    ReflectionTestUtils.setField(order, "flowStage", OrderFlowStage.AWAITING_CUSTOMER_APPROVAL);

    assertThatThrownBy(() -> salesOrderService.confirmApprovedByCustomer(order))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo("NOT_APPROVED_BY_CUSTOMER"));

    assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);
    verify(ruleEngine, never()).processConfirmedOrder(any());
    verify(domainEventPublisher, never()).publish(any());
  }

  @Test
  void governedConfirmationSkipsLegacyRuleEngineAndPublishesGovernedEvent() {
    SalesOrder order = approvedDraft();
    when(orderCoverEnrolmentService.decide(order, List.of()))
        .thenReturn(com.fabricmanagement.sales.salesorder.domain.OrderCoverRegime.GOVERNED);

    SalesOrderConfirmedEvent event = confirmWith(order, List.of());

    verify(ruleEngine, never()).processConfirmedOrder(any());
    assertThat(event.getCoverRegime())
        .isEqualTo(com.fabricmanagement.sales.salesorder.domain.OrderCoverRegime.GOVERNED);
  }

  @Test
  void eventCarriesCustomerIdAndName() {
    TradingPartnerDto partner = TradingPartnerDto.builder().build();
    ReflectionTestUtils.setField(partner, "id", partnerId);
    ReflectionTestUtils.setField(partner, "displayName", "Test Customer");
    when(partnerService.findById(tenantId, partnerId)).thenReturn(Optional.of(partner));

    SalesOrderConfirmedEvent event =
        confirmWith(approvedDraft(), List.of(line("KG", BigDecimal.valueOf(100))));

    assertThat(event.getCustomerId()).isEqualTo(partnerId);
    assertThat(event.getCustomerName()).isEqualTo("Test Customer");
  }

  @Test
  void sameUnitLinesGiveTheEventTheirUnit() {
    SalesOrderConfirmedEvent event =
        confirmWith(
            approvedDraft(),
            List.of(line("MT", BigDecimal.valueOf(100)), line("MT", BigDecimal.valueOf(50))));

    assertThat(event.getUnit()).isEqualTo("MT");
    assertThat(event.getTotalQuantity()).isEqualTo(BigDecimal.valueOf(150));
  }

  @Test
  void mixedUnitLinesLeaveTheEventWithoutAUnit() {
    SalesOrderConfirmedEvent event =
        confirmWith(
            approvedDraft(),
            List.of(line("MT", BigDecimal.valueOf(100)), line("KG", BigDecimal.valueOf(50))));

    assertThat(event.getUnit()).isNull();
    assertThat(event.getTotalQuantity()).isEqualTo(BigDecimal.valueOf(150));
  }

  @Test
  void noLinesLeaveTheEventWithoutUnitOrQuantity() {
    SalesOrderConfirmedEvent event = confirmWith(approvedDraft(), List.of());

    assertThat(event.getUnit()).isNull();
    assertThat(event.getTotalQuantity()).isEqualTo(BigDecimal.ZERO);
  }

  @Test
  void unknownPartnerLeavesTheCustomerNameEmpty() {
    when(partnerService.findById(tenantId, partnerId)).thenReturn(Optional.empty());

    SalesOrderConfirmedEvent event = confirmWith(approvedDraft(), List.of());

    assertThat(event.getCustomerId()).isEqualTo(partnerId);
    assertThat(event.getCustomerName()).isNull();
  }

  @Test
  void seededDemoOrderIsConfirmedDirectlyUnderTheIntakeConditions() {
    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(partnerId)
            .orderNumber("SO-SEED")
            .orderType(OrderType.SALES)
            .build();
    ReflectionTestUtils.setField(order, "id", orderId);
    when(orderRepository.findByTenantIdAndId(tenantId, orderId)).thenReturn(Optional.of(order));
    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of());

    salesOrderService.confirmDemoSeedOrder(orderId);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    verify(orderIntakeHooks).checkConfirmable(order, List.of());
    verify(domainEventPublisher).publish(any(SalesOrderConfirmedEvent.class));
  }
}
