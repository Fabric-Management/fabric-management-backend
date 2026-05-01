package com.fabricmanagement.platform.user.infra.repository;

import com.fabricmanagement.platform.user.domain.PermissionOverride;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionOverrideRepository extends JpaRepository<PermissionOverride, UUID> {

  @Query(
      "SELECT po FROM PermissionOverride po WHERE po.tenantId = :tenantId "
          + "AND po.userId = :userId AND po.isActive = true "
          + "AND (po.expiresAt IS NULL OR po.expiresAt > CURRENT_TIMESTAMP)")
  List<PermissionOverride> findActiveOverrides(
      @Param("tenantId") UUID tenantId, @Param("userId") UUID userId);
}
