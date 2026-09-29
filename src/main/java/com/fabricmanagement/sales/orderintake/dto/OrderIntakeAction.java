package com.fabricmanagement.sales.orderintake.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Order-intake actions whose availability the backend decides (SOI IK-11). */
@Schema(name = "OrderIntakeAction", enumAsRef = true)
public enum OrderIntakeAction {
  EVALUATE_QUANTITY,
  RECORD_STOCK_CHOICE,
  RECORD_TONE_ACCEPTANCE,
  RECORD_AGREED_TOLERANCE,
  ADD_CUSTOM_REQUEST,
  RECORD_CUSTOMER_DECISION,
  RESOLVE_CUSTOM_REQUEST,
  RECORD_PARTIAL_DELIVERY,
  UPLOAD_ATTACHMENT,
  REQUEST_READINESS_CONFIRMATION,
  CORRECT_PRODUCT,
  REQUEST_HOLD,
  CONFIRM_ORDER
}
