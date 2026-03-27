package com.fabricmanagement.approval.infra.repository;

import com.fabricmanagement.approval.domain.ApprovalEntityType;
import com.fabricmanagement.approval.domain.ApprovalRequest;
import com.fabricmanagement.approval.domain.ApprovalRequestStatus;
import com.fabricmanagement.approval.domain.ApproverRole;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

  List<ApprovalRequest> findByTenantIdAndRequestedByAndDeletedAtIsNull(
      UUID tenantId, UUID requestedBy);

  // Örneğin bir amirin beklemedeki istekleri görmesi gerekiyorsa
  // İlerleyen safhalarda Authority check eklenecek, basit tutuldu:
  List<ApprovalRequest> findByTenantIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, ApprovalRequestStatus status);

  Optional<ApprovalRequest> findByTenantIdAndEntityTypeAndEntityIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, ApprovalEntityType entityType, UUID entityId, ApprovalRequestStatus status);

  @Query(
      "SELECT a FROM ApprovalRequest a WHERE a.status = :status AND a.expiresAt < :now AND a.deletedAt IS NULL")
  List<ApprovalRequest> findExpiredPendingRequests(
      @Param("status") ApprovalRequestStatus status, @Param("now") OffsetDateTime now);

  @Query(
      "SELECT COUNT(a) FROM ApprovalRequest a WHERE a.tenantId = :tenantId AND a.requestedBy = :userId AND a.status = :status AND a.deletedAt IS NULL")
  int countApprovedRequestsForUser(
      @Param("tenantId") UUID tenantId,
      @Param("userId") UUID userId,
      @Param("status") ApprovalRequestStatus status);

  @Query(
      "SELECT a FROM ApprovalRequest a JOIN a.policy p WHERE a.tenantId = :tenantId AND a.status = :status AND a.deletedAt IS NULL AND p.approverRole = :approverRole ORDER BY a.createdAt DESC")
  List<ApprovalRequest> findPendingRequestsByApproverRole(
      @Param("tenantId") UUID tenantId,
      @Param("status") ApprovalRequestStatus status,
      @Param("approverRole") ApproverRole approverRole);
}
