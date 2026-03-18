package com.fabricmanagement.flowboard.dashboard.infra.repository;

import com.fabricmanagement.flowboard.dashboard.domain.UserPerformanceSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserPerformanceSnapshotRepository
    extends JpaRepository<UserPerformanceSnapshot, UUID> {

  Optional<UserPerformanceSnapshot> findByTenantIdAndUserIdAndSnapshotDateAndDeletedAtIsNull(
      UUID tenantId, UUID userId, LocalDate snapshotDate);

  @Query(
      "SELECT u FROM UserPerformanceSnapshot u WHERE u.tenantId = :tenantId AND u.snapshotDate = :date AND u.deletedAt IS NULL ORDER BY u.totalPoints DESC")
  List<UserPerformanceSnapshot> getLeaderboard(
      @Param("tenantId") UUID tenantId, @Param("date") LocalDate date);
}
