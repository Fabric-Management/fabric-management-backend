package com.fabricmanagement.flowboard.collection.infra.repository;

import com.fabricmanagement.flowboard.collection.domain.CollectionFollowUpScan;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionFollowUpScanRepository
    extends JpaRepository<CollectionFollowUpScan, UUID> {
  Optional<CollectionFollowUpScan> findByTenantId(UUID tenant);
}
