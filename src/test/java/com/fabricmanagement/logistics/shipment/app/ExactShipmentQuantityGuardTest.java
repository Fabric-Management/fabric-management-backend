package com.fabricmanagement.logistics.shipment.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.logistics.common.exception.LogisticsDomainException;
import com.fabricmanagement.logistics.shipment.domain.*;
import com.fabricmanagement.logistics.shipment.domain.port.SalesShipmentQuantityPort;
import com.fabricmanagement.logistics.shipment.infra.repository.ShipmentLineRepository;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;

class ExactShipmentQuantityGuardTest {
  final UUID tenant = UUID.randomUUID(), line = UUID.randomUUID(), shipmentId = UUID.randomUUID();
  final SalesShipmentQuantityPort sales = mock(SalesShipmentQuantityPort.class);
  final ShipmentLineRepository lines = mock(ShipmentLineRepository.class);
  final ExactShipmentQuantityGuard guard = new ExactShipmentQuantityGuard(sales, lines);

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    when(sales.lockExactLimits(any()))
        .thenReturn(Map.of(line, new SalesShipmentQuantityPort.Limit(new BigDecimal("2050"), "M")));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void wholeRollSourceCannotBeDispatchedAsThoughItWereTheExactDelivery() {
    assertThatThrownBy(() -> guard.assertPickupAllowed(shipment("2125")))
        .isInstanceOf(LogisticsDomainException.class)
        .hasMessageContaining("exceeds");
  }

  @Test
  void exactDeliveryAndPartialDeliveryAreAllowedWithoutDeclaringCompletion() {
    guard.assertPickupAllowed(shipment("2050"));
    guard.assertPickupAllowed(shipment("2000"));
    verify(sales, times(2)).lockExactLimits(List.of(line));
  }

  @Test
  void priorDispatchedQuantityCountsEvenBeforeTheSalesProgressEventArrives() {
    var prior = mock(ShipmentLineRepository.DispatchedQuantity.class);
    when(prior.getUnit()).thenReturn(ShipmentUnit.METER);
    when(prior.getQuantity()).thenReturn(new BigDecimal("2000"));
    when(lines.dispatchedQuantities(tenant, line, shipmentId)).thenReturn(List.of(prior));
    guard.assertPickupAllowed(shipment("50"));
    assertThatThrownBy(() -> guard.assertPickupAllowed(shipment("125")))
        .isInstanceOf(LogisticsDomainException.class);
  }

  @Test
  void rollCountsCannotBeComparedWithMetres() {
    var shipment = shipment("17");
    shipment.getLines().getFirst().setUnit(ShipmentUnit.ROLL);
    assertThatThrownBy(() -> guard.assertPickupAllowed(shipment))
        .isInstanceOf(LogisticsDomainException.class)
        .hasMessageContaining("measured quantity");
  }

  @Test
  void anInvalidPriorMeasurementCannotOffsetTheExactLimit() {
    var prior = mock(ShipmentLineRepository.DispatchedQuantity.class);
    when(prior.getUnit()).thenReturn(ShipmentUnit.METER);
    when(prior.getQuantity()).thenReturn(new BigDecimal("-75"));
    when(lines.dispatchedQuantities(tenant, line, shipmentId)).thenReturn(List.of(prior));
    assertThatThrownBy(() -> guard.assertPickupAllowed(shipment("2125")))
        .isInstanceOf(LogisticsDomainException.class)
        .hasMessageContaining("valid measured quantity");
  }

  @Test
  void distributionsForTheSameOrderLineAreSummedTogether() {
    var shipment = shipment("2000");
    var extra = loaded("125");
    var current = shipment.getLines().getFirst();
    when(shipment.getLines()).thenReturn(List.of(current, extra));
    assertThatThrownBy(() -> guard.assertPickupAllowed(shipment))
        .isInstanceOf(LogisticsDomainException.class);
  }

  private Shipment shipment(String quantity) {
    var shipment = mock(Shipment.class);
    when(shipment.getId()).thenReturn(shipmentId);
    when(shipment.getLines()).thenReturn(List.of(loaded(quantity)));
    return shipment;
  }

  private ShipmentLine loaded(String quantity) {
    var row =
        ShipmentLine.builder()
            .shipmentId(shipmentId)
            .salesOrderLineId(line)
            .lineNumber(1)
            .quantity(new BigDecimal(quantity))
            .unit(ShipmentUnit.METER)
            .build();
    row.addBatch(UUID.randomUUID(), new BigDecimal(quantity), null);
    return row;
  }
}
