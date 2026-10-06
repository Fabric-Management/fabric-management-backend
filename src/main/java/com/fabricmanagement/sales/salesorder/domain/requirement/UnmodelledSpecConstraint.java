package com.fabricmanagement.sales.salesorder.domain.requirement;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A pinned specification constraint that must never disappear merely because no comparator exists.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record UnmodelledSpecConstraint(
    String field,
    Status status,
    JsonNode sourceValue,
    String unitOrVocabulary,
    String meaning,
    String reason) {
  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown UnmodelledSpecConstraint property: " + name);
  }

  public enum Status {
    RESOLVED_UNSUPPORTED,
    MISSING_SOURCE_VALUE,
    AMBIGUOUS_MEANING,
    NOT_APPLICABLE
  }

  public UnmodelledSpecConstraint {
    if (field == null || field.isBlank() || status == null) {
      throw new IllegalArgumentException("Unmodelled constraint field and status are required");
    }
    if (status == Status.RESOLVED_UNSUPPORTED
        && (sourceValue == null || sourceValue.isNull() || meaning == null || meaning.isBlank())) {
      throw new IllegalArgumentException(
          "Resolved unsupported constraint requires pinned value and meaning: " + field);
    }
  }

  public boolean makesProfileIncomplete() {
    return status == Status.MISSING_SOURCE_VALUE || status == Status.AMBIGUOUS_MEANING;
  }
}
