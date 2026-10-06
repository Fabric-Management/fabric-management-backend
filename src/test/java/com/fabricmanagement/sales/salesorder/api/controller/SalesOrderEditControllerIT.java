package com.fabricmanagement.sales.salesorder.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditItSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The three safe-edit endpoints through real HTTP, Jackson and the error envelope (CEDIT-03 §7):
 * every refused body leaves the database as it was. Also the create and legacy-update regression of
 * the now strict requirement profile (S15.16–S15.19).
 */
class SalesOrderEditControllerIT extends SalesOrderEditItSupport {

  private static final String BASES = "/api/v1/sales/orders/{orderId}/edit-bases";
  private static final String SAVES = "/api/v1/sales/orders/{orderId}/edit-operations";

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("Base, save and receipt: 201 and 200 in the ApiResponse envelope")
  void baseSaveAndReceipt() throws Exception {
    JsonNode base = data(perform(actorB, post(BASES, orderId)).andExpect(status().isCreated()));
    assertThat(base.path("orderId").asText()).isEqualTo(orderId.toString());
    assertThat(base.path("order").path("lines")).hasSize(2);
    assertThat(base.path("orderVersion").asLong())
        .isEqualTo(base.path("order").path("version").asLong());
    UUID operationId = UUID.randomUUID();

    JsonNode result =
        data(
            saveRequest(
                    actorB,
                    json(
                        body(
                            operationId,
                            UUID.fromString(base.path("baseId").asText()),
                            "notes",
                            set("Urgent"))))
                .andExpect(status().isOk()));
    assertThat(result.path("outcome").asText()).isEqualTo("APPLIED");
    assertThat(result.path("replayed").asBoolean()).isFalse();
    assertThat(result.path("resultVersion").asLong()).isEqualTo(orderVersion());
    assertThat(result.path("nextBase").path("order").path("notes").asText()).isEqualTo("Urgent");

    JsonNode receipt =
        data(
            perform(actorB, get(SAVES + "/{operationId}", orderId, operationId))
                .andExpect(status().isOk()));
    assertThat(receipt.path("outcome").asText()).isEqualTo("APPLIED");
    assertThat(receipt.path("resultVersion").asLong()).isEqualTo(orderVersion());
    assertThat(receipt.path("conflictBaseId").isNull()).isTrue();
    perform(actorA, get(SAVES + "/{operationId}", orderId, operationId))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("S2.4: a conflict is a typed problem+json 409 with the new base")
  void conflictIsATypedProblem() throws Exception {
    UUID baseB = open(actorB).baseId();
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "paymentTerms", set("45 days")));
    UUID operationId = UUID.randomUUID();
    String request = json(body(operationId, baseB, "paymentTerms", clear()));

