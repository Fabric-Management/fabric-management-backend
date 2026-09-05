package com.fabricmanagement.procurement.purchaseorder.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.procurement.purchaseorder.infra.repository.PurchaseOrderLineRepository;
import com.fabricmanagement.product.yarn.app.port.YarnUsageSignal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcurementYarnUsageAdapterTest {

  @Mock private PurchaseOrderLineRepository repository;

  @Test
  void mapsReferencedProductsAndDistinctUnlinkedOrderCount() {
    UUID tenantId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    when(repository.findOpenReferencedProductIds(tenantId)).thenReturn(List.of(productId));
    when(repository.countOpenUnlinkedYarnPurchaseOrders(tenantId)).thenReturn(1L);

    var discovery = new ProcurementYarnUsageAdapter(repository).discover(tenantId);

    assertThat(discovery.referencedProductIds().get(YarnUsageSignal.OPEN_PURCHASE_ORDER))
        .containsExactly(productId);
    assertThat(discovery.unlinkedYarnDocumentCounts())
        .containsEntry(YarnUsageSignal.OPEN_PURCHASE_ORDER, 1L);
  }
}
