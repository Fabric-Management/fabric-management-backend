package com.fabricmanagement.platform.user.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Frozen accepted command result; scoped to the journey and original actor, not a browser draft.
 */
@Entity
@Table(name = "onboarding_operation", schema = "common_user")
@Getter
@NoArgsConstructor
public class OnboardingOperation extends BaseEntity {
  @Column(name = "journey_id", nullable = false, updatable = false)
  private UUID journeyId;

  @Column(name = "operation_id", nullable = false, updatable = false)
  private UUID operationId;

  @Column(name = "actor_user_id", nullable = false, updatable = false)
  private UUID actorUserId;

  @Column(name = "command_hash", nullable = false, updatable = false, length = 64)
  private String commandHash;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "result", nullable = false, updatable = false, columnDefinition = "jsonb")
  private JsonNode result;

  public static OnboardingOperation accepted(
      OnboardingJourney journey, UUID operation, UUID actor, String hash, JsonNode result) {
    var receipt = new OnboardingOperation();
    receipt.setTenantId(journey.getTenantId());
    receipt.journeyId = journey.getId();
    receipt.operationId = operation;
    receipt.actorUserId = actor;
    receipt.commandHash = hash;
    receipt.result = result;
    return receipt;
  }

  @Override
  protected String getModuleCode() {
    return "ONBO";
  }
}
