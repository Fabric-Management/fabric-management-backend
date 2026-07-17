package com.fabricmanagement.production.masterdata.color.domain.exception;

import com.fabricmanagement.production.common.exception.ProductionDomainException;

public class ColorPartnerRefDomainException extends ProductionDomainException {

  private ColorPartnerRefDomainException(String message, String errorCode, int httpStatus) {
    super(message, errorCode, httpStatus);
  }

  public static ColorPartnerRefDomainException conflict(String message) {
    return new ColorPartnerRefDomainException(
        message, "PRODUCTION_COLOR_PARTNER_REF_CONFLICT", 409);
  }

  public static ColorPartnerRefDomainException invalid(String message) {
    return new ColorPartnerRefDomainException(message, "PRODUCTION_COLOR_PARTNER_REF_INVALID", 422);
  }

  public static ColorPartnerRefDomainException duplicateCode(String externalCode) {
    return new ColorPartnerRefDomainException(
        "An active partner color code already exists: " + externalCode,
        "PRODUCTION_COLOR_PARTNER_CODE_DUPLICATE",
        409);
  }

  public static ColorPartnerRefDomainException unavailablePartner() {
    return new ColorPartnerRefDomainException(
        "The trading partner is missing, inactive, or incompatible with the requested role",
        "PRODUCTION_COLOR_PARTNER_UNAVAILABLE",
        409);
  }

  public static ColorPartnerRefDomainException codeNotFound(String codeId) {
    return new ColorPartnerRefDomainException(
        "Partner color code not found: " + codeId, "PRODUCTION_COLOR_PARTNER_CODE_NOT_FOUND", 404);
  }
}
