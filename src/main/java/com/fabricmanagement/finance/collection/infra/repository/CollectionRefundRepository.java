package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionRefund;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionRefundRepository extends JpaRepository<CollectionRefund, UUID> {
  Optional<CollectionRefund> findByTenantIdAndId(UUID tenant, UUID id);

  List<CollectionRefund> findByTenantIdAndObligationIdOrderByCreatedAtAscIdAsc(
      UUID tenant, UUID obligation);

  List<CollectionRefund> findByTenantIdAndObligationIdIn(UUID tenant, Collection<UUID> obligations);

  List<CollectionRefund> findByTenantIdAndPaymentId(UUID tenant, UUID payment);
}
