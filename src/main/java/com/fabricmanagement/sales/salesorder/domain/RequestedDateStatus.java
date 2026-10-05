package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Whether the customer asked for a date. "Not requested" is a value, not a missing date (ADR-0013
 * A1): without a status nothing is known yet.
 */
@Schema(name = "RequestedDateStatus", enumAsRef = true)
public enum RequestedDateStatus {
  REQUESTED,
  NOT_REQUESTED
}
