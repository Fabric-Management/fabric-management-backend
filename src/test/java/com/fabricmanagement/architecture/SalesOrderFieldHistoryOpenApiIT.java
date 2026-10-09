package com.fabricmanagement.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.sales.salesorder.app.SalesOrderLiveItSupport;
import com.fabricmanagement.sales.salesorder.domain.OrderEditKey;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The published contract of the field history read (CEDIT-09 §3, H25): operation, parameters and
 * envelope; closed page, entry, value and shape schemas; the edit key catalogue with an open stored
 * key; {@code anyOf} with a null arm for a value's data; exact decimals as patterned strings;
 * nullability and enums. Real HTTP answers are validated against the published document with the
 * JSON Schema 2020-12 validator (formats asserted), and broken copies of them are refused (R2).
 */
class SalesOrderFieldHistoryOpenApiIT extends SalesOrderLiveItSupport {

  private static final String REF = "#/components/schemas/";
  private static final String HISTORY_PATH = "/api/v1/sales/orders/{orderId}/field-history";
  private static final List<String> SHAPES =
      List.of(
          "SalesOrderFieldHistoryRequestedDate",
          "SalesOrderFieldHistoryDeliveryTerms",
          "SalesOrderFieldHistoryAgreement",
          "SalesOrderFieldHistoryContact",
          "SalesOrderFieldHistoryWidth",
          "SalesOrderFieldHistoryQuantity",
          "SalesOrderFieldHistoryPricing",
          "SalesOrderFieldHistoryTolerance",
          "SalesOrderFieldHistorySpecification",
          "SalesOrderFieldHistoryLine");

  private static final int MAX_LIMIT = SalesOrderFieldHistoryDtos.MAX_LIMIT;
  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

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
  @DisplayName("H25: operation id, read-only GET, limit and cursor bounds, the typed envelope")
  void operation() {
    Map<String, Object> operation = mapAt(document, "paths", HISTORY_PATH, "get");
    assertThat(operation).containsEntry("operationId", "getSalesOrderFieldHistory");
    assertThat(mapAt(document, "paths", HISTORY_PATH))
        .containsKey("get")
        .doesNotContainKeys("post", "put", "patch", "delete");

    Map<String, Object> limit = parameter(operation, "limit");
    assertThat(limit).containsEntry("in", "query");
    assertThat(limit.get("required")).isNotEqualTo(Boolean.TRUE);
    Map<String, Object> limitSchema = asMap(limit.get("schema"));
    assertThat(typesOf(limitSchema)).contains("integer");
    assertThat(number(limitSchema.get("minimum"))).isEqualTo(1);
    assertThat(number(limitSchema.get("maximum"))).isEqualTo(SalesOrderFieldHistoryDtos.MAX_LIMIT);
    assertThat(number(limitSchema.get("default")))
        .isEqualTo(SalesOrderFieldHistoryDtos.DEFAULT_LIMIT);

    Map<String, Object> cursor = parameter(operation, "cursor");
    assertThat(cursor).containsEntry("in", "query");
    Map<String, Object> cursorSchema = asMap(cursor.get("schema"));
    assertThat(typesOf(cursorSchema)).contains("string");
    assertThat(number(cursorSchema.get("maxLength")))
        .isEqualTo(SalesOrderFieldHistoryDtos.MAX_CURSOR_LENGTH);

    assertThat(responseRef(operation, "200"))
        .isEqualTo(REF + "ApiResponseSalesOrderFieldHistoryPage");
    Map<String, Object> envelope = schema("ApiResponseSalesOrderFieldHistoryPage");
    assertThat(refOf(asMap(asMap(envelope.get("properties")).get("data"))))
        .isEqualTo(REF + "SalesOrderFieldHistoryPage");
    for (String code : List.of("401", "403", "404", "422")) {
      assertThat(mapAt(operation, "responses", code)).containsKey("content");
    }
  }

