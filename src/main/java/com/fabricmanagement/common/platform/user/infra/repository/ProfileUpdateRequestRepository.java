package com.fabricmanagement.common.platform.user.infra.repository;

import com.fabricmanagement.common.platform.user.domain.ProfileUpdateRequest;
import com.fabricmanagement.common.platform.user.domain.value.ProfileUpdateRequestStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository for ProfileUpdateRequest entity. */
@Repository
public interface ProfileUpdateRequestRepository extends JpaRepository<ProfileUpdateRequest, UUID> {

  /** Find all requests for a user. */
  List<ProfileUpdateRequest> findByTenantIdAndUserIdOrderByCreatedAtDesc(
      UUID tenantId, UUID userId);

  /** Find all pending requests in a tenant. */
  List<ProfileUpdateRequest> findByTenantIdAndStatusOrderByCreatedAtAsc(
      UUID tenantId, ProfileUpdateRequestStatus status);

  /** Find request by ID and tenant. */
  List<ProfileUpdateRequest> findByTenantIdAndId(UUID tenantId, UUID id);
}
