package com.fabricmanagement.logistics.shipment.infra.repository;

import com.fabricmanagement.logistics.shipment.domain.ShipmentLineBatch;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipmentLineBatchRepository extends JpaRepository<ShipmentLineBatch, UUID> {
  List<ShipmentLineBatch> findAllByShipmentLineIdIn(Collection<UUID> shipmentLineIds);
}
