package com.fabricmanagement.costing.domain.currency;

/** Source of an exchange rate snapshot. */
public enum ExchangeRateSource {
  /** TCMB (Central Bank of the Republic of Turkey) — official rate. */
  TCMB,

  /** ECB (European Central Bank) official rate. */
  ECB,

  /** Rate entered manually by a finance user (e.g. negotiated rate, forward contract). */
  MANUAL
}
