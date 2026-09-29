package com.fabricmanagement.production.core.batch.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

/**
 * Technical confirmation that the named lots may be shipped together under the stated conditions
 * (SOI A04). Given by planning, quality or another person the tenant authorises; a customer's
 * general "different lots are fine" is never recorded here. Revocation is a one-time fact.
 */
@Entity
@Table(name = "lot_compatibility_confirmation", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LotCompatibilityConfirmation extends BaseEntity {

  @Type(JsonType.class)
  @Column(name = "batch_ids", nullable = false, updatable = false, columnDefinition = "jsonb")
  private List<UUID> batchIds = new ArrayList<>();

  /** When set, the confirmation applies only to this customer's orders. */
  @Column(name = "customer_id", updatable = false)
  private UUID customerId;

  @Column(name = "conditions", nullable = false, updatable = false, columnDefinition = "TEXT")
  private String conditions;

  @Column(name = "confirmed_by", nullable = false, updatable = false)
  private UUID confirmedBy;

  @Column(name = "confirmed_at", nullable = false, updatable = false)
  private Instant confirmedAt;

  @Column(name = "revoked_by")
  private UUID revokedBy;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  public static LotCompatibilityConfirmation confirm(
      Collection<UUID> batchIds,
      UUID customerId,
      String conditions,
      UUID confirmedBy,
      Instant confirmedAt) {
    Set<UUID> distinct = batchIds == null ? Set.of() : Set.copyOf(batchIds);
    if (distinct.size() < 2) {
      throw new IllegalArgumentException("A compatibility confirmation names at least two lots");
    }
    if (conditions == null || conditions.isBlank()) {
      throw new IllegalArgumentException("The conditions of the confirmation are required");
    }
    if (confirmedBy == null || confirmedAt == null) {
      throw new IllegalArgumentException("Confirmer and time are required");
    }
    LotCompatibilityConfirmation confirmation = new LotCompatibilityConfirmation();
    confirmation.batchIds = distinct.stream().sorted().toList();
    confirmation.customerId = customerId;
    confirmation.conditions = conditions.trim();
    confirmation.confirmedBy = confirmedBy;
    confirmation.confirmedAt = confirmedAt;
    return confirmation;
  }

  public void revoke(UUID actor, Instant at) {
    if (revokedAt != null) {
      throw new IllegalStateException("Compatibility confirmation is already revoked");
    }
    if (actor == null || at == null) {
      throw new IllegalArgumentException("Actor and time are required");
    }
    this.revokedBy = actor;
    this.revokedAt = at;
  }

  public boolean isEffective() {
    return revokedAt == null && Boolean.TRUE.equals(getIsActive());
  }

  /** True when every lot of the set is covered by this confirmation and the customer matches. */
  public boolean covers(Collection<UUID> lots, UUID orderCustomerId) {
    if (!isEffective()) {
      return false;
    }
    if (customerId != null && !customerId.equals(orderCustomerId)) {
      return false;
    }
    return batchIds.containsAll(lots);
  }

  @Override
  protected String getModuleCode() {
    return "LCC";
  }
}
