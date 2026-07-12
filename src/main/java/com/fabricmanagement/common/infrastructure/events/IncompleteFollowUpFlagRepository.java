package com.fabricmanagement.common.infrastructure.events;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IncompleteFollowUpFlagRepository
    extends JpaRepository<IncompleteFollowUpFlag, UUID> {

  Optional<IncompleteFollowUpFlag> findByTenantIdAndPublicationId(
      UUID tenantId, UUID publicationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select f from IncompleteFollowUpFlag f where f.tenantId = :tenantId and f.id = :id")
  Optional<IncompleteFollowUpFlag> findByTenantIdAndIdForUpdate(
      @Param("tenantId") UUID tenantId, @Param("id") UUID id);

  List<IncompleteFollowUpFlag> findByTenantIdAndEntityTypeAndEntityIdAndStatus(
      UUID tenantId, String entityType, UUID entityId, FollowUpFlagStatus status);

  List<IncompleteFollowUpFlag> findByTenantIdAndStatus(UUID tenantId, FollowUpFlagStatus status);
}
