package com.fabricmanagement.production.playground.domain;

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

/**
 * Trusted initial-provisioning marker for playground fibre fixtures (FIBER-CATALOG-1 §9). Only the
 * provisioning flow that created the tenant records it; without a PENDING marker nothing installs
 * fixtures, and COMPLETED makes every later call a no-op.
 */
@Entity
@Table(name = "playground_fixture_run", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaygroundFixtureRun extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "origin", nullable = false, length = 40, updatable = false)
  private PlaygroundFixtureOrigin origin;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PlaygroundFixtureRunStatus status;

  @Column(name = "completed_at")
  private Instant completedAt;

  public static PlaygroundFixtureRun pending(PlaygroundFixtureOrigin origin) {
    PlaygroundFixtureRun run = new PlaygroundFixtureRun();
    run.origin = origin;
    run.status = PlaygroundFixtureRunStatus.PENDING;
    return run;
  }

  public boolean isCompleted() {
    return status == PlaygroundFixtureRunStatus.COMPLETED;
  }

  public void complete(Instant at) {
    this.status = PlaygroundFixtureRunStatus.COMPLETED;
    this.completedAt = at;
  }

  @Override
  protected String getModuleCode() {
    return "PGFX";
  }
}
