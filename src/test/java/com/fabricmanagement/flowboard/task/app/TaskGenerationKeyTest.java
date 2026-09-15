package com.fabricmanagement.flowboard.task.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.flowboard.task.domain.TaskType;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskGenerationKeyTest {

  @Test
  void formatsAcceptedSalesOrderIdentityWithoutTenantOrWorkflowVersion() {
    UUID orderId = UUID.fromString("4d68dd8e-3838-4cc3-bfd4-d38b916efb54");

    String key = TaskGenerationKey.subject("sales_order", orderId, TaskType.ORDER_COVER, null);

    assertThat(key)
        .isEqualTo(
            "subjectType:SALES_ORDER:subjectId:4d68dd8e-3838-4cc3-bfd4-d38b916efb54:taskType:ORDER_COVER:fulfillmentMode:NONE");
    assertThat(key).doesNotContain("tenant", "workflowVersion");
  }

  @Test
  void recurrenceIncludesOccurrenceIdentity() {
    UUID templateId = UUID.fromString("84070f1f-4e6d-45c2-937b-0592f2cdfd23");
    OffsetDateTime occurrence = OffsetDateTime.parse("2026-09-15T10:15:30Z");

    assertThat(TaskGenerationKey.recurring(templateId, occurrence))
        .isEqualTo(
            "recurringTemplate:84070f1f-4e6d-45c2-937b-0592f2cdfd23:occurrence:2026-09-15T10:15:30Z");
  }

  @Test
  void recurrenceNormalizesEquivalentOffsetsToOneInstant() {
    UUID templateId = UUID.randomUUID();

    String utc =
        TaskGenerationKey.recurring(templateId, OffsetDateTime.parse("2026-09-15T10:00:00Z"));
    String londonSummer =
        TaskGenerationKey.recurring(templateId, OffsetDateTime.parse("2026-09-15T11:00:00+01:00"));

    assertThat(londonSummer).isEqualTo(utc);
  }

  @Test
  void automationIdentityIsPerRuleAndCannotBeSuppressedByManualWork() {
    UUID subjectId = UUID.randomUUID();
    UUID firstRule = UUID.randomUUID();
    UUID secondRule = UUID.randomUUID();

    String first =
        TaskGenerationKey.automation(firstRule, "SALES_ORDER", subjectId, TaskType.PLANNING);
    String second =
        TaskGenerationKey.automation(secondRule, "SALES_ORDER", subjectId, TaskType.PLANNING);
    String manual = TaskGenerationKey.manualRequest(UUID.randomUUID());

    assertThat(first).isNotEqualTo(second).isNotEqualTo(manual);
    assertThat(manual).startsWith("task:manualRequest:");
  }
}
