package com.fabricmanagement.production.execution.stockunit.domain.exception;

import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import java.util.UUID;

/** Coded validation errors for the privileged QC custody relocation path. */
public class QcRelocationException extends StockUnitDomainException {

  private QcRelocationException(String message, String errorCode) {
    super(message, errorCode, 422);
  }

  public static QcRelocationException releasedUnit(String barcode) {
    return new QcRelocationException(
        "Released StockUnit " + barcode + " must use the standard transfer path",
        "QC_RELOCATE_RELEASED_UNIT");
  }

  public static QcRelocationException sourceInvalid(
      String barcode, StockUnitStatus status, QualityDisposition disposition) {
    return new QcRelocationException(
        "StockUnit "
            + barcode
            + " cannot be relocated for QC in status "
            + status
            + " (disposition="
            + disposition
            + ")",
        "QC_RELOCATE_STATUS_INVALID");
  }

  public static QcRelocationException targetInvalid(UUID targetLocationId) {
    return new QcRelocationException(
        "Location " + targetLocationId + " is not an approved operational QC storage area",
        "QC_RELOCATE_TARGET_INVALID");
  }

  public static QcRelocationException sameLocation(UUID targetLocationId) {
    return new QcRelocationException(
        "StockUnit is already located at QC location " + targetLocationId,
        "QC_RELOCATE_SAME_LOCATION");
  }

  public static QcRelocationException reasonRequired() {
    return new QcRelocationException(
        "QC relocation reason must not be blank", "QC_RELOCATE_REASON_REQUIRED");
  }
}
