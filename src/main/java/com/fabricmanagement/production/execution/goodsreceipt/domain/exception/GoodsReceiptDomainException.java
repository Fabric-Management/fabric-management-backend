package com.fabricmanagement.production.execution.goodsreceipt.domain.exception;

import com.fabricmanagement.production.common.exception.ProductionDomainException;

/** Thrown for GoodsReceipt-specific business rule violations. */
public class GoodsReceiptDomainException extends ProductionDomainException {

  public GoodsReceiptDomainException(String message) {
    super(message, "GOODS_RECEIPT_RULE_VIOLATION", 400);
  }

  public GoodsReceiptDomainException(String message, Throwable cause) {
    super(message, "GOODS_RECEIPT_RULE_VIOLATION", 400, cause);
  }
}
