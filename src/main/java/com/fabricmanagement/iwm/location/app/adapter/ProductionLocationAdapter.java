package com.fabricmanagement.iwm.location.app.adapter;

import com.fabricmanagement.iwm.location.app.WarehouseLocationService;
import com.fabricmanagement.iwm.location.domain.WarehouseLocationType;
import com.fabricmanagement.iwm.location.dto.WarehouseLocationDto;
import com.fabricmanagement.production.execution.batch.domain.port.LocationValidationResult;
import com.fabricmanagement.production.execution.batch.domain.port.QcLocationValidationResult;
import com.fabricmanagement.production.execution.batch.domain.port.WarehouseLocationPort;
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
    boolean validQcLocation =
        location.isActive()
            && location.isOperational()
            && location.isStorageLocation()
            && location.isQualityArea();
    return new QcLocationValidationResult(locationId, location.getCode(), validQcLocation);
  }
}
