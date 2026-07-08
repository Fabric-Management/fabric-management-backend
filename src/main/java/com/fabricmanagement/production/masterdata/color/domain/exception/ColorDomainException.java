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
}
