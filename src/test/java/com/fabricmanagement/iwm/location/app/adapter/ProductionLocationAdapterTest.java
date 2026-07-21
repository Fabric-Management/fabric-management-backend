package com.fabricmanagement.iwm.location.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.iwm.location.app.WarehouseLocationService;
import com.fabricmanagement.iwm.location.domain.WarehouseLocationType;
import com.fabricmanagement.iwm.location.dto.WarehouseLocationDto;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductionLocationAdapterTest {

  @Mock private WarehouseLocationService warehouseLocationService;
  @InjectMocks private ProductionLocationAdapter adapter;

  @Test
  void validatesActiveOperationalStorageQualityAreaAsQcTarget() {
    UUID locationId = UUID.randomUUID();
    when(warehouseLocationService.getById(locationId))
        .thenReturn(location(locationId, true, true, true, true));

    var result = adapter.validateQcLocation(locationId);

    assertThat(result.locationId()).isEqualTo(locationId);
    assertThat(result.locationCode()).isEqualTo("QC-HOLD");
    assertThat(result.validQcLocation()).isTrue();
  }

  @Test
  void rejectsQualityAreaThatIsNotStorageCapable() {
    UUID locationId = UUID.randomUUID();
    when(warehouseLocationService.getById(locationId))
        .thenReturn(location(locationId, true, true, false, true));

    assertThat(adapter.validateQcLocation(locationId).validQcLocation()).isFalse();
  }

  @Test
  void rejectsStorageLocationWithoutQualityDesignation() {
    UUID locationId = UUID.randomUUID();
    when(warehouseLocationService.getById(locationId))
        .thenReturn(location(locationId, true, true, true, false));

    assertThat(adapter.validateQcLocation(locationId).validQcLocation()).isFalse();
  }

  @Test
  void rejectsInactiveOrNonOperationalQualityAreas() {
    UUID inactiveId = UUID.randomUUID();
    UUID blockedId = UUID.randomUUID();
    when(warehouseLocationService.getById(inactiveId))
        .thenReturn(location(inactiveId, false, false, true, true));
    when(warehouseLocationService.getById(blockedId))
        .thenReturn(location(blockedId, true, false, true, true));

    assertThat(adapter.validateQcLocation(inactiveId).validQcLocation()).isFalse();
    assertThat(adapter.validateQcLocation(blockedId).validQcLocation()).isFalse();
  }

  private WarehouseLocationDto location(
      UUID id, boolean active, boolean operational, boolean storage, boolean qualityArea) {
    return WarehouseLocationDto.builder()
        .id(id)
        .code("QC-HOLD")
        .type(WarehouseLocationType.WAREHOUSE)
        .isActive(active)
        .operational(operational)
        .storageLocation(storage)
        .qualityArea(qualityArea)
        .build();
  }
}
