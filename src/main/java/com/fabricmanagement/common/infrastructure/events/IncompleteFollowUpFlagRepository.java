package com.fabricmanagement.common.infrastructure.events;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IncompleteFollowUpFlagRepository
    extends JpaRepository<IncompleteFollowUpFlag, UUID> {

  Optional<IncompleteFollowUpFlag> findByTenantIdAndPublicationId(
      UUID tenantId, UUID publicationId);

  List<IncompleteFollowUpFlag> findByTenantIdAndEntityTypeAndEntityIdAndStatus(
      UUID tenantId, String entityType, UUID entityId, FollowUpFlagStatus status);

  List<IncompleteFollowUpFlag> findByTenantIdAndStatus(UUID tenantId, FollowUpFlagStatus status);
}
