package com.fabricmanagement.platform.tradingpartner.domain;

/**
 * Role a linked company plays for a customer (CONSIGNEE-1 C1).
 *
 * <p>The role lives on the {@link PartnerLink}, never on the partner's type: the same company may
 * be one customer's garment maker, another customer's dyehouse, and a customer of the tenant
 * itself.
 */
public enum PartnerLinkRole {

  /** Makes garments from the customer's fabric. */
  GARMENT_MAKER,

  /** Dyes or finishes goods for the customer. */
  DYEHOUSE,

  /** Prints goods for the customer. */
  PRINTER,

  /** Stores goods on the customer's behalf. */
  WAREHOUSE,

  /** Freight forwarder or carrier that receives goods for the customer. */
  FORWARDER,

  /** Any other company that receives goods for the customer. */
  OTHER
}
