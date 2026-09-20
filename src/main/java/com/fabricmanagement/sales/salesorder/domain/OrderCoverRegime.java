package com.fabricmanagement.sales.salesorder.domain;

/** Persistent cutover decision made once, at the first sales-order confirmation. */
public enum OrderCoverRegime {
  LEGACY,
  GOVERNED
}
