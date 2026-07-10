package com.fabricmanagement.production.masterdata.color.domain.exception;

import com.fabricmanagement.production.common.exception.ProductionDomainException;

public class ColorDomainException extends ProductionDomainException {

  public ColorDomainException(String message, String errorCode, int httpStatus) {
    super(message, errorCode, httpStatus);
  }

  public static ColorDomainException duplicateCode(String code) {
    return new ColorDomainException(
        "Color already exists: " + code, "PRODUCTION_COLOR_DUPLICATE_CODE", 409);
  }

  /** A cross-field rule on the shade standard was broken. */
  public static ColorDomainException invalid(String message) {
    return new ColorDomainException(message, "PRODUCTION_COLOR_INVALID", 422);
  }

  /** An approval that stops nothing from changing is not an approval. */
  public static ColorDomainException approvedStandardIsImmutable(String code) {
    return new ColorDomainException(
        "Colour standard "
            + code
            + " is approved; revert it to draft before changing the standard-defining fields",
        "PRODUCTION_COLOR_STANDARD_APPROVED",
        409);
  }
}