  @Test
  @DisplayName("H25: closed schemas, required lists, nullability and the enums of the history")
  void schemas() {
    assertThat(required("SalesOrderFieldHistoryPage"))
        .containsExactlyInAnyOrder("items", "snapshotVersion", "hasMore", "nextCursor");
    assertThat(isNullable(property("SalesOrderFieldHistoryPage", "nextCursor"))).isTrue();
    assertThat(isNullable(property("SalesOrderFieldHistoryPage", "items"))).isFalse();

    assertThat(required("SalesOrderFieldHistoryEntry"))
        .containsExactlyInAnyOrder(
            "id",
            "operationId",
            "orderVersion",
            "changedAt",
            "actor",
            "lineId",
            "editKey",
            "knownEditKey",
            "changeKind",
            "oldValue",
            "newValue",
            "resolution",
            "resolutionScope");
    for (String nullable : List.of("lineId", "knownEditKey", "resolution", "resolutionScope")) {
      assertThat(isNullable(property("SalesOrderFieldHistoryEntry", nullable)))
          .as(nullable)
          .isTrue();
    }
    for (String strict : List.of("id", "operationId", "orderVersion", "changedAt", "editKey")) {
      assertThat(isNullable(property("SalesOrderFieldHistoryEntry", strict))).as(strict).isFalse();
    }
    assertThat(isNullable(property("SalesOrderFieldHistoryActor", "displayName"))).isTrue();
    assertThat(isNullable(property("SalesOrderFieldHistoryActor", "id"))).isFalse();

    assertThat(enumOf("SalesOrderFieldHistoryChangeKind"))
        .containsExactly("SET", "CLEAR", "LINE_ADDED", "LINE_REMOVED");
    assertThat(enumOf("SalesOrderFieldHistoryResolutionScope")).containsExactly("FIELD", "LINE");
    assertThat(enumOf("SalesOrderFieldHistoryValueState")).containsExactly("KNOWN", "UNAVAILABLE");
    assertThat(enumOf("SalesOrderFieldHistoryUnavailableReason"))
        .containsExactly("UNSUPPORTED_SHAPE", "REFERENCE_UNAVAILABLE");
    assertThat(refOf(property("SalesOrderFieldHistoryEntry", "resolution")))
        .isEqualTo(REF + "SalesOrderEditResolutionChoice");

    List<String> catalogue = new ArrayList<>();
    Arrays.stream(OrderEditKey.values()).map(OrderEditKey::wireName).forEach(catalogue::add);
    catalogue.add("line");
    assertThat(SalesOrderFieldHistoryDtos.EDIT_KEY_CATALOGUE).isEqualTo(catalogue);
    assertThat(enumOf("SalesOrderFieldHistoryEditKey"))
        .hasSize(23)
        .containsExactlyElementsOf(catalogue);
    assertThat(refOf(property("SalesOrderFieldHistoryEntry", "knownEditKey")))
        .isEqualTo(REF + "SalesOrderFieldHistoryEditKey");
    Map<String, Object> rawKey = property("SalesOrderFieldHistoryEntry", "editKey");
    assertThat(typesOf(rawKey)).containsExactly("string");
    assertThat(rawKey).doesNotContainKey("enum").doesNotContainKey("$ref");
    assertThat(number(rawKey.get("maxLength")))
        .isEqualTo(SalesOrderFieldHistoryDtos.MAX_EDIT_KEY_LENGTH);

    List<String> closed = new ArrayList<>(SHAPES);
    closed.addAll(
        List.of(
            "SalesOrderFieldHistoryPage",
            "SalesOrderFieldHistoryEntry",
            "SalesOrderFieldHistoryActor",
            "SalesOrderFieldHistoryValue",
            "SalesOrderFieldHistoryProfileReference"));
    closed.forEach(
        name -> assertThat(schema(name).get("additionalProperties")).as(name).isEqualTo(false));
    Map<String, Object> specs = property("SalesOrderFieldHistorySpecification", "moduleSpecs");
    assertThat(specs.get("additionalProperties")).isNotEqualTo(false);
  }

