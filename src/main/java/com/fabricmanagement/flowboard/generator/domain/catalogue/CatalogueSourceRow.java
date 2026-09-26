package com.fabricmanagement.flowboard.generator.domain.catalogue;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A keyed task-template row read from a catalogue source tenant (golden-template or the playground
 * source). Enum-typed columns are kept as the stored strings so a source row is copied verbatim.
 */
public record CatalogueSourceRow(
    String catalogKey,
    String name,
    String description,
    String eventType,
    String titleTemplate,
    String taskType,
    String moduleType,
    String defaultPriority,
    String defaultAssigneeRole,
    BigDecimal estimatedHours,
    String checklistTemplate,
    String autoLabels,
    boolean active,
    Instant deletedAt) {

  public String fingerprint() {
    return TaskTemplateCatalogue.fingerprint(
        titleTemplate,
        taskType,
        moduleType,
        defaultPriority,
        defaultAssigneeRole,
        estimatedHours,
        autoLabels,
        checklistTemplate);
  }
}
