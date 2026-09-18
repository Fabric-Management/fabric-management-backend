package com.fabricmanagement.flowboard.routing.domain;

public enum RoutingReason {
  INACTIVE,
  MISSING_FLOWBOARD_WRITE,
  MISSING_SALES_WRITE,
  OUTSIDE_SALES_WRITE_SCOPE,
  NOT_A_MEMBER
}
