package com.fabricmanagement.iwm.location.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.iwm.common.exception.IwmDomainException;
import org.junit.jupiter.api.Test;

class WarehouseLocationTest {

  @Test
  void storageLocationCanBeDesignatedAsQualityArea() {
    WarehouseLocation location = location(WarehouseLocationType.WAREHOUSE, true);

    assertThat(location.isQualityArea()).isTrue();
    assertThat(location.isStorageLocation()).isTrue();
  }

  @Test
  void nonStorageLocationCannotBeDesignatedAsQualityArea() {
    assertThatThrownBy(() -> location(WarehouseLocationType.MACHINE, true))
        .isInstanceOf(IwmDomainException.class)
        .hasMessageContaining("storage-capable");
  }

  private WarehouseLocation location(WarehouseLocationType type, boolean qualityArea) {
    return new WarehouseLocation(
        null,
        "QC-AREA",
        "QC Area",
        null,
        type,
        StorageCondition.STANDARD,
        null,
        null,
        null,
        null,
        0,
        null,
        qualityArea);
  }
}
