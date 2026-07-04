package com.fabricmanagement.sales.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import org.springframework.http.HttpStatus;

public class SalesDomainException extends DomainException {

  public SalesDomainException(String message, String errorCode, HttpStatus httpStatus) {
    super(message, errorCode, httpStatus.value());
  }

  public SalesDomainException(
      String message, String errorCode, HttpStatus httpStatus, Object[] args) {
    super(message, errorCode, httpStatus.value(), args);
  }

  public SalesDomainException(
      String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
    super(message, errorCode, httpStatus.value(), cause);
  }

  public static SalesDomainException invalidPriceZone(String message) {
    return new SalesDomainException(message, "SALES_001_INVALID_PRICE_ZONE", HttpStatus.FORBIDDEN);
  }

  public static SalesDomainException quoteNotFound(String quoteId) {
    return new SalesDomainException(
        "Quote not found: " + quoteId, "SALES_002_QUOTE_NOT_FOUND", HttpStatus.NOT_FOUND);
  }

  public static SalesDomainException invalidQuoteStatus(String message) {
    return new SalesDomainException(
        message, "SALES_003_INVALID_QUOTE_STATUS", HttpStatus.BAD_REQUEST);
  }

  public static SalesDomainException tokenExpiredOrUsed(String message) {
    return new SalesDomainException(message, "SALES_004_TOKEN_INVALID", HttpStatus.BAD_REQUEST);
  }

  public static SalesDomainException approvalTokenNotFound(String message) {
    return new SalesDomainException(
        message, "SALES_007_APPROVAL_TOKEN_NOT_FOUND", HttpStatus.NOT_FOUND);
  }

  public static SalesDomainException approvalTokenNoLongerValid(String message) {
    return new SalesDomainException(
        message, "SALES_008_APPROVAL_TOKEN_NO_LONGER_VALID", HttpStatus.GONE);
  }

  public static SalesDomainException exchangeRateRequired(String message, Throwable cause) {
    return new SalesDomainException(
        message, "SALES_005_EXCHANGE_RATE_REQUIRED", HttpStatus.BAD_REQUEST, cause);
  }

  public static SalesDomainException needsInternalApproval(String message) {
    return new SalesDomainException(
        message, "SALES_006_QUOTE_NEEDS_INTERNAL_APPROVAL", HttpStatus.CONFLICT);
  }
}
