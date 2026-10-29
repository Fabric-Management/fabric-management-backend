package com.fabricmanagement.flowboard.collection.infra.repository;

import com.fabricmanagement.flowboard.collection.domain.CollectionFollowUpCommand;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionFollowUpCommandRepository
    extends JpaRepository<CollectionFollowUpCommand, UUID> {
  Optional<CollectionFollowUpCommand> findByTenantIdAndOperationId(UUID tenant, UUID operation);
}
