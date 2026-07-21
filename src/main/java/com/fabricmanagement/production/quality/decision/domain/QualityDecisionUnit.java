package com.fabricmanagement.production.quality.decision.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/** Append-only population snapshot row for a quality decision. */
@Entity
@Table(name = "quality_decision_unit", schema = "production")
@IdClass(QualityDecisionUnitId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QualityDecisionUnit implements Persistable<QualityDecisionUnitId> {

  @Id
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Id
  @Column(name = "decision_id", nullable = false, updatable = false)
  private UUID decisionId;

  @Id
  @Column(name = "stock_unit_id", nullable = false, updatable = false)
  private UUID stockUnitId;

  @Override
  @Transient
  public QualityDecisionUnitId getId() {
    return new QualityDecisionUnitId(tenantId, decisionId, stockUnitId);
  }

  /** Population snapshots are insert-only and use assigned composite keys. */
  @Override
  @Transient
  public boolean isNew() {
    return true;
  }

  public static QualityDecisionUnit of(UUID tenantId, UUID decisionId, UUID stockUnitId) {
    QualityDecisionUnit unit = new QualityDecisionUnit();
    unit.tenantId = tenantId;
    unit.decisionId = decisionId;
    unit.stockUnitId = stockUnitId;
    return unit;
  }
}
