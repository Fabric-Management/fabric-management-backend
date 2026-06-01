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
