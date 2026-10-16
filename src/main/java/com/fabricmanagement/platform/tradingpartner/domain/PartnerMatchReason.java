package com.fabricmanagement.platform.tradingpartner.domain;

/** Why an existing tenant partner is offered as a possible duplicate (CONSIGNEE-1 C4). */
public enum PartnerMatchReason {

  /** The entered tax id and country belong to the partner's registry record. */
  TAX_ID,

  /** The entered name is contained in the partner's own or official name. */
  NAME
}
