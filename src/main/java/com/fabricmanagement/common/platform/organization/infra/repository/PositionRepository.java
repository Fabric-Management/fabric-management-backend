package com.fabricmanagement.common.platform.organization.infra.repository;

import com.fabricmanagement.common.platform.organization.domain.Position;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for Position entity. */
@Repository
public interface PositionRepository extends JpaRepository<Position, UUID> {

  /**
   * Find all positions by tenant ID.
   *
   * <p><b>Optimized:</b> JOIN FETCH to avoid N+1 queries for defaultRole and hierarchicalParent.
   */
  @Query(
      "SELECT DISTINCT p FROM Position p "
          + "LEFT JOIN FETCH p.defaultRole "
          + "LEFT JOIN FETCH p.hierarchicalParent "
          + "LEFT JOIN FETCH p.department "
          + "WHERE p.tenantId = :tenantId AND p.isActive = true ORDER BY p.displayOrder, p.positionName")
  List<Position> findByTenantId(@Param("tenantId") UUID tenantId);

  /** Find all positions by department ID. */
  @Query(
      "SELECT p FROM Position p WHERE p.departmentId = :departmentId AND p.isActive = true ORDER BY p.displayOrder, p.positionName")
  List<Position> findByDepartmentId(@Param("departmentId") UUID departmentId);

  /** Find position by tenant ID and position code. */
  @Query("SELECT p FROM Position p WHERE p.tenantId = :tenantId AND p.positionCode = :positionCode")
  Optional<Position> findByTenantIdAndPositionCode(
      @Param("tenantId") UUID tenantId, @Param("positionCode") String positionCode);

  /** Check if position exists by tenant ID and position code. */
  @Query(
      "SELECT COUNT(p) > 0 FROM Position p WHERE p.tenantId = :tenantId AND p.positionCode = :positionCode")
  boolean existsByTenantIdAndPositionCode(
      @Param("tenantId") UUID tenantId, @Param("positionCode") String positionCode);
}
