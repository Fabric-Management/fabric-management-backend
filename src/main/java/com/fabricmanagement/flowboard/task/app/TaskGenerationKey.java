package com.fabricmanagement.flowboard.task.app;

import com.fabricmanagement.flowboard.task.domain.TaskType;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/** The only formatter for Task logical identities. */
public final class TaskGenerationKey {

  private TaskGenerationKey() {}

  /** Runtime manual identity is the request identity; legacy rows use a labelled migration key. */
  public static String manualRequest(UUID requestIdentity) {
    return "task:manualRequest:" + require(requestIdentity, "Manual request identity");
  }

  public static String subject(
      String subjectType, UUID subjectId, TaskType taskType, String fulfillmentMode) {
    return "subjectType:%s:subjectId:%s:taskType:%s:fulfillmentMode:%s"
        .formatted(
            token(subjectType, "Subject type"),
            require(subjectId, "Subject id"),
            require(taskType, "Task type").name(),
            fulfillmentMode == null || fulfillmentMode.isBlank()
                ? "NONE"
                : fulfillmentMode.trim().toUpperCase(Locale.ROOT));
  }

  public static String recurring(UUID templateId, OffsetDateTime occurrence) {
    return "recurringTemplate:%s:occurrence:%s"
        .formatted(
            require(templateId, "Recurring template id"),
            require(occurrence, "Occurrence").toInstant());
  }

  public static String automation(
      UUID ruleId, String subjectType, UUID subjectId, TaskType taskType) {
    return "automationRule:%s:%s"
        .formatted(
            require(ruleId, "Automation rule id"), subject(subjectType, subjectId, taskType, null));
  }

  private static String token(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static <T> T require(T value, String label) {
    if (value == null) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value;
  }
}
