package com.fabricmanagement.production.execution.stockunit.domain.exception;

import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;

/**
 * Thrown when a PackageType is not compatible with the given MaterialType.
 *
 * <p>For example, a FIBER material cannot be packaged as a BOBBIN (BOBBIN is only for YARN).
 *
 * <p>HTTP 400 — Bad Request.
 */
public class InvalidPackageTypeException extends StockUnitDomainException {

  public InvalidPackageTypeException(PackageType packageType, MaterialType materialType) {
    super(
        String.format(
            "PackageType %s is not compatible with MaterialType %s. Allowed package types: %s",
            packageType, materialType, PackageType.allowedFor(materialType)),
        "INVALID_PACKAGE_TYPE",
        400);
  }
}
