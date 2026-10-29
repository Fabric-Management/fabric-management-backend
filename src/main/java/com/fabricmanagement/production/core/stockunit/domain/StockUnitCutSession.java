package com.fabricmanagement.production.core.stockunit.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Target/evidence binding only. Leases, expiry and renewal remain in platform.realtime. */
@Entity
@Table(name = "stock_unit_cut_session", schema = "production")
@Getter
@NoArgsConstructor
public class StockUnitCutSession extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID sessionId;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false)
  private String targetType;

  @Column(nullable = false, updatable = false)
  private UUID targetId;

  @Column(nullable = false, updatable = false)
  private UUID targetLineId;

  @Column(nullable = false, updatable = false)
  private UUID productId;

  @Column(nullable = false)
  private String targetFingerprint;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<UUID> sourceIds = List.of();

  private UUID cutSourceId;
  private String evidenceHash;

  @Column(nullable = false)
  private boolean ended;

  private UUID previousPlanId;
  private Long previousPlanVersion;

  public static StockUnitCutSession open(
      UUID session,
      UUID actor,
      String type,
      UUID target,
      UUID line,
      UUID product,
      String fingerprint) {
    var row = new StockUnitCutSession();
    row.sessionId = session;
    row.actorId = actor;
    row.targetType = type;
    row.targetId = target;
    row.targetLineId = line;
    row.productId = product;
    row.targetFingerprint = fingerprint;
    return row;
  }

  public void remember(List<UUID> ids, UUID cut, String hash) {
    sourceIds = List.copyOf(ids);
    cutSourceId = cut;
    evidenceHash = hash;
  }

  public void end() {
    ended = true;
  }

  public void bindPlan(UUID id, Long version) {
    previousPlanId = id;
    previousPlanVersion = version;
  }

  public void bindDraftEvidence(String tokenHash) {
    if (!"DRAFT_LINE".equals(targetType))
      throw new IllegalStateException("Only a draft preview binds a source period");
    targetFingerprint = tokenHash;
  }

  @Override
  protected String getModuleCode() {
    return "CUTS";
  }
}
