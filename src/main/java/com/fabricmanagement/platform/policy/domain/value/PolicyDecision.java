package com.fabricmanagement.platform.policy.domain.value;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Policy evaluation decision - Result of policy check.
 *
 * <p>Contains:
 *
 * <ul>
 *   <li>Decision (allowed/denied)
 *   <li>Reason for decision
 *   <li>Failed conditions (if denied)
 *   <li>Policy ID that made decision
 *   <li>Evaluation timestamp & duration
 * </ul>
 *
 * <h2>Usage:</h2>
 *
 * <pre>{@code
 * PolicyDecision decision = policyEngine.evaluate(context);
 * if (!decision.isAllowed()) {
 *     throw new AccessDeniedException(decision.getReason());
 * }
 * }</pre>
 */
@Data
@Builder
public class PolicyDecision {

  private boolean allowed;
  private String reason;
  private List<String> failedConditions;
  private String policyId;
  private Instant evaluatedAt;
  private Long evaluationTimeMs;

  public static PolicyDecision allow(String reason) {
    return PolicyDecision.builder()
        .allowed(true)
        .reason(reason)
        .failedConditions(Collections.emptyList())
        .evaluatedAt(Instant.now())
        .build();
  }

  public static PolicyDecision allow(String reason, String policyId) {
    return PolicyDecision.builder()
        .allowed(true)
        .reason(reason)
        .policyId(policyId)
        .failedConditions(Collections.emptyList())
        .evaluatedAt(Instant.now())
        .build();
  }

  public static PolicyDecision deny(String reason) {
    return PolicyDecision.builder()
        .allowed(false)
        .reason(reason)
        .failedConditions(Collections.emptyList())
        .evaluatedAt(Instant.now())
        .build();
  }

  public static PolicyDecision deny(String reason, List<String> failedConditions) {
    return PolicyDecision.builder()
        .allowed(false)
        .reason(reason)
        .failedConditions(failedConditions != null ? failedConditions : Collections.emptyList())
        .evaluatedAt(Instant.now())
        .build();
  }

  public static PolicyDecision deny(String reason, String policyId, List<String> failedConditions) {
    return PolicyDecision.builder()
        .allowed(false)
        .reason(reason)
        .policyId(policyId)
        .failedConditions(failedConditions != null ? failedConditions : Collections.emptyList())
        .evaluatedAt(Instant.now())
        .build();
  }
}
