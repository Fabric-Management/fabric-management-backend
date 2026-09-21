package com.fabricmanagement.flowboard.decision.domain;

/** Durable reason why a user follows an order-cover case. */
public enum DecisionFollowSource {
  SETTLED,
  ASSIGNED,
  OPENED,
  EXPLICIT
}
