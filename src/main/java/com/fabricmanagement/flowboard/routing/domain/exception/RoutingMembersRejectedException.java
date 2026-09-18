package com.fabricmanagement.flowboard.routing.domain.exception;

import com.fabricmanagement.flowboard.routing.domain.RoutingReason;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RoutingMembersRejectedException extends RoutingException {
  private final Map<UUID, List<RoutingReason>> reasons;

  public RoutingMembersRejectedException(Map<UUID, List<RoutingReason>> reasons) {
    super("Pool contains users who are not candidates", "ROUTING_MEMBER_NOT_CANDIDATE", 422);
    this.reasons = Map.copyOf(reasons);
  }

  public Map<UUID, List<RoutingReason>> getReasons() {
    return reasons;
  }
}
