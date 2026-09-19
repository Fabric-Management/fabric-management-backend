package com.fabricmanagement.sales.salesorder.domain.requirement;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Traceable customer or authorised deviation from a pinned specification. */
public record RequirementDeviation(
    String facetIdentity, String reference, UUID actorId, Instant decidedAt) {
  public RequirementDeviation {
    if (facetIdentity == null
        || facetIdentity.isBlank()
        || reference == null
        || reference.isBlank()
        || actorId == null
        || decidedAt == null) {
      throw new IllegalArgumentException(
          "Requirement deviation requires facet, reference, actor and timestamp");
    }
    facetIdentity = facetIdentity.strip().toUpperCase(Locale.ROOT);
    reference = reference.strip();
  }
}
