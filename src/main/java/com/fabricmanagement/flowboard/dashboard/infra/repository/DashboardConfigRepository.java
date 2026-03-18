package com.fabricmanagement.flowboard.dashboard.infra.repository;

import com.fabricmanagement.flowboard.dashboard.domain.DashboardConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DashboardConfigRepository extends JpaRepository<DashboardConfig, UUID> {

  List<DashboardConfig> findByTenantIdAndUserIdAndDeletedAtIsNull(UUID tenantId, UUID userId);

  Optional<DashboardConfig> findByTenantIdAndUserIdAndIsDefaultTrueAndDeletedAtIsNull(
      UUID tenantId, UUID userId);

  @Query(
      "SELECT d FROM DashboardConfig d WHERE d.tenantId = :tenantId AND d.userId = :userId AND d.id = :id AND d.deletedAt IS NULL")
  Optional<DashboardConfig> findByIdForUser(
      @Param("tenantId") UUID tenantId, @Param("userId") UUID userId, @Param("id") UUID id);
}
