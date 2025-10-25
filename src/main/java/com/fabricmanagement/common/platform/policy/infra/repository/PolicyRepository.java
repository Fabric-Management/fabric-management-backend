package com.fabricmanagement.common.platform.policy.infra.repository;

import com.fabricmanagement.common.platform.policy.domain.Policy;
import com.fabricmanagement.common.platform.policy.domain.PolicyEffect;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Policy entity.
 */
@Repository
public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    Optional<Policy> findByPolicyId(String policyId);

    List<Policy> findByResourceAndActionAndEnabledTrueOrderByPriorityDesc(String resource, String action);

    List<Policy> findByResourceAndEnabledTrueOrderByPriorityDesc(String resource);

    List<Policy> findByEffectAndEnabledTrue(PolicyEffect effect);

    List<Policy> findByEnabledTrueOrderByPriorityDesc();

    boolean existsByPolicyId(String policyId);

    @Query("SELECT p FROM Policy p WHERE p.enabled = true AND p.resource = :resource AND p.action = :action ORDER BY p.priority DESC")
    List<Policy> findApplicablePolicies(@Param("resource") String resource, @Param("action") String action);

    long countByEnabledTrue();
}

