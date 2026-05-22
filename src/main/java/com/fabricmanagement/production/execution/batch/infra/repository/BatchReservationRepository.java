package com.fabricmanagement.production.execution.batch.infra.repository;

import com.fabricmanagement.production.execution.batch.domain.BatchReservation;
import com.fabricmanagement.production.execution.batch.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
}
