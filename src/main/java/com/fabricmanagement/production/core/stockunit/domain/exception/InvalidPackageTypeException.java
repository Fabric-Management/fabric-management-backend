package com.fabricmanagement.production.core.stockunit.domain.exception;

import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.production.core.stockunit.domain.PackageType;

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
