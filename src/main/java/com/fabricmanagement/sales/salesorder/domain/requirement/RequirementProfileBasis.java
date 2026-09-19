package com.fabricmanagement.sales.salesorder.domain.requirement;

import java.time.Instant;
import java.util.UUID;

/** Immutable identity of the source from which an order-line requirement profile was resolved. */
public record RequirementProfileBasis(
    Kind kind,
    UUID productId,
    String specificationKind,
    UUID referenceId,
    Integer specificationVersion,
    UUID actorId,
    Instant decidedAt,
    String decisionReference) {

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
