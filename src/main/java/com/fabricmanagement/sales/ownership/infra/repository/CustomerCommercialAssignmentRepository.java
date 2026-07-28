package com.fabricmanagement.sales.ownership.infra.repository;

import com.fabricmanagement.sales.ownership.domain.CustomerCommercialAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Retention-safe assignment persistence surface.
 *
 * <p>Deliberately does not extend CrudRepository/JpaRepository and exposes no delete operation.
 */
@org.springframework.stereotype.Repository
public interface CustomerCommercialAssignmentRepository
    extends Repository<CustomerCommercialAssignment, UUID> {

  CustomerCommercialAssignment save(CustomerCommercialAssignment assignment);

  void flush();

  Optional<CustomerCommercialAssignment> findByTenantIdAndCustomerIdAndValidToIsNull(
      UUID tenantId, UUID customerId);

  List<CustomerCommercialAssignment> findAllByTenantIdAndCustomerIdOrderByValidFromDescIdDesc(
      UUID tenantId, UUID customerId);

  List<CustomerCommercialAssignment>
      findAllByTenantIdAndRepresentativeIdAndValidToIsNullOrderByCustomerId(
          UUID tenantId, UUID representativeId);

  boolean existsByTenantIdAndCustomerId(UUID tenantId, UUID customerId);
}
