package com.fabricmanagement.procurement.purchaseorder.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.persistence.DocumentNumberGenerator;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.DataScopeGuard;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.procurement.purchaseorder.app.validation.PurchaseOrderValidationEngine;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrder;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderModuleType;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.fabricmanagement.procurement.purchaseorder.dto.CreatePurchaseOrderRequest;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderLineRepository;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceDataScopeTest {

  private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID PO_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

  @Mock private PurchaseOrderRepository poRepository;
  @Mock private PurchaseOrderLineRepository lineRepository;
  @Mock private PurchaseOrderValidationEngine validationEngine;
  @Mock private ApprovalPort approvalPort;
  @Mock private DocumentNumberGenerator documentNumberGenerator;
  @Mock private DataScopeGuard scopeGuard;

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  void listPurchaseOrdersComposesReadScopeFilter() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    PurchaseOrderService service = service();
    PageRequest pageable = PageRequest.of(0, 20);
    Specification<PurchaseOrder> scopeSpec = (root, query, cb) -> cb.conjunction();
    when(scopeGuard.<PurchaseOrder>scopeFilter("procurement", "read")).thenReturn(scopeSpec);
    when(poRepository.findAll(any(Specification.class), eq(pageable)))
        .thenReturn(new PageImpl<>(List.of(), pageable, 0));

    service.listPurchaseOrders(null, null, pageable);

    verify(scopeGuard).scopeFilter("procurement", "read");
    verify(poRepository).findAll(any(Specification.class), eq(pageable));
  }

  @Test
  void getPurchaseOrderAppliesReadScopeBeforeReturningLines() {
    PurchaseOrder po = purchaseOrder(PurchaseOrderStatus.DRAFT);
    PurchaseOrderService service = service();
    when(poRepository.findById(PO_ID)).thenReturn(Optional.of(po));
    when(lineRepository.findByPurchaseOrderIdAndIsActiveTrueOrderByCreatedAtAsc(PO_ID))
        .thenReturn(List.of());

    service.getPurchaseOrder(PO_ID);

    verify(scopeGuard).assertCanAccess("procurement", "read", po);
    verify(lineRepository).findByPurchaseOrderIdAndIsActiveTrueOrderByCreatedAtAsc(PO_ID);
  }

  @Test
  void changeStatusChecksWriteScopeBeforeMutating() {
    PurchaseOrder po = purchaseOrder(PurchaseOrderStatus.DRAFT);
    PurchaseOrderService service = service();
    when(poRepository.findById(PO_ID)).thenReturn(Optional.of(po));
    doThrow(new AccessDeniedException("denied"))
        .when(scopeGuard)
        .assertCanAccess("procurement", "write", po);

    assertThatThrownBy(() -> service.changeStatus(PO_ID, PurchaseOrderStatus.SENT))
        .isInstanceOf(AccessDeniedException.class);

    verify(validationEngine, never()).validateOnConfirm(any(), any());
    verify(poRepository, never()).save(any(PurchaseOrder.class));
  }

  @Test
  void createPurchaseOrderDoesNotApplyRowScopeGuard() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    PurchaseOrderService service = service();
    when(documentNumberGenerator.generate(
            eq(TENANT_ID), eq("PURCHASE_ORDER"), eq("PO"), any(), eq(5)))
        .thenReturn("PO-20260630-00001");
    when(poRepository.save(any(PurchaseOrder.class)))
        .thenAnswer(
            invocation -> {
              PurchaseOrder po = invocation.getArgument(0);
              po.setId(PO_ID);
              return po;
            });
    when(lineRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.createPurchaseOrder(createRequest());

    verifyNoInteractions(scopeGuard);
  }

  private PurchaseOrderService service() {
    return new PurchaseOrderService(
        poRepository,
        lineRepository,
        validationEngine,
        approvalPort,
        documentNumberGenerator,
        scopeGuard);
  }

  private PurchaseOrder purchaseOrder(PurchaseOrderStatus status) {
    PurchaseOrder po =
        PurchaseOrder.builder()
            .poNumber("PO-20260630-00001")
            .workOrderId(UUID.randomUUID())
            .tradingPartnerId(UUID.randomUUID())
            .status(status)
            .paymentTerms("NET30")
            .expectedDelivery(LocalDate.now().plusDays(10))
            .totalAmount(Money.of(BigDecimal.TEN, "USD"))
            .moduleType(PurchaseOrderModuleType.GENERIC)
            .build();
    po.setId(PO_ID);
    return po;
  }

  private CreatePurchaseOrderRequest createRequest() {
    return CreatePurchaseOrderRequest.builder()
        .workOrderId(UUID.randomUUID())
        .tradingPartnerId(UUID.randomUUID())
        .currency("USD")
        .paymentTerms("NET30")
        .expectedDelivery(LocalDate.now().plusDays(10))
        .moduleType(PurchaseOrderModuleType.GENERIC)
        .lines(
            List.of(
                CreatePurchaseOrderRequest.PurchaseOrderLineRequest.builder()
                    .productDesc("Cotton")
                    .qty(new BigDecimal("10.000"))
                    .unit("KG")
                    .unitPrice(new BigDecimal("3.500"))
                    .currency("USD")
                    .build()))
        .build();
  }
}
