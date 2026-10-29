package com.fabricmanagement.flowboard.collection.infra.repository;

import com.fabricmanagement.flowboard.collection.domain.CollectionFollowUp;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CollectionFollowUpRepository extends JpaRepository<CollectionFollowUp, UUID> {
  Optional<CollectionFollowUp> findByTenantIdAndId(UUID tenant, UUID id);

  Optional<CollectionFollowUp> findFirstByTenantIdAndObligationIdOrderByEpisodeDesc(
      UUID tenant, UUID obligation);

  /** The obligations that have an open case, in obligation order: the work a scan must check. */
  @Query(
      """
      select c.obligationId from CollectionFollowUp c
      where c.tenantId = :tenant and c.closedAt is null and c.obligationId > :after
      order by c.obligationId
      """)
  List<UUID> openObligationIds(UUID tenant, UUID after, Pageable page);
}
