package com.fabricmanagement.platform.tradingpartner.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Whether a customer's cheque drawer is a company or a person (PAYMENT-PLAN-7 R13). */
@Schema(name = "TradingPartnerChequeDrawerType", enumAsRef = true)
public enum ChequeDrawerType {
  COMPANY,
  INDIVIDUAL
}
