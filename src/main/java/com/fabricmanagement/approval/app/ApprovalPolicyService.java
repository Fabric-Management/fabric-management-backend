package com.fabricmanagement.approval.app;

import com.fabricmanagement.approval.domain.ApprovalEntityType;
import com.fabricmanagement.approval.domain.ApprovalPolicy;
import com.fabricmanagement.approval.domain.ApproverRole;
import com.fabricmanagement.approval.domain.PolicyTargetLevel;
import com.fabricmanagement.approval.infra.repository.ApprovalPolicyRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant bazlı Onay Politikalarının (Approval Policy) yönetildiği merkez servis. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalPolicyService {

  private final ApprovalPolicyRepository policyRepo;
  private final com.fabricmanagement.costing.app.exchange.ExchangeRateService exchangeRates;
  private final java.time.Clock clock;

  /** Bir tenant'a ait aktif/pasif tüm politikaları listeler. */
  @Transactional(readOnly = true)
  public List<ApprovalPolicy> getAllPolicies(UUID tenantId) {
    return policyRepo.findByTenantIdAndDeletedAtIsNull(tenantId);
  }

  /**
   * Bir tenant'ta belli bir işlem (Örn: WORK_ORDER) için tanımlı olan **aktif** kuralı getirir
   * (tutar olmayanlar için geriye dönük uyumluluk).
   */
  @Transactional(readOnly = true)
  public Optional<ApprovalPolicy> getActivePolicyFor(UUID tenantId, ApprovalEntityType entityType) {
    return getActivePolicyFor(tenantId, entityType, null, null);
  }

  /** Bir tenant'ta belli bir işlem (Örn: WORK_ORDER) için tanımlı olan **aktif** kuralı getirir. */
  @Transactional(readOnly = true)
  public Optional<ApprovalPolicy> getActivePolicyFor(
      UUID tenantId, ApprovalEntityType entityType, BigDecimal amount, String currency) {
    return policyRepo.findActivePoliciesForEntity(tenantId, entityType).stream()
        .filter(p -> p.matchesAmount(amount, currency))
        .findFirst();
  }

  /**
   * Picks the active policy for an entity that amounts to several currencies. A threshold is
   * compared with the sum of all amounts converted into the policy's currency, so a TRY threshold
   * also governs a USD or mixed order. If a rate is missing the policy is treated as applying: an
   * approval control that cannot prove the amount is below its threshold must not let it pass.
   */
  @Transactional(readOnly = true)
  public Optional<ApprovalPolicy> getActivePolicyFor(
      UUID tenantId,
      ApprovalEntityType entityType,
      List<com.fabricmanagement.common.util.Money> amounts) {
    java.time.LocalDate today = java.time.LocalDate.now(clock);
    return policyRepo.findActivePoliciesForEntity(tenantId, entityType).stream()
        .filter(policy -> appliesTo(tenantId, policy, amounts, today))
        .findFirst();
  }

  private boolean appliesTo(
      UUID tenantId,
      ApprovalPolicy policy,
      List<com.fabricmanagement.common.util.Money> amounts,
      java.time.LocalDate today) {
    if (policy.getMinAmountThreshold() == null || amounts.isEmpty()) {
      return policy.matchesAmount(null, null);
    }
    BigDecimal total = BigDecimal.ZERO;
    for (com.fabricmanagement.common.util.Money amount : amounts) {
      try {
        total =
            total.add(
                exchangeRates
                    .convert(
                        tenantId,
                        amount.getAmount(),
                        amount.getCurrency().getCurrencyCode(),
                        policy.getCurrency(),
                        today)
                    .getConvertedAmount());
      } catch (com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException ex) {
        log.warn(
            "No {} -> {} rate for approval policy {}; treating it as applying",
            amount.getCurrency().getCurrencyCode(),
            policy.getCurrency(),
            policy.getId());
        return true;
      }
    }
    return policy.matchesAmount(total, policy.getCurrency());
  }

  /**
   * Yeni bir kural seti (Policy) yaratır. Aynı tenant+entity+level için sadece tek kural
   * olabileceği migration seviyesindeki `unique index` ile de korunmaktadır.
   */
  @Transactional
  public ApprovalPolicy createPolicy(
      UUID tenantId,
      ApprovalEntityType entityType,
      PolicyTargetLevel requiredLevel,
      ApproverRole approverRole,
      int promotionThreshold,
      int expiryHours) {

    // Varsayılan kural kontrolü vs bu araya eklenebilir
    ApprovalPolicy policy =
        new ApprovalPolicy(
            tenantId, entityType, requiredLevel, approverRole, promotionThreshold, expiryHours);

    return policyRepo.save(policy);
  }

  /** Var olan bir policy'yi günceller. */
  @Transactional
  public ApprovalPolicy updatePolicy(
      UUID tenantId,
      UUID policyId,
      PolicyTargetLevel requiredLevel,
      ApproverRole approverRole,
      int promotionThreshold,
      int expiryHours) {

    ApprovalPolicy policy =
        policyRepo
            .findById(policyId)
            .filter(p -> p.getTenantId().equals(tenantId))
            .orElseThrow(() -> new IllegalArgumentException("Policy not found"));

    policy.update(requiredLevel, approverRole, promotionThreshold, expiryHours);
    return policyRepo.save(policy);
  }

  /** Policy'i askıya alır veya açar. */
  @Transactional
  public ApprovalPolicy togglePolicy(UUID tenantId, UUID policyId, boolean active) {
    ApprovalPolicy policy =
        policyRepo
            .findById(policyId)
            .filter(p -> p.getTenantId().equals(tenantId))
            .orElseThrow(() -> new IllegalArgumentException("Policy not found"));

    policy.toggleActive(active);
    return policyRepo.save(policy);
  }
}
