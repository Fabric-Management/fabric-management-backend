package com.fabricmanagement.sales.salesorder.domain.requirement;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A pinned specification constraint that must never disappear merely because no comparator exists.
 */
public record UnmodelledSpecConstraint(
    String field,
    Status status,
    JsonNode sourceValue,
    String unitOrVocabulary,
    String meaning,
    String reason) {

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
