package com.fabricmanagement.sales.orderintake.domain;

/**
 * How an acceptance reached the salesperson. The record means "I received this from the customer",
 * never "I accepted on the customer's behalf" (SOI A11). Portal replies arrive in a later slice.
 */
@io.swagger.v3.oas.annotations.media.Schema(name = "IntakeAcceptanceChannel", enumAsRef = true)
public enum AcceptanceChannel {
  PHONE,
  EMAIL,
  MESSAGE,
  IN_PERSON
}
