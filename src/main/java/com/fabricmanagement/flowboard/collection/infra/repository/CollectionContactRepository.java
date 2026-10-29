package com.fabricmanagement.flowboard.collection.infra.repository;

import com.fabricmanagement.flowboard.collection.domain.CollectionContact;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionContactRepository extends JpaRepository<CollectionContact, UUID> {
  Page<CollectionContact> findByTenantIdAndFollowUpIdOrderByCreatedAtDescIdDesc(
      UUID tenant, UUID followUp, Pageable page);
}
