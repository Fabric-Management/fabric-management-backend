package com.fabricmanagement.platform.policy.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.platform.policy.domain.Policy;
import com.fabricmanagement.platform.policy.domain.event.PolicyEvaluatedEvent;
import com.fabricmanagement.platform.policy.domain.value.PolicyDecision;
import com.fabricmanagement.platform.policy.dto.UpdatePolicyRequest;
import com.fabricmanagement.platform.policy.infra.repository.PolicyRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Policy Service - Layer 4 Policy Evaluation Engine.
 *
 * <p><b>Amazon IAM-style policy evaluation:</b>
 *
 * <ol>
 *   <li>Default DENY (whitelist approach)
 *   <li>Explicit ALLOW required
 *   <li>DENY overrides ALLOW (if both exist, DENY wins)
 *   <li>Priority-based evaluation (higher priority first)
 * </ol>
 *
 * <h2>Evaluation Flow:</h2>
 *
 * <pre>
 * 1. Find all policies for resource + action
 * 2. Evaluate in priority order (high → low)
 * 3. If ANY DENY matches → DENY (immediate return)
 * 4. If ANY ALLOW matches → ALLOW
 * 5. If NO matches → DENY (default)
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyService {

  private final PolicyRepository policyRepository;
  private final DomainEventPublisher eventPublisher;

  /**
   * Evaluate policy for given context.
   *
   * @param tenantId the tenant ID
   * @param userId the user ID
   * @param resource the resource being accessed
   * @param action the action being performed
   * @param context additional context (roles, department, etc.)
   * @return policy decision
   */
  @Cacheable(
      value = "policy-decision",
      key = "#tenantId + ':' + #userId + ':' + #resource + ':' + #action")
  @Transactional(readOnly = true)
  public PolicyDecision evaluate(
      UUID tenantId, UUID userId, String resource, String action, Map<String, Object> context) {
    long startTime = System.currentTimeMillis();

    log.debug(
        "[Policy Layer 4] Evaluating: resource={}, action={}, userId={}", resource, action, userId);

    List<Policy> policies = policyRepository.findApplicablePolicies(resource, action);

    if (policies.isEmpty()) {
      log.debug("[Policy Layer 4] No policies found - DEFAULT DENY");
      return logAndPublishDecision(
          tenantId,
          userId,
          resource,
          action,
          PolicyDecision.deny("No applicable policies found - default deny"),
          startTime);
    }

    // ✅ Performance: Single loop instead of iterating twice
    // Amazon IAM style: Check DENY first (takes priority), then ALLOW
    Policy denyMatch = null;
    Policy allowMatch = null;

    for (Policy policy : policies) {
      if (!matchesConditions(policy, context)) {
        continue; // Skip if conditions don't match
      }

      if (policy.isDeny()) {
        denyMatch = policy; // DENY takes priority, break immediately
        break;
      }

      // Store first ALLOW match (only if no DENY found yet)
      if (allowMatch == null && policy.isAllow()) {
        allowMatch = policy;
      }
    }

    // DENY takes priority over ALLOW
    if (denyMatch != null) {
      log.warn("[Policy Layer 4] DENY match: policyId={}", denyMatch.getPolicyId());
      return logAndPublishDecision(
          tenantId,
          userId,
          resource,
          action,
          PolicyDecision.deny(
              "Explicit DENY policy matched: " + denyMatch.getPolicyId(),
              denyMatch.getPolicyId(),
              List.of()),
          startTime);
    }

    // Check ALLOW
    if (allowMatch != null) {
      log.info("[Policy Layer 4] ALLOW match: policyId={}", allowMatch.getPolicyId());
      return logAndPublishDecision(
          tenantId,
          userId,
          resource,
          action,
          PolicyDecision.allow(
              "Policy matched: " + allowMatch.getPolicyId(), allowMatch.getPolicyId()),
          startTime);
    }

    // No ALLOW found - default DENY
    log.debug("[Policy Layer 4] No ALLOW match - DEFAULT DENY");
    return logAndPublishDecision(
        tenantId,
        userId,
        resource,
        action,
        PolicyDecision.deny("No matching ALLOW policy - default deny"),
        startTime);
  }

  /**
   * Check if policy conditions match the given context.
   *
   * @param policy the policy to evaluate
   * @param context the evaluation context
   * @return true if all conditions match
   */
  private boolean matchesConditions(Policy policy, Map<String, Object> context) {
    Map<String, Object> conditions = policy.getConditions();

    if (conditions == null || conditions.isEmpty()) {
      return true; // No conditions = always match
    }

    // Check roles
    if (conditions.containsKey("roles")) {
      @SuppressWarnings("unchecked")
      List<String> requiredRoles = (List<String>) conditions.get("roles");
      @SuppressWarnings("unchecked")
      List<String> userRoles = (List<String>) context.getOrDefault("roles", List.of());

      boolean hasRole = requiredRoles.stream().anyMatch(userRoles::contains);
      if (!hasRole) {
        log.debug("Role check failed: required={}, user={}", requiredRoles, userRoles);
        return false;
      }
    }

    // Check departments
    if (conditions.containsKey("departments")) {
      @SuppressWarnings("unchecked")
      List<String> requiredDepartments = (List<String>) conditions.get("departments");
      String userDepartment = (String) context.get("department");

      if (userDepartment == null || !requiredDepartments.contains(userDepartment)) {
        log.debug(
            "Department check failed: required={}, user={}", requiredDepartments, userDepartment);
        return false;
      }
    }

    // Check time range (if specified)
    if (conditions.containsKey("timeRange")) {
      String timeRange = (String) conditions.get("timeRange");
      if (!isWithinTimeRange(timeRange)) {
        log.debug(
            "Time range check failed: range={}, current={}", timeRange, java.time.LocalTime.now());
        return false;
      }
    }

    // All conditions passed
    return true;
  }

  /**
   * Check if current time is within specified range.
   *
   * @param timeRange Format: "HH:mm-HH:mm" (e.g., "08:00-18:00")
   * @return true if within range
   */
  private boolean isWithinTimeRange(String timeRange) {
    if (timeRange == null || !timeRange.contains("-")) {
      return true;
    }

    try {
      String[] parts = timeRange.split("-");
      java.time.LocalTime start = java.time.LocalTime.parse(parts[0].trim());
      java.time.LocalTime end = java.time.LocalTime.parse(parts[1].trim());
      java.time.LocalTime now = java.time.LocalTime.now();

      return !now.isBefore(start) && !now.isAfter(end);
    } catch (Exception e) {
      log.warn("Invalid time range format: {}", timeRange);
      return true;
    }
  }

  /** Log and publish policy evaluation event. */
  private PolicyDecision logAndPublishDecision(
      UUID tenantId,
      UUID userId,
      String resource,
      String action,
      PolicyDecision decision,
      long startTime) {
    long evaluationTime = System.currentTimeMillis() - startTime;

    PolicyDecision enrichedDecision =
        PolicyDecision.builder()
            .allowed(decision.isAllowed())
            .reason(decision.getReason())
            .policyId(decision.getPolicyId())
            .failedConditions(decision.getFailedConditions())
            .evaluatedAt(decision.getEvaluatedAt())
            .evaluationTimeMs(evaluationTime)
            .build();

    eventPublisher.publish(
        new PolicyEvaluatedEvent(
            tenantId,
            userId,
            resource,
            action,
            decision.isAllowed(),
            decision.getReason(),
            evaluationTime));

    log.info(
        "[Policy Layer 4] Decision: {} in {}ms - {}",
        decision.isAllowed() ? "ALLOW" : "DENY",
        evaluationTime,
        decision.getReason());

    return enrichedDecision;
  }

  /** Create a new policy. */
  @CacheEvict(value = "policy-decision", allEntries = true)
  @Transactional
  public Policy createPolicy(Policy policy) {
    log.info("Creating policy: policyId={}", policy.getPolicyId());

    if (policyRepository.existsByPolicyId(policy.getPolicyId())) {
      throw new IllegalArgumentException("Policy already exists: " + policy.getPolicyId());
    }

    Policy created = policyRepository.save(policy);

    log.info(
        "Policy created successfully: id={}, policyId={}", created.getId(), created.getPolicyId());

    return created;
  }

  /** Get all enabled policies. */
  @Transactional(readOnly = true)
  public List<Policy> getAllPolicies() {
    return policyRepository.findByEnabledTrueOrderByPriorityDesc();
  }

  /** Get policy by ID. */
  @Transactional(readOnly = true)
  public Optional<Policy> getPolicyById(UUID id) {
    log.debug("Getting policy by ID: {}", id);
    return policyRepository.findById(id);
  }

  /** Get policy by policyId (string identifier). */
  @Transactional(readOnly = true)
  public Optional<Policy> getPolicyByPolicyId(String policyId) {
    log.debug("Getting policy by policyId: {}", policyId);
    return policyRepository.findByPolicyId(policyId);
  }

  /** Update an existing policy. */
  @CacheEvict(value = "policy-decision", allEntries = true)
  @Transactional
  public Policy updatePolicy(UUID id, UpdatePolicyRequest request) {
    log.info("Updating policy: id={}", id);

    Policy policy =
        policyRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + id));

    // Update fields if provided
    if (request.getResource() != null) {
      policy.setResource(request.getResource());
    }
    if (request.getAction() != null) {
      policy.setAction(request.getAction());
    }
    if (request.getPriority() != null) {
      policy.setPriority(request.getPriority());
    }
    if (request.getEffect() != null) {
      policy.setEffect(request.getEffect());
    }
    if (request.getEnabled() != null) {
      policy.setEnabled(request.getEnabled());
    }
    if (request.getConditions() != null) {
      policy.setConditions(request.getConditions());
    }
    if (request.getDescription() != null) {
      policy.setDescription(request.getDescription());
    }

    Policy updated = policyRepository.save(policy);

    log.info(
        "Policy updated successfully: id={}, policyId={}", updated.getId(), updated.getPolicyId());

    return updated;
  }

  /** Delete a policy (soft delete by disabling). */
  @CacheEvict(value = "policy-decision", allEntries = true)
  @Transactional
  public void deletePolicy(UUID id) {
    log.info("Deleting policy: id={}", id);

    Policy policy =
        policyRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + id));

    // Soft delete: disable instead of hard delete
    policy.disable();
    policyRepository.save(policy);

    log.info(
        "Policy deleted (disabled) successfully: id={}, policyId={}", id, policy.getPolicyId());
  }

  /** Enable a policy. */
  @CacheEvict(value = "policy-decision", allEntries = true)
  @Transactional
  public Policy enablePolicy(UUID id) {
    log.info("Enabling policy: id={}", id);

    Policy policy =
        policyRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + id));

    policy.enable();
    Policy enabled = policyRepository.save(policy);

    log.info(
        "Policy enabled successfully: id={}, policyId={}", enabled.getId(), enabled.getPolicyId());

    return enabled;
  }

  /** Disable a policy. */
  @CacheEvict(value = "policy-decision", allEntries = true)
  @Transactional
  public Policy disablePolicy(UUID id) {
    log.info("Disabling policy: id={}", id);

    Policy policy =
        policyRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + id));

    policy.disable();
    Policy disabled = policyRepository.save(policy);

    log.info(
        "Policy disabled successfully: id={}, policyId={}",
        disabled.getId(),
        disabled.getPolicyId());

    return disabled;
  }
}
