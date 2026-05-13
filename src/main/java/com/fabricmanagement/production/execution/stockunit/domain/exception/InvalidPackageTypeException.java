package com.fabricmanagement.production.execution.stockunit.domain.exception;

import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;

/**
 * Thrown when a PackageType is not compatible with the given ProductType.
 *
 * <p>For example, a FIBER product cannot be packaged as a BOBBIN (BOBBIN is only for YARN).
 *
 * <p>HTTP 400 — Bad Request.
 */
public class InvalidPackageTypeException extends StockUnitDomainException {

  public InvalidPackageTypeException(PackageType packageType, ProductType productType) {
    super(
        String.format(
            "PackageType %s is not compatible with ProductType %s. Allowed package types: %s",
            packageType, productType, PackageType.allowedFor(productType)),
        "INVALID_PACKAGE_TYPE",
        400);
  }
}