  @Test
  @DisplayName("H25: data is the anyOf of string, date, boolean, every shape and null; no oneOf")
  void dataShapes() {
    Map<String, Object> data = property("SalesOrderFieldHistoryValue", "data");
    assertThat(data).doesNotContainKey("oneOf").containsKey("anyOf");
    List<Map<String, Object>> arms =
        ((List<?>) data.get("anyOf")).stream().map(SalesOrderFieldHistoryOpenApiIT::asMap).toList();
    Set<String> refs = new LinkedHashSet<>();
    Set<String> types = new LinkedHashSet<>();
    boolean date = false;
    for (Map<String, Object> arm : arms) {
      if (arm.get("$ref") instanceof String ref) {
        refs.add(ref.substring(REF.length()));
      }
      types.addAll(typesOf(arm));
      date |= "date".equals(arm.get("format"));
    }
    assertThat(refs).containsExactlyInAnyOrderElementsOf(SHAPES);
    assertThat(types).contains("string", "boolean", "null");
    assertThat(date).isTrue();
    SHAPES.forEach(name -> assertThat(schema(name)).as(name).isNotEmpty());
  }

  @Test
  @DisplayName("H25/H11: decimals are patterned strings; digest and fingerprint are not published")
  void decimals() {
    Map<String, List<String>> decimals =
        Map.of(
            "SalesOrderFieldHistoryQuantity", List.of("requestedQty"),
            "SalesOrderFieldHistoryPricing", List.of("unitPrice", "discountAmount", "taxAmount"),
            "SalesOrderFieldHistoryTolerance", List.of("upPct", "downPct"),
            "SalesOrderFieldHistoryWidth", List.of("value"));
    decimals.forEach(
        (name, fields) ->
            fields.forEach(
                field -> {
                  Map<String, Object> property = property(name, field);
                  assertThat(typesOf(property)).as("%s.%s", name, field).contains("string");
                  assertThat(property.get("pattern"))
                      .as("%s.%s", name, field)
                      .isEqualTo(SalesOrderFieldHistoryDtos.DECIMAL_PATTERN);
                }));
    assertThat(asMap(schema("SalesOrderFieldHistoryLine").get("properties")))
        .doesNotContainKey("allocationDigest");
    assertThat(asMap(schema("SalesOrderFieldHistoryProfileReference").get("properties")).keySet())
        .containsExactlyInAnyOrder("profileId", "profileVersion");
  }

  @Test
  @DisplayName(
      "H25/R2: real HTTP answers (envelope and later page) pass the published schemas; null,"
          + " KNOWN, UNAVAILABLE, composite, whole line and an unknown stored key included")
  void realAnswersConform() throws Exception {
    UUID unknownEntry = fixture();

    JsonNode envelope = historyHttp(MAX_LIMIT, null);
    assertThat(validateResponse(envelope)).isEmpty();
    JsonNode page = envelope.path("data");
    assertThat(validateRef(REF + "SalesOrderFieldHistoryPage", page)).isEmpty();

    List<JsonNode> items = new ArrayList<>();
    page.path("items").forEach(items::add);
    assertThat(items).anySatisfy(item -> assertThat(isKnownNull(item.path("newValue"))).isTrue());
    assertThat(items)
        .anySatisfy(
            item ->
                assertThat(item.path("oldValue").path("state").asText()).isEqualTo("UNAVAILABLE"));
    assertThat(byKnownKey(items, "requestedDeliveryDate").path("newValue").path("data").isObject())
        .isTrue();
    assertThat(items)
        .anySatisfy(item -> assertThat(item.path("changeKind").asText()).isEqualTo("LINE_REMOVED"));
    assertThat(items)
        .anySatisfy(item -> assertThat(item.path("changeKind").asText()).isEqualTo("LINE_ADDED"));

    JsonNode unknown =
        items.stream()
            .filter(item -> unknownEntry.toString().equals(item.path("id").asText()))
            .findFirst()
            .orElseThrow();
    assertThat(unknown.path("editKey").asText()).isEqualTo("line.futureKey");
    assertThat(unknown.path("knownEditKey").isNull()).isTrue();
    assertThat(unknown.path("newValue").path("reason").asText()).isEqualTo("UNSUPPORTED_SHAPE");

    JsonNode first = historyHttp(2, null).path("data");
    assertThat(first.path("hasMore").asBoolean()).isTrue();
    JsonNode later = historyHttp(2, first.path("nextCursor").asText());
    assertThat(validateResponse(later)).isEmpty();
  }

