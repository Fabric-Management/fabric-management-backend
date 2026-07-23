package com.fabricmanagement.production.execution.batch.domain.port;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Port for validating warehouse locations from the IWM module.
 *
 * <p>Production never imports IWM services/entities directly. Instead, this port defines what
 * production needs (location validation for production start), and IWM provides an adapter.
 *
 * <p>This follows the Port/Adapter pattern established in the codebase (see {@code
 * ProductionOrderPort}, {@code UserTrustLevelPort}).
 */
public interface WarehouseLocationPort {

  /**
   * Validate that the given location exists and is suitable for production (MACHINE or
   * PRODUCTION_LINE type).
   *
   * @param locationId the warehouse location ID
   * @return validation result with location metadata
   * @throws com.fabricmanagement.common.infrastructure.web.exception.NotFoundException if location
   *     not found
   */
  LocationValidationResult validateProductionLocation(UUID locationId);

  /**
   * Validate that a location is an active, operational storage location designated for QC or
   * quarantine custody.
   *
   * @param locationId the warehouse location ID
   * @return minimal validation result translated from the IWM bounded context
   */
  QcLocationValidationResult validateQcLocation(UUID locationId);

  /** Lists the active operational storage locations designated for QC custody. */
  List<QualityRelocationTarget> findQualityRelocationTargets(UUID tenantId);

  /** Resolves minimal location labels in one tenant-scoped bulk read. */
  List<WarehouseLocationRef> findLocationRefs(UUID tenantId, Collection<UUID> locationIds);
}
