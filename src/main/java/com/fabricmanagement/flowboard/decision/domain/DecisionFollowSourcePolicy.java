package com.fabricmanagement.flowboard.decision.domain;

import java.util.UUID;

/** Pure source eligibility rules from ADR-0003 D52. */
public final class DecisionFollowSourcePolicy {
  private DecisionFollowSourcePolicy() {}

  public static boolean acceptsSettlement(String actorKind, UUID actorId, UUID systemUserId) {
    return "USER".equals(actorKind) && acceptsUser(actorId, systemUserId);
  }

  public static boolean acceptsAssignment(UUID assignedUserId, UUID systemUserId) {
    return acceptsUser(assignedUserId, systemUserId);
  }

  public static boolean acceptsOpened(UUID createdBy, boolean activeTenantUser, UUID systemUserId) {
    return activeTenantUser && acceptsUser(createdBy, systemUserId);
  }

  private static boolean acceptsUser(UUID userId, UUID systemUserId) {
    return userId != null && !userId.equals(systemUserId);
  }
}