  @Test
  @DisplayName(
      "H25/R2: the same answer is refused when a rule is broken: required, closed, bounds,"
          + " decimal, scalar type, format, enum")
  void brokenAnswersAreRefused() throws Exception {
    fixture();
    JsonNode page = historyHttp(MAX_LIMIT, null).path("data");
    String pageRef = REF + "SalesOrderFieldHistoryPage";
    assertThat(validateRef(pageRef, page)).isEmpty();

    assertRefused(pageRef, page, copy -> item(copy, 0).remove("operationId"));
    assertRefused(
        pageRef, page, copy -> ((ObjectNode) item(copy, 0).path("actor")).put("email", "x"));
    assertRefused(pageRef, page, copy -> copy.put("total", 5));
    assertRefused(pageRef, page, copy -> copy.put("snapshotVersion", -1));
    assertRefused(
        pageRef,
        page,
        copy -> {
          ArrayNode items = (ArrayNode) copy.path("items");
          JsonNode sample = items.get(0);
          while (items.size() <= MAX_LIMIT) {
            items.add(sample.deepCopy());
          }
        });
    assertRefused(
        pageRef,
        page,
        copy ->
            ((ObjectNode)
                    entryOf(copy, "line.specification")
                        .path("newValue")
                        .path("data")
                        .path("requirementProfile"))
                .put("profileVersion", 0));
    assertRefused(
        pageRef,
        page,
        copy ->
            ((ObjectNode) entryOf(copy, "line.pricing").path("newValue").path("data"))
                .put("unitPrice", "6.8e1"));
    assertRefused(
        pageRef,
        page,
        copy ->
            ((ObjectNode) entryOf(copy, "line.pricing").path("newValue").path("data"))
                .put("unitPrice", "06.80"));
    assertRefused(pageRef, page, copy -> item(copy, 0).put("orderVersion", "seven"));
    assertRefused(pageRef, page, copy -> item(copy, 0).put("changedAt", "yesterday"));
    assertRefused(pageRef, page, copy -> item(copy, 0).put("knownEditKey", "line.futureKey"));
    assertRefused(pageRef, page, copy -> item(copy, 0).put("editKey", "k".repeat(41)));
    assertRefused(
        pageRef, page, copy -> ((ObjectNode) item(copy, 0).path("newValue")).put("state", "MAYBE"));
  }

  // ── fixture and HTTP ──────────────────────────────────────────────────────

