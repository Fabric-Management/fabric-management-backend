package com.fabricmanagement.inventory.location.app;

import com.fabricmanagement.inventory.location.dto.WarehouseLocationDto;

/** Single IWM policy used by both QC target discovery and relocation validation. */
public final class QcRelocationTargetPolicy {

  private QcRelocationTargetPolicy() {}

  public static boolean isEligible(WarehouseLocationDto location) {
    return location.isActive()
        && location.isOperational()
        && location.isStorageLocation()
        && location.isQualityArea();
  }
}
