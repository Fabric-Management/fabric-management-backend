package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.DocumentNumberGenerator;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.CurrencyMismatchException;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerResolver;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.app.ruleengine.SalesOrderRuleEngine;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.OrderType;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.UpdateSalesOrderLineRequest;
import com.fabricmanagement.sales.salesorder.dto.UpdateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("deprecation")
class SalesOrderServiceUpdateTest {

  @Mock private SalesOrderRepository orderRepository;
  @Mock private TradingPartnerResolver partnerResolver;
  @Mock private TradingPartnerService partnerService;
  @Mock private SalesOrderLineRepository lineRepository;
  @Mock private SalesOrderRuleEngine ruleEngine;
  @Mock private ModuleSpecsValidator moduleSpecsValidator;
  @Mock private DomainEventPublisher domainEventPublisher;
  @Mock private DocumentNumberGenerator documentNumberGenerator;
  @Mock private ApprovalPort approvalPort;

  @InjectMocks private SalesOrderService salesOrderService;

  @Captor private ArgumentCaptor<SalesOrder> orderCaptor;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private SalesOrder draftOrder;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    draftOrder =
        SalesOrder.builder()
            .totals(com.fabricmanagement.common.util.OrderTotals.zero("GBP"))
            .tradingPartnerId(UUID.randomUUID())
            .orderNumber("SO-123")
            .orderType(OrderType.SALES)
            .build();
    try {
      java.lang.reflect.Field idField =
          com.fabricmanagement.common.infrastructure.persistence.BaseEntity.class.getDeclaredField(
              "id");
      idField.setAccessible(true);
      idField.set(draftOrder, orderId);

      java.lang.reflect.Field versionField =
          com.fabricmanagement.common.infrastructure.persistence.BaseEntity.class.getDeclaredField(
              "version");
      versionField.setAccessible(true);
      versionField.set(draftOrder, 1L);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void updateOrder_versionMismatch_throwsOptimisticLock() {
    when(orderRepository.findByTenantIdAndId(tenantId, orderId))
        .thenReturn(Optional.of(draftOrder));

    UpdateSalesOrderRequest request = new UpdateSalesOrderRequest();
    request.setVersion(99L); // Mismatched version

    assertThatThrownBy(() -> salesOrderService.updateOrder(orderId, request))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  @Test
  void updateOrder_nonDraftReject_throwsOrderDomainException() {
    draftOrder.confirm(); // Transitions to CONFIRMED or pending
    // Let's force it to CANCELLED to be safe
    draftOrder.cancel();

    when(orderRepository.findByTenantIdAndId(tenantId, orderId))
        .thenReturn(Optional.of(draftOrder));

    UpdateSalesOrderRequest request = new UpdateSalesOrderRequest();
    request.setVersion(1L);
    request.setCurrency("TRY");
    request.setLines(new ArrayList<>());

    assertThatThrownBy(() -> salesOrderService.updateOrder(orderId, request))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("does not allow editing");
  }

  @Test
  void updateOrder_emptyLines_softDeletesAll() {
    when(orderRepository.findByTenantIdAndId(tenantId, orderId))
        .thenReturn(Optional.of(draftOrder));

    SalesOrderLine line1 = mock(SalesOrderLine.class);
    SalesOrderLine line2 = mock(SalesOrderLine.class);
    when(line1.getId()).thenReturn(UUID.randomUUID());
    when(line2.getId()).thenReturn(UUID.randomUUID());

    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(line1, line2));

    when(orderRepository.save(any())).thenReturn(draftOrder);

    UpdateSalesOrderRequest request = new UpdateSalesOrderRequest();
    request.setVersion(1L);
    request.setCurrency("TRY");
    request.setLines(new ArrayList<>()); // Empty lines

    salesOrderService.updateOrder(orderId, request);

    verify(line1).delete();
    verify(line2).delete();
  }

  @Test
  void updateOrder_currencyMismatchOnLine_throws() {
    when(orderRepository.findByTenantIdAndId(tenantId, orderId))
        .thenReturn(Optional.of(draftOrder));
    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(new ArrayList<>());

    UpdateSalesOrderLineRequest lineReq = new UpdateSalesOrderLineRequest();
    lineReq.setUnitPrice(BigDecimal.TEN);
    lineReq.setCurrency("USD");

    UpdateSalesOrderRequest request = new UpdateSalesOrderRequest();
    request.setVersion(1L);
    request.setCurrency("TRY");
    request.setLines(List.of(lineReq));

    assertThatThrownBy(() -> salesOrderService.updateOrder(orderId, request))
        .isInstanceOf(CurrencyMismatchException.class);
  }

  @Test
  void updateOrder_calculatesTotalFromLines_skipsNullUnitPrice() {
    when(orderRepository.findByTenantIdAndId(tenantId, orderId))
        .thenReturn(Optional.of(draftOrder));

    SalesOrderLine existingLine =
        SalesOrderLine.builder()
            .unitPrice(Money.of(new BigDecimal("10"), "TRY"))
            .requestedQty(new BigDecimal("5"))
            .build();
    try {
      java.lang.reflect.Field isActiveField =
          com.fabricmanagement.common.infrastructure.persistence.BaseEntity.class.getDeclaredField(
              "isActive");
      isActiveField.setAccessible(true);
      isActiveField.set(existingLine, true);

      java.lang.reflect.Field idField =
          com.fabricmanagement.common.infrastructure.persistence.BaseEntity.class.getDeclaredField(
              "id");
      idField.setAccessible(true);
      idField.set(existingLine, UUID.randomUUID());
    } catch (Exception e) {
    }

    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(existingLine));

    UpdateSalesOrderLineRequest req1 = new UpdateSalesOrderLineRequest();
    req1.setId(existingLine.getId());
    req1.setUnitPrice(new BigDecimal("10"));
    req1.setCurrency("TRY");
    req1.setRequestedQty(new BigDecimal("5"));

    // Line with null unit price (should be skipped in calculation but added to lines)
    UpdateSalesOrderLineRequest req2 = new UpdateSalesOrderLineRequest();
    req2.setUnitPrice(null);
    req2.setRequestedQty(new BigDecimal("10"));

    UpdateSalesOrderRequest request = new UpdateSalesOrderRequest();
    request.setVersion(1L);
    request.setCurrency("TRY");
    request.setLines(List.of(req1, req2));

    when(lineRepository.save(any()))
        .thenAnswer(
            inv -> {
              SalesOrderLine l = inv.getArgument(0);
              try {
                java.lang.reflect.Field isActiveField =
                    com.fabricmanagement.common.infrastructure.persistence.BaseEntity.class
                        .getDeclaredField("isActive");
                isActiveField.setAccessible(true);
                isActiveField.set(l, true);
              } catch (Exception e) {
              }
              return l;
            });

    when(orderRepository.save(any())).thenReturn(draftOrder);

    salesOrderService.updateOrder(orderId, request);

    verify(orderRepository).save(orderCaptor.capture());
    SalesOrder savedOrder = orderCaptor.getValue();

    // (10 * 5) = 50. The other line has null unitPrice and should be skipped.
    assertThat(savedOrder.getTotals().getTotalAmount().getAmount()).isEqualByComparingTo("50");
  }