  /**
   * Saves that cover every value kind, then one stored row of a key this version does not know (as
   * a later version or an old one could have written it). Returns that row's id.
   */
  private UUID fixture() {
    saved(
        actorA,
        withLines(
            body(
                UUID.randomUUID(),
                open(actorA).baseId(),
                "notes",
                set("<b>Urgent</b>"),
                "paymentTerms",
                clear(),
                "requestedDeliveryDate",
                set("2026-11-15")),
            List.of(
                update(l2, "pricing", set(pricing("GBP", "6.80"))),
                remove(l1),
                add(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "quantity",
                    set(quantity("12.5", "M"))))));
    saved(
        actorB,
        withLines(
            body(UUID.randomUUID(), open(actorB).baseId()),
            List.of(update(l2, "specification", set(specification())))));
    UUID unknown = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO sales_ord.order_field_change (id, tenant_id, created_at, updated_at,"
            + " sales_order_id, operation_id, operation_receipt_id, line_id, edit_key,"
            + " change_kind, old_value, new_value, actor_id, order_version, changed_at)"
            + " VALUES (?, ?, now(), now(), ?, ?, ?, ?, 'line.futureKey', 'SET', NULL,"
            + " '{\"a\":1}'::jsonb, ?, ?, now())",
        unknown,
        tenantId,
        orderId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        l2,
        actorA.id(),
        orderVersion());
    return unknown;
  }

