package com.fabricmanagement.sales.salesorder.domain.requirement;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Immutable identity of the source from which an order-line requirement profile was resolved. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record RequirementProfileBasis(
    Kind kind,
    UUID productId,
    String specificationKind,
    UUID referenceId,
    Integer specificationVersion,
    UUID actorId,
    Instant decidedAt,
    String decisionReference) {
  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown RequirementProfileBasis property: " + name);
  }

  public enum Kind {
    SPEC_VERSION,
    LINE_EXPLICIT,
    AUTHORISED_DECISION
  }

  public RequirementProfileBasis {
    if (kind == null) {
      throw new IllegalArgumentException("Requirement profile basis kind is required");
    }
    if (kind == Kind.SPEC_VERSION
        && (productId == null
            || specificationKind == null
            || specificationKind.isBlank()
            || referenceId == null
            || specificationVersion == null
            || specificationVersion < 1)) {
      throw new IllegalArgumentException(
          "SPEC_VERSION basis requires product, specification kind, reference and positive version");
    }
    if (kind == Kind.AUTHORISED_DECISION
        && (actorId == null
            || decidedAt == null
            || decisionReference == null
            || decisionReference.isBlank())) {
      throw new IllegalArgumentException(
          "AUTHORISED_DECISION basis requires actor, timestamp and decision reference");
    }
    if (kind == Kind.LINE_EXPLICIT
        && (actorId == null
            || decidedAt == null
            || decisionReference == null
            || decisionReference.isBlank())) {
      throw new IllegalArgumentException(
          "LINE_EXPLICIT basis requires actor, timestamp and instruction reference");
    }
  }
}
