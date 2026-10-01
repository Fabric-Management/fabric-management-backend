package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Where the conversation that led to the order took place. It is context only: the terms the
 * customer accepts are recorded by the customer's own approval of the sent order version.
 */
@Schema(name = "AgreementContext", enumAsRef = true)
public enum AgreementContext {
  /** The customer visited our showroom or sales office. */
  CUSTOMER_VISITED_US,
  /** We visited the customer. */
  WE_VISITED_CUSTOMER,
  /** At a trade fair or an event. */
  TRADE_FAIR_OR_EVENT,
  /** A remote meeting: phone, video call or messages. */
  REMOTE_MEETING,
  /** Anything else; described in a note. */
  OTHER
}
