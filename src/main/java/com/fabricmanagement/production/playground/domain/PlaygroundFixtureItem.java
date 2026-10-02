package com.fabricmanagement.production.playground.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Stable per-tenant fixture key bound to the private row it created. */
@Entity
@Table(name = "playground_fixture_item", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaygroundFixtureItem extends BaseEntity {

  @Column(name = "fixture_key", nullable = false, length = 80, updatable = false)
  private String fixtureKey;

  @Column(name = "entity_type", nullable = false, length = 40, updatable = false)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  public static PlaygroundFixtureItem of(String fixtureKey, String entityType, UUID entityId) {
    PlaygroundFixtureItem item = new PlaygroundFixtureItem();
    item.fixtureKey = fixtureKey;
    item.entityType = entityType;
    item.entityId = entityId;
    return item;
  }

  /** Rebinds a key whose row was lost during an interrupted provisioning. */
  public void rebind(UUID newEntityId) {
    this.entityId = newEntityId;
  }

  @Override
  protected String getModuleCode() {
    return "PGFI";
  }
}
