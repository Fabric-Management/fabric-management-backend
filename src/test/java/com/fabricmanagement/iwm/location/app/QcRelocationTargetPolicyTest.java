package com.fabricmanagement.iwm.location.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.iwm.location.dto.WarehouseLocationDto;
import org.junit.jupiter.api.Test;

class QcRelocationTargetPolicyTest {

  @Test
  void acceptsOnlyActiveOperationalStorageQualityAreas() {
    assertThat(QcRelocationTargetPolicy.isEligible(location(true, true, true, true))).isTrue();
    assertThat(QcRelocationTargetPolicy.isEligible(location(false, true, true, true))).isFalse();
    assertThat(QcRelocationTargetPolicy.isEligible(location(true, false, true, true))).isFalse();
    assertThat(QcRelocationTargetPolicy.isEligible(location(true, true, false, true))).isFalse();
    assertThat(QcRelocationTargetPolicy.isEligible(location(true, true, true, false))).isFalse();
  }

  private WarehouseLocationDto location(
      boolean active, boolean operational, boolean storage, boolean qualityArea) {
    return WarehouseLocationDto.builder()
        .isActive(active)
        .operational(operational)
        .storageLocation(storage)
        .qualityArea(qualityArea)
        .build();
  }
}