    MvcResult first =
        saveRequest(actorB, request)
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("EDIT_CONFLICT"))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.operationId").value(operationId.toString()))
            .andExpect(jsonPath("$.baseId").value(baseB.toString()))
            .andExpect(jsonPath("$.instance").value(SAVES.replace("{orderId}", orderId.toString())))
            .andExpect(jsonPath("$.conflicts[0].key").value("paymentTerms"))
            .andExpect(jsonPath("$.conflicts[0].base").value("30 days"))
            .andExpect(jsonPath("$.conflicts[0].current").value("45 days"))
            .andExpect(jsonPath("$.conflicts[0].mine").isEmpty())
            .andExpect(jsonPath("$.currentBase.baseId").exists())
            .andReturn();

    // S10.12 over HTTP: the repeat answers the same body.
    MvcResult repeat = saveRequest(actorB, request).andExpect(status().isConflict()).andReturn();
    assertThat(objectMapper.readTree(repeat.getResponse().getContentAsString()))
        .isEqualTo(objectMapper.readTree(first.getResponse().getContentAsString()));
    assertThat(receipts("CONFLICT")).isEqualTo(1);
  }

  @Test
  @DisplayName("S11.1: an unknown base is a plain 409 problem, not EDIT_CONFLICT")
  void unknownBaseIsAPlainProblem() throws Exception {
    saveRequest(actorB, json(body(UUID.randomUUID(), UUID.randomUUID(), "notes", set("Urgent"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EDIT_BASE_UNKNOWN"))
        .andExpect(jsonPath("$.conflicts").doesNotExist());
    assertUnchanged();
  }

  static Stream<Arguments> unreadableBodies() {
    String op = "\"operationId\":\"" + UUID.randomUUID() + "\"";
    String notes = "\"notes\":{\"operation\":\"SET\",\"value\":\"Urgent\"}";
    return Stream.of(
        Arguments.of("S2.8 explicit null key", "{" + op + ",BASE,\"header\":{\"notes\":null}}"),
        Arguments.of(
            "S2.9 unknown header key",
            "{"
                + op
                + ",BASE,\"header\":{"
                + notes
                + ",\"discountNote\":{\"operation\":\"SET\",\"value\":\"x\"}}}"),
        Arguments.of(
            "S15.9 unknown operation",
            "{" + op + ",BASE,\"header\":{\"notes\":{\"operation\":\"MERGE\",\"value\":\"x\"}}}"),
        Arguments.of(
            "S15.1 unknown root property",
            "{" + op + ",BASE,\"force\":true,\"header\":{" + notes + "}}"),
        Arguments.of(
            "S11.2 client base object",
            "{" + op + ",BASE,\"base\":{\"paymentTerms\":\"x\"},\"header\":{" + notes + "}}"),
        Arguments.of(
            "S15.3 unknown instruction property",
            "{"
                + op
                + ",BASE,\"header\":{\"notes\":{\"operation\":\"SET\",\"value\":\"Urgent\",\"previous\":null}}}"),
        Arguments.of(
            "S15.4 unknown value property",
            "{"
                + op
                + ",BASE,\"header\":{"
                + notes
                + "},\"lines\":[{\"operation\":\"UPDATE\",\"lineId\":\"L1\",\"fields\":{\"pricing\":{\"operation\":\"SET\",\"value\":{\"currency\":\"GBP\",\"unitPrice\":4,\"vat\":1}}}}]}"),
        Arguments.of(
            "S15.5 unknown line operation property",
            "{"
                + op
                + ",BASE,\"header\":{"
                + notes
                + "},\"lines\":[{\"operation\":\"UPDATE\",\"lineId\":\"L1\",\"position\":2,\"fields\":{}}]}"),
        Arguments.of(
            "S6.9 product id in line fields",
            "{"
                + op
                + ",BASE,\"header\":{"
                + notes
                + "},\"lines\":[{\"operation\":\"UPDATE\",\"lineId\":\"L1\",\"fields\":{\"productId\":\"L1\"}}]}"),
        Arguments.of(
            "S15.7 unknown resolution property",
            "{"
                + op
                + ",BASE,\"header\":{"
                + notes
                + "},\"resolutions\":[{\"key\":\"paymentTerms\",\"choice\":\"USE_MINE\",\"force\":true}]}"),
        Arguments.of(
            // A complete basis: only the unknown property can make this unreadable.
            "S15.8 unknown profile basis property",
            "{"
                + op
                + ",BASE,\"header\":{"
                + notes
                + "},\"lines\":[{\"operation\":\"UPDATE\",\"lineId\":\"L1\",\"fields\":{\"specification\":{\"operation\":\"SET\",\"value\":{\"moduleSpecs\":{},\"requirementProfile\":{\"basis\":{\"kind\":\"LINE_EXPLICIT\",\"actorId\":\"00000000-0000-0000-0000-0000000000a1\",\"decidedAt\":\"2026-10-01T10:00:00Z\",\"decisionReference\":\"line-1\",\"extra\":1},\"scopeVersion\":\"s\",\"resolutionRuleVersion\":\"r\"}}}}}]}"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unreadableBodies")
  @DisplayName("S15: an unreadable body at any level is 400 and writes nothing")
  void unreadableBodyIsBadRequest(String scenario, String template) throws Exception {
    UUID base = open(actorB).baseId();
    String request =
        template.replace("BASE", "\"baseId\":\"" + base + "\"").replace("\"L1\"", "\"" + l1 + "\"");

    saveRequest(actorB, request)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

    assertUnchanged();
  }

  static Stream<Arguments> refusedInstructions() {
    return Stream.of(
        Arguments.of("S2.3", "orderDate", clear(), "REQUIRED_FIELD_CANNOT_BE_CLEARED"),
        Arguments.of("S2.5", "notes", set("  "), "BLANK_VALUE_USE_CLEAR"),
        Arguments.of("S2.6", "notes", Map.of("operation", "SET"), "SET_REQUIRES_VALUE"),
        Arguments.of(
            "S2.7", "notes", Map.of("operation", "CLEAR", "value", "x"), "CLEAR_TAKES_NO_VALUE"),
        Arguments.of(
            "S4.6",
            "contact",
            set(Map.of("name", "Jane Hill", "whatsapp", true)),
            "CONTACT_WHATSAPP_NEEDS_PHONE"),
        // CEDIT-02 §4.2 over HTTP: an empty composite is blank before any part is missing.
        Arguments.of("§4.2 empty", "deliveryTerms", set(Map.of()), "BLANK_VALUE_USE_CLEAR"),
        Arguments.of("§4.2 empty", "agreementContext", set(Map.of()), "BLANK_VALUE_USE_CLEAR"),
        Arguments.of("§4.2 empty", "contact", set(Map.of()), "BLANK_VALUE_USE_CLEAR"),
        Arguments.of(
            "§4.2 part missing", "deliveryTerms", set(Map.of("term", "FCA")), "VALIDATION_ERROR"),
        Arguments.of(
            "§4.2 CLEAR with a value",
            "deliveryTerms",
            Map.of("operation", "CLEAR", "value", Map.of()),
            "CLEAR_TAKES_NO_VALUE"));
  }

  @ParameterizedTest(name = "{0} {3}")
  @MethodSource("refusedInstructions")
  @DisplayName("S2/S4.6: a refused header instruction is 422 with its key and writes nothing")
  void refusedHeaderInstruction(String scenario, String key, Object instruction, String code)
      throws Exception {
    UUID base = open(actorB).baseId();

    saveRequest(actorB, json(body(UUID.randomUUID(), base, key, instruction)))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.key").value(key));

    assertUnchanged();
  }

  static Stream<Arguments> textLimits() {
    return Stream.of(
        Arguments.of("customerReference", 100),
        Arguments.of("paymentTerms", 200),
        Arguments.of("shippingAddress", 500),
        Arguments.of("billingAddress", 500),
        Arguments.of("shippingMethod", 50));
  }

  @ParameterizedTest(name = "{0} at most {1}")
  @MethodSource("textLimits")
  @DisplayName(
      "S2.9 (R3): one character over a text limit is 422 VALIDATION_ERROR with the field path in"
          + " errors, written once; the limit itself saves")
  void textLimitOverHttp(String key, int limit) throws Exception {
    UUID base = open(actorB).baseId();

    MvcResult refused =
        saveRequest(actorB, json(body(UUID.randomUUID(), base, key, set("x".repeat(limit + 1)))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.key").value(key))
            .andExpect(jsonPath("$.errors['header." + key + ".value']").exists())
            .andReturn();
    String text = refused.getResponse().getContentAsString();
    assertThat(text.split("\"errors\"", -1)).as("one errors key in %s", text).hasSize(2);
    assertUnchanged();

    saveRequest(actorB, json(body(UUID.randomUUID(), base, key, set("x".repeat(limit)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.outcome").value("APPLIED"));
  }

  @Test
  @DisplayName("S2.10, S6.6–S6.8, S12.2: refused line instructions name the line")
  void refusedLineInstructions() throws Exception {
    UUID base = open(actorB).baseId();
    UUID client = UUID.randomUUID();

    saveRequest(
            actorB,
            json(
                withLines(body(UUID.randomUUID(), base), List.of(update(l1, "quantity", clear())))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("REQUIRED_FIELD_CANNOT_BE_CLEARED"))
        .andExpect(jsonPath("$.lineId").value(l1.toString()));
    saveRequest(
            actorB,
            json(
                withLines(
                    body(UUID.randomUUID(), base),
                    List.of(update(l1, "singleLotRequired", clear())))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("REQUIRED_FIELD_CANNOT_BE_CLEARED"));
    saveRequest(
            actorB,
            json(
                withLines(
                    body(UUID.randomUUID(), base),
                    List.of(
                        add(client, p1, "quantity", set(quantity("10", "M"))),
                        add(client, p2, "quantity", set(quantity("10", "M")))))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("CLIENT_LINE_ID_DUPLICATED"))
        .andExpect(jsonPath("$.clientLineId").value(client.toString()));
    saveRequest(
            actorB,
            json(
                withLines(
                    body(UUID.randomUUID(), base),
                    List.of(update(l1, "notes", set("x")), remove(l1)))))
        .andExpect(status().isBadRequest());
    saveRequest(
            actorB,
            json(
                withLines(
                    body(UUID.randomUUID(), base),
                    List.of(update(l1, "pricing", set(pricing("GBP", "4.10"))), remove(l1)))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("LINE_OPERATION_DUPLICATED"))
        .andExpect(jsonPath("$.lineId").value(l1.toString()));
    saveRequest(
            actorB,
            json(
                withLines(
                    body(UUID.randomUUID(), base),
                    List.of(
                        add(
                            client,
                            p1,
                            "quantity",
                            set(quantity("10", "M")),
                            "pricing",
                            clear())))))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("CLEAR_NOT_ALLOWED_ON_ADD"));

    assertUnchanged();
  }

  @Test
  @DisplayName("An operation id is required: missing fields are validation errors")
  void missingOperationIdIsRefused() throws Exception {
    UUID base = open(actorB).baseId();
    Map<String, Object> request = body(null, base, "notes", set("Urgent"));
    request.remove("operationId");

    saveRequest(actorB, json(request)).andExpect(status().is4xxClientError());

    assertUnchanged();
  }

  @Test
  @DisplayName("S15.16, S15.18: create and legacy update still accept valid profiles verbatim")
  void createAndLegacyUpdateKeepValidProfiles() throws Exception {
    Map<String, Object> profile = profileInput(Map.of());
    MvcResult created =
        perform(
                actorB,
                post("/api/v1/sales/orders")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(createRequest(profile))))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode order = objectMapper.readTree(created.getResponse().getContentAsString()).path("data");
    String newOrder = order.path("id").asText();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_ord.requirement_profile_version v"
                    + " JOIN sales_ord.sales_order_line l ON l.id = v.sales_order_line_id"
                    + " WHERE l.sales_order_id = ?::uuid",
                Integer.class,
                newOrder))
        .isEqualTo(1);

    // S15.18: a free source value is kept exactly.
    Map<String, Object> constraint =
        pairs(
            "field",
            "handFeel",
            "status",
            "RESOLVED_UNSUPPORTED",
            "sourceValue",
            Map.of("anyKey", List.of(1, Map.of("nested", true))),
            "meaning",
            "as sampled",
            "reason",
            "not modelled yet");
    JsonNode line = order.path("lines").get(0);
    Map<String, Object> lineUpdate =
        pairs(
            "id",
            line.path("id").asText(),
            "productId",
            line.path("productId").asText(),
            "requestedQty",
            100,
            "unit",
            "M",
            "requirementProfile",
            profileInput(Map.of("unmodelledConstraints", List.of(constraint))));
    Map<String, Object> update =
        pairs(
            "version",
            order.path("version").asLong(),
            "orderDate",
            ORDER_DATE.toString(),
            "lines",
            List.of(lineUpdate));
    perform(
            actorB,
            put("/api/v1/sales/orders/{id}", newOrder)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(update)))
        .andExpect(status().isOk());
    String stored =
        jdbc.queryForObject(
            "SELECT requirement_profile_snapshot::text FROM sales_ord.sales_order_line"
                + " WHERE id = ?::uuid",
            String.class,
            line.path("id").asText());
    assertThat(
            objectMapper.readTree(stored).path("unmodelledConstraints").get(0).path("sourceValue"))
        .isEqualTo(objectMapper.valueToTree(constraint.get("sourceValue")));
  }

  @Test
  @DisplayName("S15.17, S15.19: create refuses unknown profile properties and writes nothing")
  void createRefusesUnknownProfileProperties() throws Exception {
    int ordersBefore = ordersOfTenant();
    Map<String, Object> facet =
        pairs("kind", "WIDTH", "qualifier", null, "state", "UNSPECIFIED", "extra", 1);
    Map<String, Object> constraint =
        pairs("field", "handFeel", "status", "NOT_APPLICABLE", "origin", "sample");

    for (Map<String, Object> profile :
        List.of(
            profileInput(Map.of("facets", List.of(facet))),
            profileInput(Map.of("unmodelledConstraints", List.of(constraint))))) {
      perform(
              actorB,
              post("/api/v1/sales/orders")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json(createRequest(profile))))
          .andExpect(status().isBadRequest());
    }

    assertThat(ordersOfTenant()).isEqualTo(ordersBefore);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private ResultActions saveRequest(Actor actor, String request) throws Exception {
    return perform(
        actor,
        post(SAVES, orderId).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(request));
  }

  private ResultActions perform(Actor actor, MockHttpServletRequestBuilder request)
      throws Exception {
    TenantContext.setCurrentTenantId(actor.tenantId());
    TenantContext.setCurrentUserId(actor.id());
    try {
      return mockMvc.perform(request.with(authentication(actor.authentication())));
    } finally {
      TenantContext.clear();
    }
  }

  private JsonNode data(ResultActions actions) throws Exception {
    return objectMapper
        .readTree(actions.andReturn().getResponse().getContentAsString())
        .path("data");
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }

  /** Nothing of the order changed and no receipt or history was written. */
  private void assertUnchanged() {
    assertThat(orderText("notes")).isNull();
    assertThat(orderText("payment_terms")).isEqualTo("30 days");
    assertThat(receipts()).isZero();
    assertThat(historyRows()).isZero();
    assertThat(activeLines()).isEqualTo(2);
  }

  private int ordersOfTenant() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.sales_order WHERE tenant_id = ?", Integer.class, tenantId);
  }

  private Map<String, Object> createRequest(Map<String, Object> profile) {
    Map<String, Object> line =
        pairs(
            "productId",
            UUID.randomUUID(),
            "requestedQty",
            100,
            "unit",
            "M",
            "requirementProfile",
            profile);
    return pairs(
        "partnerId", partnerId, "orderDate", ORDER_DATE.toString(), "lines", List.of(line));
  }

  /** A minimal valid line-explicit profile input, with the given parts replaced. */
  private Map<String, Object> profileInput(Map<String, Object> overrides) {
    Map<String, Object> profile =
        pairs(
            "basis",
            pairs(
                "kind",
                "LINE_EXPLICIT",
                "actorId",
                actorB.id(),
                "decidedAt",
                "2026-10-01T10:00:00Z",
                "decisionReference",
                "order-line"),
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
    profile.putAll(overrides);
    return profile;
  }
}
