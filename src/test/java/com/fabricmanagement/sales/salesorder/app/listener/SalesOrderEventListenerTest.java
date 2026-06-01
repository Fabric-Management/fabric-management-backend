package com.fabricmanagement.sales.salesorder.app.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.logistics.shipment.domain.event.ShipmentLineConfirmedEvent;
import com.fabricmanagement.sales.salesorder.app.ShipmentProgressService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesOrderEventListenerTest {

  @Mock private ShipmentProgressService shipmentProgressService;

  @InjectMocks private SalesOrderEventListener listener;

  private UUID tenantId;
  private UUID shipmentLineId;
  private UUID salesOrderLineId;
  private BigDecimal confirmedQuantity;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    shipmentLineId = UUID.randomUUID();
    salesOrderLineId = UUID.randomUUID();
    confirmedQuantity = new BigDecimal("100");
  }

  @Test
  void onShipmentLineConfirmed_whenLineExists_delegatesToBothPhases() {
    ShipmentLineConfirmedEvent event =
        new ShipmentLineConfirmedEvent(
            tenantId, shipmentLineId, salesOrderLineId, confirmedQuantity);
    UUID salesOrderId = UUID.randomUUID();

    when(shipmentProgressService.recordLineShipment(
            salesOrderLineId, shipmentLineId, confirmedQuantity))
        .thenReturn(salesOrderId);

    listener.onShipmentLineConfirmed(event);

    verify(shipmentProgressService)
        .recordLineShipment(salesOrderLineId, shipmentLineId, confirmedQuantity);
    verify(shipmentProgressService).updateOrderShipmentStatus(salesOrderId);
  }

  @Test
  void onShipmentLineConfirmed_whenLineDoesNotExist_skipsPhase2() {
    ShipmentLineConfirmedEvent event =
        new ShipmentLineConfirmedEvent(
            tenantId, shipmentLineId, salesOrderLineId, confirmedQuantity);

    // Returns null to simulate line not found
    when(shipmentProgressService.recordLineShipment(
            salesOrderLineId, shipmentLineId, confirmedQuantity))
        .thenReturn(null);

    listener.onShipmentLineConfirmed(event);

    verify(shipmentProgressService)
        .recordLineShipment(salesOrderLineId, shipmentLineId, confirmedQuantity);
    verify(shipmentProgressService, never()).updateOrderShipmentStatus(any(UUID.class));
  }
}
