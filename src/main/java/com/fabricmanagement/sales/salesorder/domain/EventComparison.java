package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How the event the customer meant compares with the delivery term's event (ADR-0014 D7). Not
 * knowing what the customer meant, or having no term yet, is {@link #UNKNOWN} — never "different".
 */
@Schema(name = "EventComparison", enumAsRef = true)
public enum EventComparison {
  SAME,
  DIFFERENT,
  UNKNOWN
}
