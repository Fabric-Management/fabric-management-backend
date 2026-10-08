package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.platform.realtime.dto.LiveCloseReason;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CEDIT-05 L20: the published contract of the live stream, read from the running application's
 * OpenAPI 3.1 document. The stream content is the anyOf of the three frame envelopes with an
 * event-to-schema map (ready and invalidated share one shape, so oneOf would reject valid frames:
 * R3); real serialized frames are validated against the published schemas with a JSON Schema
 * 2020-12 validator; the streaming Java type is not a schema; refusals are typed problems;
 * Last-Event-ID is documented as ignored.
 */
class SalesOrderLiveOpenApiIT extends SalesOrderLiveItSupport {

  private static final String REF = "#/components/schemas/";
  private static final String LIVE_PATH = "/api/v1/sales/orders/{orderId}/live-events";
  private static final String PROBLEM_JSON = "application/problem+json";

  @Autowired private MockMvc mockMvc;

  private Map<String, Object> document;
  private ObjectNode documentTree;

  @BeforeEach
  void readDocument() throws Exception {
    String yaml =
        mockMvc
            .perform(get("/api-docs.yaml"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    document = new YAMLMapper().readValue(yaml, new TypeReference<Map<String, Object>>() {});
    documentTree = (ObjectNode) new YAMLMapper().readTree(yaml);
    assertThat(document.get("openapi")).isEqualTo("3.1.0");
  }

  @Test
  @DisplayName(
      "L20/R3: 200 is text/event-stream only, the anyOf of the three frame envelopes, with the"
          + " event-to-schema map")
  void streamContentIsTheFrameEnvelopes() {
    Map<String, Object> operation = operation();
    assertThat(operation).containsEntry("operationId", "subscribeSalesOrderLiveEvents");
    assertThat(operation.get("tags"))
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .contains("Sales Order Live Events");
    assertThat(operation).containsKey("x-fabric-live-stream");

    Map<String, Object> content = map(operation, "responses", "200", "content");
    assertThat(content.keySet()).containsExactly("text/event-stream");
    Map<String, Object> stream = map(content, "text/event-stream");
    Map<String, Object> streamSchema = map(stream, "schema");
    assertThat(streamSchema).doesNotContainKey("oneOf");
    List<String> frames =
        list(streamSchema.get("anyOf")).stream().map(item -> refOf(map(item))).toList();
    assertThat(frames)
        .containsExactly(
            REF + "ApiResponseLiveReadyDto",
            REF + "ApiResponseLiveInvalidatedDto",
            REF + "ApiResponseLiveClosedDto");
    // A map: the published order of its keys carries no meaning (the export sorts them).
    assertThat(map(streamSchema, "x-fabric-live-frames"))
        .containsOnly(
            Map.entry("ready", REF + "ApiResponseLiveReadyDto"),
            Map.entry("invalidated", REF + "ApiResponseLiveInvalidatedDto"),
            Map.entry("closed", REF + "ApiResponseLiveClosedDto"));
    assertThat(map(stream, "examples").keySet())
        .contains("ready", "invalidated", "closed", "keepalive");
    assertThat(map(operation, "responses", "200", "headers").keySet())
        .contains("Cache-Control", "X-Accel-Buffering");
    assertThat(String.valueOf(operation)).doesNotContain(REF + "SseEmitter");
  }

  @Test
  @DisplayName("L20: each envelope is the standard ApiResponse with its frame DTO as data")
  void envelopes() {
    Map<String, String> dataOf =
        Map.of(
            "ApiResponseLiveReadyDto", "LiveReadyDto",
            "ApiResponseLiveInvalidatedDto", "LiveInvalidatedDto",
            "ApiResponseLiveClosedDto", "LiveClosedDto");
    dataOf.forEach(
        (envelope, data) -> {
          Map<String, Object> schema = schema(envelope);
          assertThat(list(schema.get("required"))).contains("success", "data", "timestamp");
          Map<String, Object> properties = map(schema, "properties");
          assertThat(refOf(map(properties, "data"))).isEqualTo(REF + data);
          assertThat(typeOf(map(properties, "success"))).isEqualTo("boolean");
          assertThat(map(properties, "timestamp")).containsEntry("format", "date-time");
        });
  }

  @Test
  @DisplayName("L20: frame DTOs state required ids, uuid formats and a non-empty string revision")
  void frameSchemas() {
    for (String name : List.of("LiveReadyDto", "LiveInvalidatedDto")) {
      Map<String, Object> schema = schema(name);
      assertThat(list(schema.get("required")))
          .containsExactlyInAnyOrder("connectionId", "resourceId", "revision");
      Map<String, Object> properties = map(schema, "properties");
      assertThat(map(properties, "connectionId")).containsEntry("format", "uuid");
      assertThat(map(properties, "resourceId")).containsEntry("format", "uuid");
      assertThat(typeOf(map(properties, "revision"))).isEqualTo("string");
      assertThat(map(properties, "revision")).containsEntry("minLength", 1);
      // CEDIT-06: optional, never mixed with revision.
      assertThat(typeOf(map(properties, "presenceRevision"))).isEqualTo("string");
      assertThat(map(properties, "presenceRevision")).containsEntry("minLength", 1);
      // CEDIT-07: optional lease marker, never mixed with the other two.
      assertThat(typeOf(map(properties, "leaseRevision"))).isEqualTo("string");
      assertThat(map(properties, "leaseRevision")).containsEntry("minLength", 1);
    }
    Map<String, Object> closed = schema("LiveClosedDto");
    assertThat(list(closed.get("required"))).containsExactlyInAnyOrder("connectionId", "reason");
    assertThat(refOf(map(closed, "properties", "reason"))).isEqualTo(REF + "LiveCloseReason");
    assertThat(list(schema("LiveCloseReason").get("enum")))
        .containsExactlyInAnyOrder(
            "AUTH_EXPIRED",
            "ACCESS_REVOKED",
            "RECONNECT_REQUIRED",
            "TEMPORARILY_UNAVAILABLE",
            "SLOW_CONSUMER",
            "FEATURE_DISABLED");
  }

  @Test
  @DisplayName("L20: refusals are typed problems; 429 and 503 carry Retry-After")
  void refusals() {
    Map<String, Object> responses = map(operation(), "responses");
    for (String code : List.of("401", "403", "404", "429", "503")) {
      assertThat(refOf(map(responses, code, "content", PROBLEM_JSON, "schema")))
          .as(code)
          .isEqualTo(REF + "ApiProblemDetail");
    }
    assertThat(map(responses, "429", "headers")).containsKey("Retry-After");
    assertThat(map(responses, "503", "headers")).containsKey("Retry-After");
  }

  @Test
  @DisplayName("L20: the order id is a required uuid; Last-Event-ID is optional and not replayed")
  void parameters() {
    List<Map<String, Object>> parameters =
        list(operation().get("parameters")).stream().map(SalesOrderLiveOpenApiIT::map).toList();
    Map<String, Object> orderId =
        parameters.stream().filter(p -> "orderId".equals(p.get("name"))).findFirst().orElseThrow();
    assertThat(orderId).containsEntry("in", "path").containsEntry("required", true);
    assertThat(map(orderId, "schema")).containsEntry("format", "uuid");

    Map<String, Object> lastEventId =
        parameters.stream()
            .filter(p -> "Last-Event-ID".equals(p.get("name")))
            .findFirst()
            .orElseThrow();
    assertThat(lastEventId).containsEntry("in", "header");
    assertThat(lastEventId.get("required")).isNotEqualTo(true);
    assertThat(String.valueOf(lastEventId.get("description"))).contains("never replayed");
    assertThat(parameters)
        .extracting(p -> p.get("name"))
        .doesNotContain("token", "access_token", "tenantId", "actorId");
  }

  @Test
  @DisplayName("L20: normal JSON endpoints keep their ApiResponse envelope")
  void jsonEnvelopeUnchanged() {
    Map<String, Object> save =
        map(document, "paths", "/api/v1/sales/orders/{orderId}/edit-operations", "post");
    // The JSON endpoints publish their media type as the project's controllers declare it.
    Map<String, Object> content = map(save, "responses", "200", "content");
    assertThat(content).hasSize(1).doesNotContainKey("text/event-stream");
    Object only = content.values().iterator().next();
    assertThat(refOf(map(only, "schema"))).isEqualTo(REF + "ApiResponseSalesOrderEditResult");
  }

  @Test
  @DisplayName(
      "R3: real ready, invalidated and closed frames validate against the published stream schema"
          + " and their own envelope; broken bodies are rejected")
  void realFramesValidateAgainstThePublishedSchemas() throws Exception {
    LiveSse stream = subscribe(actorB);
    LiveSse.Frame ready = ready(stream);
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("Schema check")));
    LiveSse.Frame invalidated = stream.nextEvent(WAIT);
    assertThat(invalidated.event()).isEqualTo("invalidated");
    liveStreams.closeAll(LiveCloseReason.RECONNECT_REQUIRED);
    LiveSse.Frame closed = stream.nextEvent(WAIT);
    assertThat(closed.event()).isEqualTo("closed");

    Map<String, Object> byEvent =
        map(
            document,
            "paths",
            LIVE_PATH,
            "get",
            "responses",
            "200",
            "content",
            "text/event-stream",
            "schema",
            "x-fabric-live-frames");
    for (LiveSse.Frame frame : List.of(ready, invalidated, closed)) {
      JsonNode body = objectMapper.readTree(frame.data());
      assertThat(validateStream(body)).as(frame.event() + " against the stream").isEmpty();
      assertThat(validateRef(String.valueOf(byEvent.get(frame.event())), body))
          .as(frame.event() + " against its envelope")
          .isEmpty();
    }

    JsonNode readyBody = objectMapper.readTree(ready.data());
    String readyRef = String.valueOf(byEvent.get("ready"));
    ObjectNode missingRevision = readyBody.deepCopy();
    ((ObjectNode) missingRevision.get("data")).remove("revision");
    ObjectNode emptyRevision = readyBody.deepCopy();
    ((ObjectNode) emptyRevision.get("data")).put("revision", "");
    ObjectNode numericId = readyBody.deepCopy();
    ((ObjectNode) numericId.get("data")).put("connectionId", 42);
    ObjectNode noData = readyBody.deepCopy();
    noData.remove("data");
    // CEDIT-06: a sales order's frames carry the presence marker beside the revision.
    assertThat(readyBody.path("data").path("presenceRevision").asText()).isEqualTo("0");
    ObjectNode emptyPresence = readyBody.deepCopy();
    ((ObjectNode) emptyPresence.get("data")).put("presenceRevision", "");
    // CEDIT-07: and the lease marker; nobody holds a lease yet.
    assertThat(readyBody.path("data").path("leaseRevision").asText()).isEqualTo("0");
    ObjectNode emptyLease = readyBody.deepCopy();
    ((ObjectNode) emptyLease.get("data")).put("leaseRevision", "");
    for (JsonNode broken :
        List.of(missingRevision, emptyRevision, numericId, noData, emptyPresence, emptyLease)) {
      assertThat(validateRef(readyRef, broken)).as(broken.toString()).isNotEmpty();
      assertThat(validateStream(broken)).as(broken.toString()).isNotEmpty();
    }
    ObjectNode unknownReason = objectMapper.readTree(closed.data()).deepCopy();
    ((ObjectNode) unknownReason.get("data")).put("reason", "LATER");
    assertThat(validateRef(String.valueOf(byEvent.get("closed")), unknownReason)).isNotEmpty();
    assertThat(validateStream(unknownReason)).isNotEmpty();
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  /** Validates against the published stream schema (its anyOf), refs resolved in the document. */
  private Set<ValidationMessage> validateStream(JsonNode instance) {
    ObjectNode root = documentTree.deepCopy();
    JsonNode streamSchema =
        documentTree
            .path("paths")
            .path(LIVE_PATH)
            .path("get")
            .path("responses")
            .path("200")
            .path("content")
            .path("text/event-stream")
            .path("schema");
    assertThat(streamSchema.path("anyOf").isArray()).isTrue();
    root.set("anyOf", streamSchema.get("anyOf").deepCopy());
    return schema(root).validate(instance);
  }

  /** Validates against one component schema of the published document. */
  private Set<ValidationMessage> validateRef(String ref, JsonNode instance) {
    assertThat(ref).startsWith(REF);
    ObjectNode root = documentTree.deepCopy();
    root.put("$ref", ref);
    return schema(root).validate(instance);
  }

  private static JsonSchema schema(ObjectNode root) {
    return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(root);
  }

  private Map<String, Object> operation() {
    return map(document, "paths", LIVE_PATH, "get");
  }

  private Map<String, Object> schema(String name) {
    return map(document, "components", "schemas", name);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object node, String... path) {
    Object current = node;
    for (String key : path) {
      assertThat(current).as("parent of " + key).isInstanceOf(Map.class);
      current = ((Map<String, Object>) current).get(key);
    }
    assertThat(current).as(String.join("/", path)).isInstanceOf(Map.class);
    return (Map<String, Object>) current;
  }

  @SuppressWarnings("unchecked")
  private static <T> List<T> list(Object node) {
    assertThat(node).isInstanceOf(List.class);
    return (List<T>) node;
  }

  private static String refOf(Map<String, Object> schema) {
    Object ref = schema.get("$ref");
    if (ref == null && schema.get("anyOf") instanceof List<?> anyOf) {
      ref =
          anyOf.stream()
              .map(SalesOrderLiveOpenApiIT::map)
              .map(item -> item.get("$ref"))
              .filter(java.util.Objects::nonNull)
              .findFirst()
              .orElse(null);
    }
    return ref == null ? null : ref.toString();
  }

  private static String typeOf(Map<String, Object> schema) {
    Object type = schema.get("type");
    if (type instanceof List<?> types) {
      return types.stream()
          .map(Object::toString)
          .filter(t -> !"null".equals(t))
          .findFirst()
          .orElse(null);
    }
    return type == null ? null : type.toString();
  }
}
