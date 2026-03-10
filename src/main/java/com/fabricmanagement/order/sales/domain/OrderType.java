package com.fabricmanagement.order.sales.domain;

/** Type of sales order. */
public enum OrderType {
  /** Standard sales order */
  SALES,

  /** Purchase order (from supplier) */
  PURCHASE,

  /** Return order */
  RETURN,

  /** Sample order */
  SAMPLE,

  /** Consignment order */
  CONSIGNMENT,

  /** Work order (fason production) */
  WORK_ORDER
}
