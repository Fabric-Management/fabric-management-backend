package com.fabricmanagement.iwm.location.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.iwm.location.domain.LocationStatus;
import com.fabricmanagement.iwm.location.domain.WarehouseLocation;
import com.fabricmanagement.iwm.location.domain.WarehouseLocationType;
import com.fabricmanagement.iwm.location.infra.repository.WarehouseLocationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseLocationServiceQcRelocationTest {

  @Mock private WarehouseLocationRepository repository;
  @InjectMocks private WarehouseLocationService service;

  @Test
  void returnsOnlyEligibleTargetsInRepositoryPathOrderForTheTenant() {
    UUID tenantId = UUID.randomUUID();
    WarehouseLocation alpha = location("QC-A", true);
    WarehouseLocation blocked = location("QC-BLOCKED", true);
    blocked.changeStatus(LocationStatus.BLOCKED);
    WarehouseLocation ordinary = location("STORAGE", false);
    WarehouseLocation beta = location("QC-B", true);
    when(repository.findByTenantIdAndQualityAreaTrueOrderByPathAscCodeAsc(tenantId))
        .thenReturn(List.of(alpha, blocked, ordinary, beta));

    assertThat(service.findQcRelocationTargets(tenantId))
        .extracting(location -> location.getCode())
        .containsExactly("QC-A", "QC-B");
    verify(repository).findByTenantIdAndQualityAreaTrueOrderByPathAscCodeAsc(tenantId);
  }

  private WarehouseLocation location(String code, boolean qualityArea) {
    WarehouseLocation location =
        new WarehouseLocation(
            null,
            code,
            code,
            null,
            WarehouseLocationType.BIN,
            null,
            null,
            null,
            null,
            null,
            0,
            null,
            qualityArea);
    location.assignPath("MAIN/" + code, 1);
    return location;
  }
}
