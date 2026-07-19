package com.fabricmanagement.production.execution.batch.infra.repository;

import com.fabricmanagement.production.execution.batch.domain.BatchReservation;
import com.fabricmanagement.production.execution.batch.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchReservationRepository extends JpaRepository<BatchReservation, UUID> {

  List<BatchReservation> findByTenantIdAndBatchIdAndIsActiveTrue(UUID tenantId, UUID batchId);

  List<BatchReservation> findByTenantIdAndBatchIdAndStatusInAndIsActiveTrue(
      UUID tenantId, UUID batchId, Collection<ReservationStatus> statuses);

  @Lock(LockModeType.OPTIMISTIC)
  Optional<BatchReservation> findByIdAndTenantId(UUID id, UUID tenantId);

  List<BatchReservation> findByTenantIdAndReferenceIdAndIsActiveTrue(
      UUID tenantId, UUID referenceId);

  Optional<BatchReservation> findFirstByTenantIdAndBatchIdAndReferenceIdAndStatusInAndIsActiveTrue(
      UUID tenantId, UUID batchId, UUID referenceId, Collection<ReservationStatus> statuses);

  @Query(
      """
      SELECT r.batchId AS batchId, COALESCE(SUM(r.reservedQuantity - r.consumedQuantity), 0) AS quantity
      FROM BatchReservation r
      WHERE r.tenantId = :tenantId
        AND r.batchId IN :batchIds
        AND r.status IN :statuses
        AND r.isActive = true
      GROUP BY r.batchId
      """)
  List<ReservationQuantityRow> sumRemainingRowsByBatchIds(
      @Param("tenantId") UUID tenantId,
      @Param("batchIds") Collection<UUID> batchIds,
      @Param("statuses") Collection<ReservationStatus> statuses);

  @Query(
      """
      SELECT r.batchId AS batchId,
             UPPER(TRIM(r.unit)) AS unit,
             COALESCE(SUM(r.reservedQuantity - r.consumedQuantity), 0) AS quantity,
             COUNT(r.id) AS rowCount
      FROM BatchReservation r
      WHERE r.tenantId = :tenantId
        AND r.batchId IN :batchIds
        AND r.status IN :statuses
        AND r.isActive = true
      GROUP BY r.batchId, UPPER(TRIM(r.unit))
      """)
  List<ReservationUnitQuantityRow> sumRemainingRowsByBatchIdsAndUnit(
      @Param("tenantId") UUID tenantId,
      @Param("batchIds") Collection<UUID> batchIds,
      @Param("statuses") Collection<ReservationStatus> statuses);

  default Map<UUID, BigDecimal> sumActiveRemainingByBatchIds(
      UUID tenantId, Collection<UUID> batchIds) {
    if (batchIds == null || batchIds.isEmpty()) {
      return Map.of();
    }
    return sumRemainingRowsByBatchIds(
            tenantId,
            batchIds,
            List.of(ReservationStatus.ACTIVE, ReservationStatus.PARTIALLY_CONSUMED))
        .stream()
        .collect(
            Collectors.toMap(
                ReservationQuantityRow::getBatchId, ReservationQuantityRow::getQuantity));
  }

  interface ReservationQuantityRow {
    UUID getBatchId();

    BigDecimal getQuantity();
  }

  interface ReservationUnitQuantityRow {
    UUID getBatchId();

    String getUnit();

    BigDecimal getQuantity();

    long getRowCount();
  }
}
