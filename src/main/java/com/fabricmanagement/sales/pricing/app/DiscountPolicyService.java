package com.fabricmanagement.sales.pricing.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.pricing.domain.DiscountPolicy;
import com.fabricmanagement.sales.pricing.infra.repository.DiscountPolicyRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiscountPolicyService {

  private final DiscountPolicyRepository repository;

  /** The active policy for a module, or empty — for callers that treat "none yet" as normal. */
  @Transactional(readOnly = true)
  public Optional<DiscountPolicy> findActivePolicy(String moduleType) {
    UUID tenantId = TenantContext.requireTenantId();
    return repository.findActiveByModuleType(tenantId, moduleType);
  }

  @Transactional(readOnly = true)
  public DiscountPolicy getActivePolicy(String moduleType) {
    return findActivePolicy(moduleType)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No active discount policy found for module: " + moduleType));
  }

  @Transactional
  public DiscountPolicy savePolicy(DiscountPolicy policy) {
    UUID tenantId = TenantContext.requireTenantId();
    policy.setTenantId(tenantId);

    // Deactivate the old active policy for the same module type
    repository
        .findActiveByModuleType(tenantId, policy.getModuleType())
        .ifPresent(
            old -> {
              old.markAsDeleted();
              repository.save(old);
            });

    return repository.save(policy);
  }
}