  private JsonNode historyHttp(Integer limit, String cursor) throws Exception {
    StringBuilder query = new StringBuilder("?limit=").append(limit);
    if (cursor != null) {
      query.append("&cursor=").append(URLEncoder.encode(cursor, StandardCharsets.UTF_8));
    }
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + port
                        + "/api/v1/sales/orders/"
                        + orderId
                        + "/field-history"
                        + query))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer " + token(actorC))
            .GET()
            .build();
    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return objectMapper.readTree(response.body());
  }

  private Map<String, Object> specification() {
    Map<String, Object> basis =
        pairs(
            "kind",
            "LINE_EXPLICIT",
            "actorId",
            actorA.id(),
            "decidedAt",
            "2026-10-01T10:00:00Z",
            "decisionReference",
            "openapi-check");
    Map<String, Object> profile =
        pairs(
            "basis",
            basis,
            "scopeVersion",
            "client-scope",
            "resolutionRuleVersion",
            "client-rule",
            "scope",
            List.of(),
            "facets",
            List.of(),
            "unmodelledConstraints",
            List.of(),
            "deviations",
            List.of());
    return pairs("moduleSpecs", Map.of("gsm", 180), "requirementProfile", profile);
  }

  // ── JSON Schema 2020-12 validation against the published document ─────────

  /** The answer against the published 200 response schema of the operation. */
  private Set<ValidationMessage> validateResponse(JsonNode answer) {
    JsonNode content =
        documentTree
            .path("paths")
            .path(HISTORY_PATH)
            .path("get")
            .path("responses")
            .path("200")
            .path("content");
    assertThat(content.size()).isEqualTo(1);
    JsonNode schema = content.elements().next().path("schema");
    assertThat(schema.path("$ref").asText())
        .isEqualTo(REF + "ApiResponseSalesOrderFieldHistoryPage");
    return validateRef(schema.path("$ref").asText(), answer);
  }

  /** Against one component schema; references resolve within the published document. */
  private Set<ValidationMessage> validateRef(String ref, JsonNode instance) {
    ObjectNode root = documentTree.deepCopy();
    root.put("$ref", ref);
    SchemaValidatorsConfig config =
        SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
    JsonSchema schema =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(root, config);
    return schema.validate(instance);
  }

  private void assertRefused(String ref, JsonNode valid, Consumer<ObjectNode> breaking) {
    ObjectNode copy = valid.deepCopy();
    breaking.accept(copy);
    assertThat(copy).as("the mutation changed the answer").isNotEqualTo(valid);
    assertThat(validateRef(ref, copy)).as("refused: %s", copy).isNotEmpty();
  }

  private static ObjectNode item(ObjectNode page, int index) {
    return (ObjectNode) page.path("items").get(index);
  }

  private static JsonNode entryOf(ObjectNode page, String knownKey) {
    for (JsonNode item : page.path("items")) {
      if (knownKey.equals(item.path("knownEditKey").asText())
          && item.path("newValue").path("data").isObject()) {
        return item;
      }
    }
    throw new AssertionError("No entry of " + knownKey);
  }

  private static JsonNode byKnownKey(List<JsonNode> items, String knownKey) {
    return items.stream()
        .filter(item -> knownKey.equals(item.path("knownEditKey").asText()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No entry of " + knownKey));
  }

  private static boolean isKnownNull(JsonNode value) {
    return "KNOWN".equals(value.path("state").asText()) && value.path("data").isNull();
  }

  // ── document helpers ──────────────────────────────────────────────────────

  private Map<String, Object> schema(String name) {
    return mapAt(document, "components", "schemas", name);
  }

  private Map<String, Object> property(String schemaName, String propertyName) {
    Object property = asMap(schema(schemaName).get("properties")).get(propertyName);
    assertThat(property).as("%s.%s", schemaName, propertyName).isNotNull();
    return asMap(property);
  }

  private List<String> required(String schemaName) {
    Object required = schema(schemaName).get("required");
    assertThat(required).as("%s.required", schemaName).isInstanceOf(List.class);
    return ((List<?>) required).stream().map(String::valueOf).toList();
  }

  private List<String> enumOf(String name) {
    Object values = schema(name).get("enum");
    assertThat(values).as("%s.enum", name).isInstanceOf(List.class);
    return ((List<?>) values).stream().map(String::valueOf).toList();
  }

  private static Map<String, Object> parameter(Map<String, Object> operation, String name) {
    return ((List<?>) operation.get("parameters"))
        .stream()
            .map(SalesOrderFieldHistoryOpenApiIT::asMap)
            .filter(parameter -> name.equals(parameter.get("name")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No parameter " + name));
  }

  private static String responseRef(Map<String, Object> operation, String status) {
    Map<String, Object> content = mapAt(operation, "responses", status, "content");
    return content.values().stream()
        .map(media -> asMap(media).get("schema"))
        .filter(Map.class::isInstance)
        .map(schema -> refOf(asMap(schema)))
        .filter(ref -> ref != null)
        .findFirst()
        .orElseThrow(() -> new AssertionError("No schema for " + status));
  }

  private static String refOf(Map<String, Object> property) {
    if (property.get("$ref") instanceof String ref) {
      return ref;
    }
    for (String keyword : List.of("allOf", "anyOf", "oneOf")) {
      if (property.get(keyword) instanceof List<?> arms) {
        for (Object arm : arms) {
          if (arm instanceof Map<?, ?> map && map.get("$ref") instanceof String ref) {
            return ref;
          }
        }
      }
    }
    return null;
  }

  private static boolean isNullable(Map<String, Object> property) {
    if (typesOf(property).contains("null") || Boolean.TRUE.equals(property.get("nullable"))) {
      return true;
    }
    for (String keyword : List.of("anyOf", "oneOf")) {
      if (property.get(keyword) instanceof List<?> arms) {
        for (Object arm : arms) {
          if (arm instanceof Map<?, ?> map && typesOf(asMap(map)).contains("null")) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static Set<String> typesOf(Map<String, Object> schema) {
    Object type = schema.get("type");
    Set<String> types = new LinkedHashSet<>();
    if (type instanceof String single) {
      types.add(single);
    } else if (type instanceof List<?> many) {
      many.forEach(value -> types.add(String.valueOf(value)));
    }
    if (Boolean.TRUE.equals(schema.get("nullable"))) {
      types.add("null");
    }
    return types;
  }

  private static int number(Object value) {
    return Integer.parseInt(String.valueOf(value));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object node) {
    assertThat(node).isInstanceOf(Map.class);
    return (Map<String, Object>) node;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapAt(Map<String, Object> root, String... path) {
    Object current = root;
    for (String segment : path) {
      assertThat(current).as("OpenAPI map before %s", segment).isInstanceOf(Map.class);
      current = ((Map<?, ?>) current).get(segment);
      assertThat(current).as("OpenAPI value at %s", segment).isNotNull();
    }
    assertThat(current).isInstanceOf(Map.class);
    return (Map<String, Object>) current;
  }
}
