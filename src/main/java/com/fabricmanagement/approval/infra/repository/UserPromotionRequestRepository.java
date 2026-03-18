package com.fabricmanagement.approval.infra.repository;

import com.fabricmanagement.approval.domain.PromotionRequestStatus;
import com.fabricmanagement.approval.domain.UserPromotionRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPromotionRequestRepository extends JpaRepository<UserPromotionRequest, UUID> {

  List<UserPromotionRequest> findByTenantIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, PromotionRequestStatus status);

  List<UserPromotionRequest> findByTenantIdAndUserIdAndDeletedAtIsNull(UUID tenantId, UUID userId);

  Optional<UserPromotionRequest> findByTenantIdAndUserIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, UUID userId, PromotionRequestStatus status);

  int countByTenantIdAndUserIdAndStatusAndDeletedAtIsNull(
      UUID tenantId, UUID userId, PromotionRequestStatus status);
}
