package com.fabricmanagement.sales.ownership.infra.repository;

import com.fabricmanagement.sales.ownership.domain.OwnershipPolicy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

@org.springframework.stereotype.Repository
public interface OwnershipPolicyRepository extends Repository<OwnershipPolicy, UUID> {

  Optional<OwnershipPolicy> findByTenantId(UUID tenantId);
}