  @Test
  void updateOrder_homogeneousLineModuleTypes_derivesHeaderModuleType() {
    when(orderRepository.findByTenantIdAndId(tenantId, orderId))
        .thenReturn(Optional.of(draftOrder));

    SalesOrderLine existingLine =
        activeLine(
            UUID.randomUUID(), ModuleType.YARN, BigDecimal.ONE, Money.of(BigDecimal.TEN, "TRY"));
    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(existingLine));
    when(orderRepository.save(any())).thenReturn(draftOrder);

    UpdateSalesOrderLineRequest req = updateLineRequest(existingLine.getId(), ModuleType.FABRIC);
    UpdateSalesOrderRequest request = updateRequest(List.of(req));
    request.setModuleType(ModuleType.YARN);

    salesOrderService.updateOrder(orderId, request);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getModuleType()).isEqualTo(ModuleType.FABRIC);
  }

  @Test
  void updateOrder_mixedLineModuleTypes_derivesNullHeaderModuleType() {
    when(orderRepository.findByTenantIdAndId(tenantId, orderId))
        .thenReturn(Optional.of(draftOrder));

    SalesOrderLine fabricLine =
        activeLine(
            UUID.randomUUID(), ModuleType.FABRIC, BigDecimal.ONE, Money.of(BigDecimal.TEN, "TRY"));
    SalesOrderLine yarnLine =
        activeLine(
            UUID.randomUUID(), ModuleType.YARN, BigDecimal.ONE, Money.of(BigDecimal.TEN, "TRY"));
    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(fabricLine, yarnLine));
    when(orderRepository.save(any())).thenReturn(draftOrder);

    UpdateSalesOrderRequest request =
        updateRequest(
            List.of(
                updateLineRequest(fabricLine.getId(), ModuleType.FABRIC),
                updateLineRequest(yarnLine.getId(), ModuleType.YARN)));
    request.setModuleType(ModuleType.FIBER);

    salesOrderService.updateOrder(orderId, request);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getModuleType()).isNull();
  }

  @Test
  void updateOrder_nullLineModuleTypeIsIgnoredWhenDerivingHeaderModuleType() {
    when(orderRepository.findByTenantIdAndId(tenantId, orderId))
        .thenReturn(Optional.of(draftOrder));

    SalesOrderLine fabricLine =
        activeLine(
            UUID.randomUUID(), ModuleType.FABRIC, BigDecimal.ONE, Money.of(BigDecimal.TEN, "TRY"));
    SalesOrderLine nullLine =
        activeLine(
            UUID.randomUUID(), ModuleType.YARN, BigDecimal.ONE, Money.of(BigDecimal.TEN, "TRY"));
    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(fabricLine, nullLine));
    when(orderRepository.save(any())).thenReturn(draftOrder);

    UpdateSalesOrderRequest request =
        updateRequest(
            List.of(
                updateLineRequest(fabricLine.getId(), ModuleType.FABRIC),
                updateLineRequest(nullLine.getId(), null)));

    salesOrderService.updateOrder(orderId, request);

    verify(orderRepository).save(orderCaptor.capture());
    assertThat(orderCaptor.getValue().getModuleType()).isEqualTo(ModuleType.FABRIC);
  }

  @Test
  void updateOrder_happyPath_updatesAndReturnsDto() {
    when(orderRepository.findByTenantIdAndId(tenantId, orderId))
        .thenReturn(Optional.of(draftOrder));
    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(new ArrayList<>());
    when(orderRepository.save(any())).thenReturn(draftOrder);

    UpdateSalesOrderRequest request = new UpdateSalesOrderRequest();
    request.setVersion(1L);
    request.setCurrency("TRY");
    request.setLines(new ArrayList<>());

    salesOrderService.updateOrder(orderId, request);

    verify(orderRepository).save(draftOrder);
  }

  @Test
  void updateOrder_lineSyncAddsNew() {
    when(orderRepository.findByTenantIdAndId(tenantId, orderId))
        .thenReturn(Optional.of(draftOrder));

    SalesOrderLine existingLine = mock(SalesOrderLine.class);
    when(existingLine.getId()).thenReturn(UUID.randomUUID());
    when(existingLine.getIsActive()).thenReturn(true);
    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(existingLine));

    UpdateSalesOrderLineRequest existingReq = new UpdateSalesOrderLineRequest();
    existingReq.setId(existingLine.getId());
    existingReq.setRequestedQty(BigDecimal.ONE);

    UpdateSalesOrderLineRequest newReq = new UpdateSalesOrderLineRequest();
    newReq.setRequestedQty(BigDecimal.TEN);

    UpdateSalesOrderRequest request = new UpdateSalesOrderRequest();
    request.setVersion(1L);
    request.setCurrency("TRY");
    request.setLines(List.of(existingReq, newReq));

    when(orderRepository.save(any())).thenReturn(draftOrder);
    when(lineRepository.save(any())).thenReturn(mock(SalesOrderLine.class));

    salesOrderService.updateOrder(orderId, request);

    verify(lineRepository).save(any(SalesOrderLine.class));
  }

  @Test
  void updateOrder_lineSyncRemovesMissing() {
    when(orderRepository.findByTenantIdAndId(tenantId, orderId))
        .thenReturn(Optional.of(draftOrder));

    SalesOrderLine lineToKeep = mock(SalesOrderLine.class);
    when(lineToKeep.getId()).thenReturn(UUID.randomUUID());
    when(lineToKeep.getIsActive()).thenReturn(true);

    SalesOrderLine lineToRemove = mock(SalesOrderLine.class);
    when(lineToRemove.getId()).thenReturn(UUID.randomUUID());

    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(lineToKeep, lineToRemove));

    UpdateSalesOrderLineRequest keepReq = new UpdateSalesOrderLineRequest();
    keepReq.setId(lineToKeep.getId());
    keepReq.setRequestedQty(BigDecimal.ONE);

    UpdateSalesOrderRequest request = new UpdateSalesOrderRequest();
    request.setVersion(1L);
    request.setCurrency("TRY");
    request.setLines(List.of(keepReq));

    when(orderRepository.save(any())).thenReturn(draftOrder);

    salesOrderService.updateOrder(orderId, request);

    verify(lineToRemove).delete();
  }

  @Test
  void updateOrder_lineSyncUpdatesExisting() {
    when(orderRepository.findByTenantIdAndId(tenantId, orderId))
        .thenReturn(Optional.of(draftOrder));

    SalesOrderLine existingLine = mock(SalesOrderLine.class);
    when(existingLine.getId()).thenReturn(UUID.randomUUID());
    when(existingLine.getIsActive()).thenReturn(true);

    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(List.of(existingLine));

    UpdateSalesOrderLineRequest req = new UpdateSalesOrderLineRequest();
    req.setId(existingLine.getId());
    req.setRequestedQty(BigDecimal.TEN);
    req.setCurrency("TRY");

    UpdateSalesOrderRequest request = new UpdateSalesOrderRequest();
    request.setVersion(1L);
    request.setCurrency("TRY");
    request.setLines(List.of(req));

    when(orderRepository.save(any())).thenReturn(draftOrder);

    salesOrderService.updateOrder(orderId, request);

    verify(existingLine).setRequestedQty(BigDecimal.TEN);
  }

  @Test
  void updateOrder_lineWithUnknownId_throws() {
    when(orderRepository.findByTenantIdAndId(tenantId, orderId))
        .thenReturn(Optional.of(draftOrder));
    when(lineRepository.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(orderId))
        .thenReturn(new ArrayList<>());

    UpdateSalesOrderLineRequest req = new UpdateSalesOrderLineRequest();
    req.setId(UUID.randomUUID());

    UpdateSalesOrderRequest request = new UpdateSalesOrderRequest();
    request.setVersion(1L);
    request.setCurrency("TRY");
    request.setLines(List.of(req));

    assertThatThrownBy(() -> salesOrderService.updateOrder(orderId, request))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("Line not found");
  }

  private UpdateSalesOrderRequest updateRequest(List<UpdateSalesOrderLineRequest> lines) {
    UpdateSalesOrderRequest request = new UpdateSalesOrderRequest();
    request.setVersion(1L);
    request.setCurrency("TRY");
    request.setLines(lines);
    return request;
  }

  private UpdateSalesOrderLineRequest updateLineRequest(UUID id, ModuleType moduleType) {
    UpdateSalesOrderLineRequest req = new UpdateSalesOrderLineRequest();
    req.setId(id);
    req.setProductDesc("Cotton fabric");
    req.setRequestedQty(BigDecimal.ONE);
    req.setUnit("KG");
    req.setUnitPrice(BigDecimal.TEN);
    req.setCurrency("TRY");
    req.setModuleType(moduleType);
    return req;
  }

  private SalesOrderLine activeLine(
      UUID id, ModuleType moduleType, BigDecimal requestedQty, Money unitPrice) {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .productDesc("Cotton fabric")
            .requestedQty(requestedQty)
            .unit("KG")
            .unitPrice(unitPrice)
            .moduleType(moduleType)
            .build();
    setBaseField(line, "id", id);
    setBaseField(line, "isActive", true);
    return line;
  }

  private void setBaseField(SalesOrderLine line, String fieldName, Object value) {
    try {
      java.lang.reflect.Field field =
          com.fabricmanagement.common.infrastructure.persistence.BaseEntity.class.getDeclaredField(
              fieldName);
      field.setAccessible(true);
      field.set(line, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
