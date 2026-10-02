package com.fabricmanagement.sales.orderintake.infra.repository;

import com.fabricmanagement.sales.orderintake.domain.LineGreigeCover;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LineGreigeCoverRepository extends JpaRepository<LineGreigeCover, UUID> {

  Optional<LineGreigeCover> findFirstByTenantIdAndSalesOrderLineIdAndStatus(
      UUID tenantId, UUID salesOrderLineId, LineGreigeCover.Status status);

  List<LineGreigeCover> findByTenantIdAndSalesOrderLineIdInAndStatus(
      UUID tenantId, Collection<UUID> lineIds, LineGreigeCover.Status status);
}
