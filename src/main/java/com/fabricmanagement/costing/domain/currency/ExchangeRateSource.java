package com.fabricmanagement.costing.domain.currency;

/** Source of an exchange rate snapshot. */
public enum ExchangeRateSource {
  /** TCMB (Türkiye Cumhuriyet Merkez Bankası) — Turkish Central Bank official rate. */
  TCMB,

  /** ECB (European Central Bank) official rate. */
  ECB,

  /** Rate entered manually by a finance user (e.g. negotiated rate, forward contract). */
  MANUAL
}
