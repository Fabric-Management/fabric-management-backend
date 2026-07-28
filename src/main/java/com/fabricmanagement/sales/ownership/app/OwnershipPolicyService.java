package com.fabricmanagement.sales.ownership.app;

import com.fabricmanagement.sales.ownership.domain.OwnershipPolicy;
import com.fabricmanagement.sales.ownership.infra.repository.OwnershipPolicyRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OwnershipPolicyService {

  private final OwnershipPolicyRepository repository;

  @Transactional(readOnly = true)
  public OwnershipPolicy requirePolicy(UUID tenantId) {
    return repository
        .findByTenantId(tenantId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Sales ownership policy is missing for tenant " + tenantId));
  }
}
