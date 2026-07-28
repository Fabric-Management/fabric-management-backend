package com.fabricmanagement.sales.ownership.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ownership_policy", schema = "sales")
@Getter
@Setter(AccessLevel.NONE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnershipPolicy extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "default_mode", nullable = false, length = 20)
  private OwnershipMode defaultMode;

  @Column(name = "mode_effective_at", nullable = false)
  private Instant modeEffectiveAt;

  @Column(name = "assignment_ladder_enabled", nullable = false)
  private boolean assignmentLadderEnabled;

  @Column(name = "triage_age_threshold_hours", nullable = false)
  private int triageAgeThresholdHours;

  @Override
  protected String getModuleCode() {
    return "OWNP";
  }
}
