package com.fabricmanagement.platform.tradingpartner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What adding a drawer did. A name that already exists is never added twice: an active row is
 * returned for use, an inactive one is returned unchanged so the person can choose to reactivate it
 * (PAYMENT-PLAN-7 R15).
 */
@Schema(name = "TradingPartnerChequeDrawerAddResultDto")
public record TradingPartnerChequeDrawerAddResultDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Outcome outcome,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) TradingPartnerChequeDrawerDto drawer) {

  @Schema(name = "TradingPartnerChequeDrawerAddOutcome", enumAsRef = true)
  public enum Outcome {
    /** A new drawer was recorded. */
    CREATED,
    /** An active drawer with this name already existed and is returned for use. */
    EXISTING,
    /** A deactivated drawer has this name; it was not reactivated and must not be selected. */
    INACTIVE_DUPLICATE
  }
}
