package com.fabricmanagement.approval.infra.repository;

import com.fabricmanagement.approval.domain.ApprovalEntityType;
import com.fabricmanagement.approval.domain.ApprovalPolicy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalPolicyRepository extends JpaRepository<ApprovalPolicy, UUID> {

  List<ApprovalPolicy> findByTenantIdAndDeletedAtIsNull(UUID tenantId);

  @Query(
      "SELECT p FROM ApprovalPolicy p WHERE p.tenantId = :tenantId AND p.entityType = :entityType AND p.isActive = true AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
  List<ApprovalPolicy> findActivePoliciesForEntity(
      @Param("tenantId") UUID tenantId, @Param("entityType") ApprovalEntityType entityType);
}
