package com.fabricmanagement.production.execution.warehouse.infra.repository;

import com.fabricmanagement.production.execution.warehouse.domain.LocationStatus;
import com.fabricmanagement.production.execution.warehouse.domain.WarehouseLocation;
import com.fabricmanagement.production.execution.warehouse.domain.WarehouseLocationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WarehouseLocationRepository extends JpaRepository<WarehouseLocation, UUID> {

  Optional<WarehouseLocation> findByTenantIdAndCode(UUID tenantId, String code);

  List<WarehouseLocation> findByParentId(UUID parentId);

  List<WarehouseLocation> findByParentIdAndIsActiveTrue(UUID parentId);

  List<WarehouseLocation> findByIsActiveTrue();

  List<WarehouseLocation> findByIsActiveTrueOrderBySortOrderAscNameAsc();

  List<WarehouseLocation> findByTypeAndIsActiveTrue(WarehouseLocationType type);

  List<WarehouseLocation> findByTypeInAndIsActiveTrue(List<WarehouseLocationType> types);

  List<WarehouseLocation> findByStatusAndIsActiveTrue(LocationStatus status);

  @Query(
      "SELECT w FROM WarehouseLocation w WHERE w.parentId IS NULL AND w.isActive = true ORDER BY w.sortOrder, w.name")
  List<WarehouseLocation> findRootLocations();

  @Query(
      "SELECT w FROM WarehouseLocation w WHERE (w.path = :pathPrefix OR w.path LIKE CONCAT(:pathPrefix, '/%')) AND w.isActive = true ORDER BY w.path, w.sortOrder")
  List<WarehouseLocation> findDescendantsByPath(@Param("pathPrefix") String pathPrefix);

  @Query(
      "SELECT w FROM WarehouseLocation w WHERE w.isActive = true "
          + "AND (LOWER(w.name) LIKE LOWER(CONCAT('%', :search, '%')) "
          + "OR LOWER(w.code) LIKE LOWER(CONCAT('%', :search, '%')) "
          + "OR LOWER(w.barcode) LIKE LOWER(CONCAT('%', :search, '%'))) "
          + "ORDER BY w.path, w.sortOrder")
  List<WarehouseLocation> search(@Param("search") String search);

  @Query(
      "SELECT w FROM WarehouseLocation w WHERE w.isActive = true "
          + "AND (:type IS NULL OR w.type = :type) "
          + "AND (:status IS NULL OR w.status = :status) "
          + "AND (:storageCondition IS NULL OR w.storageCondition = :storageCondition) "
          + "ORDER BY w.path, w.sortOrder")
  List<WarehouseLocation> findByFilters(
      @Param("type") WarehouseLocationType type,
      @Param("status") LocationStatus status,
      @Param("storageCondition")
          com.fabricmanagement.production.execution.warehouse.domain.StorageCondition
              storageCondition);

  @Query(
      "SELECT w FROM WarehouseLocation w WHERE w.isActive = true "
          + "AND w.status = :availableStatus "
          + "AND (w.maxWeightKg IS NULL OR (w.currentWeightKg + :requiredWeight) <= w.maxWeightKg) "
          + "AND (:type IS NULL OR w.type = :type) "
          + "ORDER BY w.currentWeightKg ASC")
  List<WarehouseLocation> findAvailableByCapacity(
      @Param("requiredWeight") java.math.BigDecimal requiredWeight,
      @Param("type") WarehouseLocationType type,
      @Param("availableStatus") LocationStatus availableStatus);

  @Query(
      "SELECT COUNT(w) FROM WarehouseLocation w WHERE w.parentId = :parentId AND w.isActive = true")
  long countActiveChildren(@Param("parentId") UUID parentId);

  boolean existsByTenantIdAndCode(UUID tenantId, String code);

  boolean existsByTenantIdAndBarcode(UUID tenantId, String barcode);
}
