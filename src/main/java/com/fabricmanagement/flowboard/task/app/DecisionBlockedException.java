package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.flowboard.common.exception.FlowBoardDomainException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed 403 for a readable subject whose write gate refused the actor (FE-ARCH-5b-3 §3.4). */
public class DecisionBlockedException extends FlowBoardDomainException {

  /**
   * @param code a {@code DecisionBlockedReason} code of the 5a contract
   * @param requiredPermission the missing grant when the reason identifies one, otherwise null
   * @param currentTaskVersion the task version the gate evaluated, otherwise null
   */
  public DecisionBlockedException(String code, String requiredPermission, Long currentTaskVersion) {
    super("Decision action is not currently allowed", "DECISION_BLOCKED", 403);
    // Both parameters are required-nullable in the contract, so they are always present as keys.
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("requiredPermission", requiredPermission);
    parameters.put("currentTaskVersion", currentTaskVersion);
    Map<String, Object> reason = new LinkedHashMap<>();
    reason.put("code", code);
    reason.put("messageKey", "decision.blocked." + code.toLowerCase(java.util.Locale.ROOT));
    reason.put("parameters", parameters);
    withDetail("reason", reason);
  }

  public DecisionBlockedException(String code) {
    this(code, null, null);
  }
}
