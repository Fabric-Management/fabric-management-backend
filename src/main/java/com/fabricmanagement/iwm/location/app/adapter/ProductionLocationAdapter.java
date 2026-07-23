package com.fabricmanagement.iwm.location.app.adapter;

import com.fabricmanagement.iwm.location.app.QcRelocationTargetPolicy;
import com.fabricmanagement.iwm.location.app.WarehouseLocationService;
import com.fabricmanagement.iwm.location.domain.WarehouseLocationType;
import com.fabricmanagement.iwm.location.dto.WarehouseLocationDto;
import com.fabricmanagement.production.execution.batch.domain.port.LocationValidationResult;
import com.fabricmanagement.production.execution.batch.domain.port.QcLocationValidationResult;
import com.fabricmanagement.production.execution.batch.domain.port.QualityRelocationTarget;
import com.fabricmanagement.production.execution.batch.domain.port.WarehouseLocationPort;
import com.fabricmanagement.production.execution.batch.domain.port.WarehouseLocationRef;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapter that implements production's {@link WarehouseLocationPort} using IWM's {@link
 * WarehouseLocationService}.
 *
 * <p>This is the Anti-Corruption Layer between IWM and Production. IWM types (enums, DTOs) are
 * translated into production's port contract ({@link LocationValidationResult}) here. Production
 * never sees IWM internals.
 */
@Component
@RequiredArgsConstructor
public class ProductionLocationAdapter implements WarehouseLocationPort {

  private final WarehouseLocationService warehouseLocationService;

  @Override
  public LocationValidationResult validateProductionLocation(UUID locationId) {
    WarehouseLocationDto location = warehouseLocationService.getById(locationId);

    boolean isProductionLocation =
        location.getType() == WarehouseLocationType.MACHINE
            || location.getType() == WarehouseLocationType.PRODUCTION_LINE;

    return new LocationValidationResult(locationId, location.getCode(), isProductionLocation);
  }

  @Override
  public QcLocationValidationResult validateQcLocation(UUID locationId) {
    WarehouseLocationDto location = warehouseLocationService.getById(locationId);
    boolean validQcLocation = QcRelocationTargetPolicy.isEligible(location);
    return new QcLocationValidationResult(locationId, location.getCode(), validQcLocation);
  }

  @Override
  public List<QualityRelocationTarget> findQualityRelocationTargets(UUID tenantId) {
    return warehouseLocationService.findQcRelocationTargets(tenantId).stream()
        .map(
            location ->
                new QualityRelocationTarget(
                    location.getId(), location.getCode(), location.getName(), location.getPath()))
        .toList();
  }

  @Override
  public List<WarehouseLocationRef> findLocationRefs(UUID tenantId, Collection<UUID> locationIds) {
    return warehouseLocationService.findByIds(tenantId, locationIds).stream()
        .map(
            location ->
                new WarehouseLocationRef(location.getId(), location.getCode(), location.getName()))
        .toList();
  }
}
