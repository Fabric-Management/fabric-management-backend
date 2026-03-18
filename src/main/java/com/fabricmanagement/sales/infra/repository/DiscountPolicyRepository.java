package com.fabricmanagement.sales.infra.repository;

import com.fabricmanagement.sales.domain.policy.DiscountPolicy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscountPolicyRepository extends JpaRepository<DiscountPolicy, UUID> {

  /** Gets the active discount policy for the tenant and module type. */
  @Query(
      """
      SELECT dp FROM DiscountPolicy dp
      WHERE dp.tenantId = :tenantId
        AND dp.moduleType = :moduleType
        AND dp.isActive = true
      """)
  Optional<DiscountPolicy> findActiveByModuleType(
      @Param("tenantId") UUID tenantId, @Param("moduleType") String moduleType);
}
