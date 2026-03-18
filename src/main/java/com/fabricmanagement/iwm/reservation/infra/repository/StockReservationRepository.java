package com.fabricmanagement.iwm.reservation.infra.repository;

import com.fabricmanagement.iwm.reservation.domain.StockReservation;
import com.fabricmanagement.iwm.reservation.domain.StockReservationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {
  List<StockReservation> findBySalesOrderLineIdAndStatusAndDeletedAtIsNull(
      UUID salesOrderLineId, StockReservationStatus status);

  Optional<StockReservation> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
