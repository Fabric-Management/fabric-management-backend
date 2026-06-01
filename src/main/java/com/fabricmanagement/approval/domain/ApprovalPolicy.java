package com.fabricmanagement.approval.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tenant bazlı Onay Politikası kural tanımı. Hangi tür işlem, hangi güven düzeyindeki birinden
 * çıkarsa kimin onayına düşecek?
 */
@Entity
@Table(schema = "common_approval", name = "approval_policy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalPolicy extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 50)
  private ApprovalEntityType entityType;

  // ALL, PROBATION, STANDARD
  @Enumerated(EnumType.STRING)
  @Column(name = "required_for_level", nullable = false, length = 32)
  private PolicyTargetLevel requiredForLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "approver_role", nullable = false, length = 50)
  private ApproverRole approverRole;

  @Column(name = "promotion_threshold", nullable = false)
  private int promotionThreshold = 10;

  @Column(name = "min_amount_threshold", precision = 18, scale = 3)
  private BigDecimal minAmountThreshold;

  @Column(name = "max_amount_threshold", precision = 18, scale = 3)
  private BigDecimal maxAmountThreshold;

  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  @Column(name = "expiry_hours", nullable = false)
  private int expiryHours = 48;

  private static final int MIN_EXPIRY = 1;
  private static final int MAX_EXPIRY = 720;

  private void validateExpiryHours(int hours) {
    if (hours < MIN_EXPIRY || hours > MAX_EXPIRY) {
      throw new IllegalArgumentException(
          "expiryHours must be between " + MIN_EXPIRY + " and " + MAX_EXPIRY);
    }
  }

  /**
   * Bu politika verilen tutar için geçerli mi? Tutar eşiği tanımlı değilse (null) → her zaman
   * geçerli (legacy davranış).
   */
  public boolean matchesAmount(BigDecimal amount, String amountCurrency) {
    if (minAmountThreshold == null) {
      return true; // tutar bazlı olmayan politika — her zaman eşleşir
    }
    if (amount == null || !currency.equals(amountCurrency)) {
      return false;
    }
    return amount.compareTo(minAmountThreshold) >= 0
        && (maxAmountThreshold == null || amount.compareTo(maxAmountThreshold) <= 0);
  }

  public ApprovalPolicy(
      UUID tenantId,
      ApprovalEntityType entityType,
      PolicyTargetLevel requiredForLevel,
      ApproverRole approverRole,
      int promotionThreshold,
      int expiryHours) {
    if (entityType == null) {
      throw new IllegalArgumentException("entityType cannot be null");
    }
    if (approverRole == null) {
      throw new IllegalArgumentException("approverRole cannot be null");
    }
    if (promotionThreshold < 1) {
      throw new IllegalArgumentException("promotionThreshold must be >= 1");
    }
    validateExpiryHours(expiryHours);
    this.setTenantId(tenantId);
    this.entityType = entityType;
    this.requiredForLevel = requiredForLevel;
    this.approverRole = approverRole;
    this.promotionThreshold = promotionThreshold;
    this.expiryHours = expiryHours;
  }

  @Override
  protected String getModuleCode() {
    return "APOL"; // Approval POLicy
  }

  public void update(
      PolicyTargetLevel requiredForLevel,
      ApproverRole approverRole,
      int promotionThreshold,
      int expiryHours) {
    validateExpiryHours(expiryHours);
    this.requiredForLevel = requiredForLevel;
    this.approverRole = approverRole;
    this.promotionThreshold = promotionThreshold;
    this.expiryHours = expiryHours;
  }

  public void toggleActive(boolean active) {
    this.isActive = active;
  }
}
