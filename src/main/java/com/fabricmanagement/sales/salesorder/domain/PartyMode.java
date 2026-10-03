package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How a party on an order is identified (ADR-0014 D4). A role that is not decided yet has no mode;
 * absence is never read as "the customer".
 */
@Schema(name = "PartyMode", enumAsRef = true)
public enum PartyMode {
  /** The order's customer itself. */
  CUSTOMER,
  /** Another registered trading partner. */
  PARTNER,
  /** A party not registered in the system; name, contact and address are stored on the order. */
  SNAPSHOT
}
