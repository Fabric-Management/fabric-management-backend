package com.fabricmanagement.flowboard.decision.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverDetail.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.v3.core.converter.ModelConverters;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DecisionQueueContractTest {
  private final ObjectMapper mapper =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Test
  void dueDateIsAnIsoCalendarDateWithoutTimeOrZone() throws Exception {
    var json = mapper.readTree(mapper.writeValueAsBytes(item(LocalDate.of(2026, 10, 1))));
    assertThat(json.get("dueDate").asText()).isEqualTo("2026-10-01");
    assertThat(mapper.readTree(mapper.writeValueAsBytes(item(null))).get("dueDate").isNull())
        .isTrue();
  }

  @Test
  void commonEnvelopeContainsNoOrderCoverLineFields() {
    assertThat(Arrays.stream(DecisionQueueItem.class.getRecordComponents()).map(c -> c.getName()))
        .doesNotContain("lineIds", "lines", "unresolvedLineIds", "shortfall");
  }

  @Test
  void exportedDecisionSchemasPinRequiredAndNullableFields() {
    var schemas = new LinkedHashMap<String, io.swagger.v3.oas.models.media.Schema>();
    schemas.putAll(ModelConverters.getInstance().readAll(DecisionQueueItem.class));
    schemas.putAll(ModelConverters.getInstance().readAll(DecisionQueueSummary.class));
    schemas.putAll(ModelConverters.getInstance().readAll(DecisionProjectionRebuildRequest.class));
    schemas.putAll(ModelConverters.getInstance().readAll(DecisionProjectionRebuildResponse.class));

    assertThat(schemas.keySet().stream().filter(name -> name.startsWith("Decision")).toList())
        .doesNotHaveDuplicates();
    assertThat(schemas.get("DecisionQueueItem").getRequired())
        .containsExactlyInAnyOrder(
            "id",
            "kind",
            "taskId",
            "taskVersion",
            "subject",
            "assignment",
            "priority",
            "dueDate",
            "createdAt",
            "projectedAt",
            "state",
            "verdictCode",
            "actions",
            "detailHref");
    assertThat(property(schemas, "DecisionQueueItem", "dueDate").getNullable()).isTrue();
    assertThat(property(schemas, "DecisionQueueItem", "dueDate").getFormat()).isEqualTo("date");
    assertThat(schemas.get("DecisionQueueSummary").getRequired())
        .containsExactlyInAnyOrder(
            "mineCount",
            "departmentCount",
            "unassignedCount",
            "waitingCount",
            "computedAt",
            "stale");
    assertThat(property(schemas, "DecisionQueueSummary", "unassignedCount").getNullable()).isTrue();
    assertThat(property(schemas, "DecisionQueueSummary", "computedAt").getFormat())
        .isEqualTo("date-time");
    assertThat(schemas.get("DecisionProjectionRebuildResponse").getRequired())
        .containsExactlyInAnyOrder("scanned", "inserted", "updated", "unchanged", "orphaned");
    assertThat(schemas.get("DecisionProjectionRebuildResponse").getProperties())
        .containsKey("orphaned")
        .doesNotContainKey("deleted");
    assertThat(property(schemas, "DecisionProjectionRebuildRequest", "caseIds").getMaxItems())
        .isEqualTo(100);
    assertThat(enumValues(schemas, "DecisionQueueItem", "kind")).containsExactly("ORDER_COVER");
    assertThat(enumValues(schemas, "DecisionQueueItem", "verdictCode"))
        .containsExactlyInAnyOrder("ACTIONABLE", "EVIDENCE_UNKNOWN", "NO_EVIDENCE", "CASE_CLOSED");
    assertThat(schemas.keySet().stream().filter(name -> name.equals("VerdictCode"))).isEmpty();
  }

  @Test
  void qualityFixtureUsesTheSharedEnvelopeWithoutOrderCoverFields() throws Exception {
    record QualityPayload(UUID inspectionId, String grade) {}
    record QualityFixture(
        UUID id,
        String kind,
        UUID taskId,
        QualityPayload payload,
        List<DecisionCapability> actions) {}

    var fixture =
        new QualityFixture(
            UUID.randomUUID(),
            "QUALITY",
            UUID.randomUUID(),
            new QualityPayload(UUID.randomUUID(), "A"),
            List.of());
    var json = mapper.readTree(mapper.writeValueAsBytes(fixture));

    assertThat(json.path("kind").asText()).isEqualTo("QUALITY");
    assertThat(json.path("payload").has("inspectionId")).isTrue();
    assertThat(json.toString())
        .doesNotContain("lineCover", "unresolvedLineIds", "shortfall", "orderCover");
  }

  private DecisionQueueItem item(LocalDate dueDate) {
    UUID id = UUID.randomUUID();
    return new DecisionQueueItem(
        id,
        DecisionQueueItem.Kind.ORDER_COVER,
        UUID.randomUUID(),
        0,
        new DecisionSubjectRef(DecisionSubjectType.SALES_ORDER, UUID.randomUUID(), "SO-1", null),
        new DecisionAssignment(DecisionAssignmentBucket.MINE, List.of(), List.of(), null),
        Priority.HIGH,
        dueDate,
        Instant.parse("2026-09-21T10:00:00Z"),
        Instant.parse("2026-09-21T10:00:01Z"),
        DecisionQueueItem.State.OPEN,
        DecisionVerdictCode.ACTIONABLE,
        List.of(),
        "/sales/orders/1/cover");
  }

  @SuppressWarnings("unchecked")
  private static io.swagger.v3.oas.models.media.Schema<?> property(
      Map<String, io.swagger.v3.oas.models.media.Schema> schemas, String schema, String property) {
    return (io.swagger.v3.oas.models.media.Schema<?>)
        schemas.get(schema).getProperties().get(property);
  }

  private static List<String> enumValues(
      Map<String, io.swagger.v3.oas.models.media.Schema> schemas, String schema, String property) {
    var value = property(schemas, schema, property);
    if (value.getEnum() == null) {
      String reference = value.get$ref();
      assertThat(reference).as("enum schema reference for %s.%s", schema, property).isNotBlank();
      value = schemas.get(reference.substring(reference.lastIndexOf('/') + 1));
      assertThat(value).as("referenced enum schema for %s.%s", schema, property).isNotNull();
    }
    assertThat(value.getEnum()).as("enum values for %s.%s", schema, property).isNotNull();
    return value.getEnum().stream().map(String.class::cast).toList();
  }
}
