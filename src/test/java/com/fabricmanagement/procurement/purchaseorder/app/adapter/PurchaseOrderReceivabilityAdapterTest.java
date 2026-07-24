package com.fabricmanagement.procurement.purchaseorder.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrder;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrderStatus;
import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderReceivabilityAdapterTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID PO_ID = UUID.randomUUID();

  @Mock private PurchaseOrderRepository purchaseOrderRepository;

  @ParameterizedTest
  @EnumSource(
      value = PurchaseOrderStatus.class,
      names = {"CONFIRMED", "PARTIALLY_RECEIVED"})
  void acceptsReceivableStatuses(PurchaseOrderStatus status) {
    PurchaseOrder purchaseOrder = PurchaseOrder.builder().status(status).build();
    when(purchaseOrderRepository.findByIdAndTenantIdAndIsActiveTrue(PO_ID, TENANT_ID))
        .thenReturn(Optional.of(purchaseOrder));

    boolean receivable =
        new PurchaseOrderReceivabilityAdapter(purchaseOrderRepository)
            .isReceivable(TENANT_ID, PO_ID);

    assertThat(receivable).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = PurchaseOrderStatus.class,
      names = {"CONFIRMED", "PARTIALLY_RECEIVED"},
      mode = EnumSource.Mode.EXCLUDE)
  void rejectsOtherStatuses(PurchaseOrderStatus status) {
    PurchaseOrder purchaseOrder = PurchaseOrder.builder().status(status).build();
    when(purchaseOrderRepository.findByIdAndTenantIdAndIsActiveTrue(PO_ID, TENANT_ID))
        .thenReturn(Optional.of(purchaseOrder));

    boolean receivable =
        new PurchaseOrderReceivabilityAdapter(purchaseOrderRepository)
            .isReceivable(TENANT_ID, PO_ID);

    assertThat(receivable).isFalse();
  }

  @Test
  void rejectsMissingOrWrongTenantPurchaseOrder() {
    when(purchaseOrderRepository.findByIdAndTenantIdAndIsActiveTrue(PO_ID, TENANT_ID))
        .thenReturn(Optional.empty());

    boolean receivable =
        new PurchaseOrderReceivabilityAdapter(purchaseOrderRepository)
            .isReceivable(TENANT_ID, PO_ID);

    assertThat(receivable).isFalse();
  }
}
