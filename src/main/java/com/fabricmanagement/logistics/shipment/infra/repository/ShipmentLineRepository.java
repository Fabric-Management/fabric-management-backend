package com.fabricmanagement.logistics.shipment.infra.repository;

import com.fabricmanagement.logistics.shipment.domain.ShipmentLine;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipmentLineRepository extends JpaRepository<ShipmentLine, UUID> {
  interface DispatchedQuantity {
    com.fabricmanagement.logistics.shipment.domain.ShipmentUnit getUnit();

    java.math.BigDecimal getQuantity();
  }

  @org.springframework.data.jpa.repository.Query(
      """
      select l.unit as unit, sum(b.quantity) as quantity
      from ShipmentLine l join l.shipment s join l.batches b
      where l.tenantId = :tenantId and s.tenantId = :tenantId
        and l.salesOrderLineId = :lineId and s.id <> :excludedShipment
        and l.isActive = true and s.isActive = true and s.status in :statuses
      group by l.unit
      """)
  List<DispatchedQuantity> sumDispatched(
      @org.springframework.data.repository.query.Param("tenantId") UUID tenant,
      @org.springframework.data.repository.query.Param("lineId") UUID line,
      @org.springframework.data.repository.query.Param("excludedShipment") UUID excluded,
      @org.springframework.data.repository.query.Param("statuses")
          List<com.fabricmanagement.logistics.shipment.domain.ShipmentStatus> statuses);

  default List<DispatchedQuantity> dispatchedQuantities(UUID tenant, UUID line, UUID excluded) {
    return sumDispatched(
        tenant,
        line,
        excluded,
        List.of(
            com.fabricmanagement.logistics.shipment.domain.ShipmentStatus.PICKED_UP,
            com.fabricmanagement.logistics.shipment.domain.ShipmentStatus.IN_TRANSIT,
            com.fabricmanagement.logistics.shipment.domain.ShipmentStatus.OUT_FOR_DELIVERY,
            com.fabricmanagement.logistics.shipment.domain.ShipmentStatus.DELIVERED,
            com.fabricmanagement.logistics.shipment.domain.ShipmentStatus.DELIVERY_FAILED,
            com.fabricmanagement.logistics.shipment.domain.ShipmentStatus.RETURNED));
  }

  Optional<ShipmentLine> findByIdAndTenantId(UUID id, UUID tenantId);

  List<ShipmentLine> findAllByShipmentIdAndTenantId(UUID shipmentId, UUID tenantId);
}
