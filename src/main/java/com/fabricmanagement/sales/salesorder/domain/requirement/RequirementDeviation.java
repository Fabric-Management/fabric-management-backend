package com.fabricmanagement.sales.salesorder.domain.requirement;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Traceable customer or authorised deviation from a pinned specification. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record RequirementDeviation(
    String facetIdentity, String reference, UUID actorId, Instant decidedAt) {
  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown RequirementDeviation property: " + name);
  }

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
