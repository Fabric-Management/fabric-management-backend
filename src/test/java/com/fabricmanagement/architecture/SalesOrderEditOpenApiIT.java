package com.fabricmanagement.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.sales.salesorder.app.SalesOrderEditItSupport;
import com.fabricmanagement.sales.salesorder.app.SalesOrderEditProperties;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacet;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacetValue;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSamples;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * The published contract of the safe edit (CEDIT-02 §4.1, §4.6; CEDIT-03 §7): operation ids and
 * envelopes, the typed 409, schema names, closed request schemas with free {@code moduleSpecs} and
 * {@code sourceValue}, enums, required lists, nullability and the line-operation limit.
 *
 * <p>The document is read from the running application, as {@link OpenApiExportIT} reads it, but
 * through MockMvc inside the safe-edit test context: springdoc's {@code /api-docs.yaml} is an
 * ordinary MVC endpoint that the test profile enables and the security configuration permits, so no
 * server port is needed and the context and container of the other safe-edit ITs are reused. The
 * document is OpenAPI 3.1: a nullable property is a type array with {@code "null"}, or {@code
 * anyOf} with a null arm for a reference ({@code OpenApi31ValidationCustomizer}); both count.
 */
class SalesOrderEditOpenApiIT extends SalesOrderEditItSupport {

  private static final String REF = "#/components/schemas/";
  private static final String ORDER_PATH = "/api/v1/sales/orders/{orderId}";
  private static final String BASES_PATH = ORDER_PATH + "/edit-bases";
  private static final String OPERATIONS_PATH = ORDER_PATH + "/edit-operations";
  private static final String OPERATION_PATH = OPERATIONS_PATH + "/{operationId}";
  private static final String PROBLEM_JSON = "application/problem+json";

  /** Field instructions with their value schema; null where the value is a plain JSON type. */
  private static final Map<String, String> FIELD_EDITS = fieldEdits();

  /** Request-side schemas that must refuse unknown properties (S15.1–S15.8, S15.15). */
  private static final List<String> CLOSED_SCHEMAS =
      List.of(
          "SalesOrderEditRequest",
          "SalesOrderHeaderEdits",
          "SalesOrderLineEdit",
          "SalesOrderLineFieldEdits",
          "SalesOrderEditResolution",
          "SalesOrderTextFieldEdit",
          "SalesOrderDateFieldEdit",
          "SalesOrderIdFieldEdit",
          "SalesOrderFlagFieldEdit",
          "SalesOrderLineShipmentPreferenceEdit",
          "SalesOrderDeliveryTermsEdit",
          "SalesOrderDeliveryTermsValue",
          "SalesOrderAgreementContextEdit",
          "SalesOrderAgreementContextValue",
          "SalesOrderContactEdit",
          "SalesOrderContactValue",
          "SalesOrderLineQuantityEdit",
          "SalesOrderLineQuantityValue",
          "SalesOrderLinePricingEdit",
          "SalesOrderLinePricingValue",
          "SalesOrderLineToleranceEdit",
          "SalesOrderLineToleranceValue",
          "SalesOrderLineWidthEdit",
          "SalesOrderLineWidthValue",
          "SalesOrderLineSpecificationEdit",
          "SalesOrderLineSpecificationValue",
          "RequirementProfileInput",
          "RequirementProfileBasis",
          "RequirementFacet",
          "DecisionBasis",
          "RequirementDeviation",
          "UnmodelledSpecConstraint");

  @Autowired private MockMvc mockMvc;
  @Autowired private SalesOrderEditProperties editProperties;

  private Map<String, Object> document;

  @BeforeEach
  void readDocument() throws Exception {
    String yaml =
        mockMvc
            .perform(get("/api-docs.yaml"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(yaml).contains("openapi: 3.1");
    document = new YAMLMapper().readValue(yaml, new TypeReference<Map<String, Object>>() {});
    assertThat(document.get("openapi")).isEqualTo("3.1.0");
  }

  @Test
  @DisplayName("S8.1/S10.5/S11.1: the three operations publish their paths, methods and envelopes")
  void operationsAndEnvelopes() {
    Map<String, Object> openBase = mapAt(document, "paths", BASES_PATH, "post");
    assertThat(openBase).containsEntry("operationId", "createSalesOrderEditBase");
    assertThat(openBase).doesNotContainKey("requestBody");
    assertThat(responseRefs(openBase, "201")).contains(REF + "ApiResponseSalesOrderEditBase");
    assertThat(pathParameters(openBase)).containsExactly("orderId");

    Map<String, Object> save = mapAt(document, "paths", OPERATIONS_PATH, "post");
    assertThat(save).containsEntry("operationId", "saveSalesOrderEdit");
    Map<String, Object> requestBody = mapAt(save, "requestBody");
    assertThat(requestBody).containsEntry("required", true);
    assertThat(mapAt(requestBody, "content", "application/json", "schema"))
        .containsEntry("$ref", REF + "SalesOrderEditRequest");
    assertThat(responseRefs(save, "200")).contains(REF + "ApiResponseSalesOrderEditResult");
    assertThat(mapAt(save, "responses")).containsKeys("400", "403", "404", "409", "422");
    assertThat(pathParameters(save)).containsExactly("orderId");

    Map<String, Object> read = mapAt(document, "paths", OPERATION_PATH, "get");
    assertThat(read).containsEntry("operationId", "getSalesOrderEditOperation");
    assertThat(responseRefs(read, "200")).contains(REF + "ApiResponseSalesOrderEditOperationView");
    assertThat(pathParameters(read)).containsExactlyInAnyOrder("orderId", "operationId");

    for (Map<String, Object> operation : List.of(openBase, save, read)) {
      assertThat(operation.get("tags"))
          .asInstanceOf(InstanceOfAssertFactories.LIST)
          .contains("Sales Order Safe Edit");
      for (Object parameter : (List<?>) operation.get("parameters")) {
        Map<String, Object> path = asMap(parameter);
        if ("path".equals(path.get("in"))) {
          assertThat(path).containsEntry("required", true);
          assertThat(asMap(path.get("schema"))).containsEntry("format", "uuid");
        }
      }
    }

    assertThat(refOf(property("ApiResponseSalesOrderEditBase", "data")))
        .isEqualTo(REF + "SalesOrderEditBase");
    assertThat(refOf(property("ApiResponseSalesOrderEditResult", "data")))
        .isEqualTo(REF + "SalesOrderEditResult");
    assertThat(refOf(property("ApiResponseSalesOrderEditOperationView", "data")))
        .isEqualTo(REF + "SalesOrderEditOperationView");
    assertThat(refOf(property("SalesOrderEditResult", "nextBase")))
        .isEqualTo(REF + "SalesOrderEditBase");
    assertThat(refOf(asMap(property("SalesOrderEditResult", "lineIds").get("items"))))
        .isEqualTo(REF + "SalesOrderEditLineIdMapping");
    assertThat(refOf(asMap(property("SalesOrderEditOperationView", "lineIds").get("items"))))
        .isEqualTo(REF + "SalesOrderEditLineIdMapping");
    assertThat(refOf(property("SalesOrderEditBase", "order"))).isEqualTo(REF + "SalesOrderDto");
  }

  @Test
  @DisplayName("S8.1/S11.1: only the save's 409 is the typed SalesOrderEditConflictProblem")
  void typedConflictProblem() {
    Map<String, Object> conflict =
        mapAt(document, "paths", OPERATIONS_PATH, "post", "responses", "409", "content");
    assertThat(mapAt(conflict, PROBLEM_JSON, "schema"))
        .containsEntry("$ref", REF + "SalesOrderEditConflictProblem");

    // Opening a base answers plain problems for its 409s (ORDER_WITH_PLANNING and the like).
    Map<String, Object> openConflict =
        mapAt(document, "paths", BASES_PATH, "post", "responses", "409", "content");
    assertThat(mapAt(openConflict, PROBLEM_JSON, "schema"))
        .containsEntry("$ref", REF + "ApiProblemDetail");

    Map<String, Object> problem = properties("SalesOrderEditConflictProblem");
    assertThat(problem).containsKeys("code", "operationId", "baseId", "currentBase", "conflicts");
    assertThat(asMap(problem.get("operationId"))).containsEntry("format", "uuid");
    assertThat(asMap(problem.get("baseId"))).containsEntry("format", "uuid");
    assertThat(refOf(asMap(problem.get("currentBase")))).isEqualTo(REF + "SalesOrderEditBase");
    assertThat(refOf(asMap(asMap(problem.get("conflicts")).get("items"))))
        .isEqualTo(REF + "SalesOrderEditConflict");

    Map<String, Object> item = properties("SalesOrderEditConflict");
    assertThat(item)
        .containsKeys(
            "key", "lineId", "clientLineId", "reason", "base", "current", "mine", "choices");
    assertThat(refOf(asMap(item.get("reason")))).isEqualTo(REF + "SalesOrderEditConflictReason");
    assertThat(refOf(asMap(asMap(item.get("choices")).get("items"))))
        .isEqualTo(REF + "SalesOrderEditResolutionChoice");
  }

  @Test
  @DisplayName("CEDIT-02 §4.6: every contract schema name is published, the profile input reused")
  void contractSchemaNames() {
    Map<String, Object> schemas = mapAt(document, "components", "schemas");
    assertThat(schemas)
        .containsKeys(
            "SalesOrderEditBase",
            "SalesOrderEditRequest",
            "SalesOrderHeaderEdits",
            "SalesOrderLineEdit",
            "SalesOrderLineEditOperation",
            "SalesOrderLineFieldEdits",
            "SalesOrderFieldEditOperation",
            "SalesOrderEditResolution",
            "SalesOrderEditResolutionChoice",
            "SalesOrderEditResult",
            "SalesOrderEditOutcome",
            "SalesOrderEditLineIdMapping",
            "SalesOrderEditOperationView",
            "SalesOrderEditConflictProblem",
            "SalesOrderEditConflict",
            "SalesOrderEditConflictReason",
            "RequirementProfileInput");
    FIELD_EDITS.forEach(
        (edit, value) -> {
          assertThat(schemas).containsKey(edit);
          if (value != null) {
            assertThat(schemas).containsKey(value);
          }
        });
    assertThat(refOf(property("SalesOrderLineSpecificationValue", "requirementProfile")))
        .isEqualTo(REF + "RequirementProfileInput");
    assertThat(schemas.keySet())
        .noneMatch(name -> name.startsWith("SalesOrderEdit") && name.matches(".*_\\d+$"));
  }

  @Test
  @DisplayName("S2.9/S15.9: SET/CLEAR, ADD/UPDATE/REMOVE, choices, reasons and outcomes are enums")
  void enums() {
    assertThat(enumOf("SalesOrderFieldEditOperation")).containsExactlyInAnyOrder("SET", "CLEAR");
    assertThat(enumOf("SalesOrderLineEditOperation"))
        .containsExactlyInAnyOrder("ADD", "UPDATE", "REMOVE");
    assertThat(enumOf("SalesOrderEditResolutionChoice"))
        .containsExactlyInAnyOrder("KEEP_CURRENT", "USE_MINE", "NEW_VALUE");
    assertThat(enumOf("SalesOrderEditOutcome"))
        .containsExactlyInAnyOrder("APPLIED", "NO_CHANGE", "CONFLICT");
    assertThat(enumOf("SalesOrderEditConflictReason"))
        .containsExactlyInAnyOrder(
            "CHANGED_ON_SERVER",
            "LINE_REMOVED_ON_SERVER",
            "LINE_CHANGED_ON_SERVER",
            "LINE_PRODUCT_CHANGED",
            "UNCONFIRMED_REVERT",
            "REVIEW_REQUIRED");

    FIELD_EDITS
        .keySet()
        .forEach(
            edit ->
                assertThat(refOf(property(edit, "operation")))
                    .as("%s.operation", edit)
                    .isEqualTo(REF + "SalesOrderFieldEditOperation"));
    assertThat(refOf(property("SalesOrderLineEdit", "operation")))
        .isEqualTo(REF + "SalesOrderLineEditOperation");
    assertThat(refOf(property("SalesOrderEditResolution", "choice")))
        .isEqualTo(REF + "SalesOrderEditResolutionChoice");
    assertThat(refOf(property("SalesOrderEditResult", "outcome")))
        .isEqualTo(REF + "SalesOrderEditOutcome");
    assertThat(refOf(property("SalesOrderEditOperationView", "outcome")))
        .isEqualTo(REF + "SalesOrderEditOutcome");
  }

  @Test
  @DisplayName(
      "S15.15/S15.16 (R3): each facet value subtype is closed and carries its own valueType;"
          + " every valid value fits exactly one, the same value with an unknown property none")
  void facetValueSubtypesAreClosed() throws Exception {
    Map<String, Object> parent = schema("RequirementFacetValue");
    Object oneOf = parent.get("oneOf");
    assertThat(oneOf).as("RequirementFacetValue.oneOf").isInstanceOf(List.class);
    List<String> subtypes =
        ((List<?>) oneOf)
            .stream()
                .map(item -> String.valueOf(asMap(item).get("$ref")))
                .filter(ref -> ref.startsWith(REF))
                .map(ref -> ref.substring(REF.length()))
                .toList();
    assertThat(subtypes).hasSize(10);
    Map<String, Object> discriminator = asMap(parent.get("discriminator"));
    assertThat(discriminator).containsEntry("propertyName", "valueType");
    assertThat(asMap(discriminator.get("mapping"))).hasSize(10);
    assertThat(asMap(discriminator.get("mapping")).values())
        .containsExactlyInAnyOrderElementsOf(subtypes.stream().map(name -> REF + name).toList());

    for (String name : subtypes) {
      Map<String, Object> subtype = schema(name);
      assertThat(subtype)
          .as("%s is one object, not allOf its parent", name)
          .doesNotContainKey("allOf");
      assertThat(subtype.get("additionalProperties"))
          .as("%s.additionalProperties", name)
          .isEqualTo(Boolean.FALSE);
      assertThat(asMap(asMap(subtype.get("properties")).get("valueType")).get("enum"))
          .as("%s.valueType", name)
          .asInstanceOf(InstanceOfAssertFactories.LIST)
          .hasSize(1);
      assertThat(required(name)).as("%s required", name).contains("valueType");
    }

    // Every valueType's valid value, written as the server writes it.
    List<RequirementFacetValue> values =
        RequirementProfileSamples.everyValueType(UUID.randomUUID(), UUID.randomUUID())
            .facets()
            .stream()
            .map(RequirementFacet::value)
            .toList();
    assertThat(values).hasSize(10);
    for (RequirementFacetValue value : values) {
      Map<String, Object> written =
          objectMapper.readValue(
              objectMapper.writeValueAsString(value), new TypeReference<Map<String, Object>>() {});
      assertThat(subtypes.stream().filter(name -> fits(written, schema(name))).toList())
          .as("subtypes accepting %s", written)
          .hasSize(1);

      Map<String, Object> extended = new LinkedHashMap<>(written);
      extended.put("unexpected", 1);
      assertThat(subtypes.stream().filter(name -> fits(extended, schema(name))).toList())
          .as("subtypes accepting %s", extended)
          .isEmpty();
    }
  }

  @Test
  @DisplayName(
      "S2.9 (R3): each limited header text publishes its own maxLength; the shared text"
          + " instruction and unlimited texts have none")
  void headerTextLimits() {
    Map<String, Integer> limits = new LinkedHashMap<>();
    limits.put("customerReference", 100);
    limits.put("paymentTerms", 200);
    limits.put("shippingAddress", 500);
    limits.put("billingAddress", 500);
    limits.put("shippingMethod", 50);
    limits.forEach(
        (key, limit) -> {
          Map<String, Object> property = property("SalesOrderHeaderEdits", key);
          assertThat(refOf(property)).as("%s", key).isEqualTo(REF + "SalesOrderTextFieldEdit");
          List<Integer> maxLengths = new ArrayList<>();
          for (Object arm : (List<?>) property.get("allOf")) {
            if (asMap(arm).get("properties") instanceof Map<?, ?> own
                && own.get("value") instanceof Map<?, ?> value
                && value.get("maxLength") instanceof Number max) {
              maxLengths.add(max.intValue());
            }
          }
          assertThat(maxLengths).as("%s.value maxLength", key).containsExactly(limit);
        });
    assertThat(property("SalesOrderHeaderEdits", "notes"))
        .containsEntry("$ref", REF + "SalesOrderTextFieldEdit")
        .doesNotContainKey("allOf");
    assertThat(property("SalesOrderTextFieldEdit", "value")).doesNotContainKey("maxLength");
  }

  @Test
  @DisplayName("S15.15: typed request schemas are closed; moduleSpecs and sourceValue stay free")
  void closedRequestSchemasWithFreeJson() {
    CLOSED_SCHEMAS.forEach(
        name ->
            assertThat(schema(name).get("additionalProperties"))
                .as("%s.additionalProperties", name)
                .isEqualTo(Boolean.FALSE));

    Map<String, Object> moduleSpecs = property("SalesOrderLineSpecificationValue", "moduleSpecs");
    assertThat(moduleSpecs.get("additionalProperties"))
        .as("moduleSpecs.additionalProperties")
        .isNotNull()
        .isNotEqualTo(Boolean.FALSE);
    assertThat(moduleSpecs).doesNotContainKey("properties");
    assertThat(typesOf(moduleSpecs)).isSubsetOf("object", "null");
    if (moduleSpecs.get("additionalProperties") instanceof Map<?, ?> values) {
      // S15.10: {"gsm": 120} is valid, so the values themselves are not restricted to a type.
      assertThat(asMap(values)).as("moduleSpecs values").doesNotContainKey("type");
    }

    Map<String, Object> sourceValue = resolve(property("UnmodelledSpecConstraint", "sourceValue"));
    assertThat(sourceValue)
        .as("UnmodelledSpecConstraint.sourceValue is any JSON value")
        .doesNotContainKeys("type", "properties", "enum");
    assertThat(sourceValue.get("additionalProperties")).isNotEqualTo(Boolean.FALSE);
  }

  @Test
  @DisplayName("S2.9/S6.9/S11.2/S15.1: request containers publish exactly the contract properties")
  void requestContainersAreExact() {
    assertThat(properties("SalesOrderEditRequest").keySet())
        .containsExactlyInAnyOrder(
            "operationId",
            "baseId",
            "header",
            "lines",
            "resolutions",
            "editSessionId",
            "leaseTokens");
    assertThat(refOf(property("SalesOrderEditRequest", "header")))
        .isEqualTo(REF + "SalesOrderHeaderEdits");
    assertThat(refOf(asMap(property("SalesOrderEditRequest", "lines").get("items"))))
        .isEqualTo(REF + "SalesOrderLineEdit");
    assertThat(refOf(asMap(property("SalesOrderEditRequest", "resolutions").get("items"))))
        .isEqualTo(REF + "SalesOrderEditResolution");

    Map<String, String> header = new LinkedHashMap<>();
    header.put("customerReference", "SalesOrderTextFieldEdit");
    header.put("orderDate", "SalesOrderDateFieldEdit");
    header.put("requestedDeliveryDate", "SalesOrderDateFieldEdit");
    header.put("deliveryTerms", "SalesOrderDeliveryTermsEdit");
    header.put("paymentTerms", "SalesOrderTextFieldEdit");
    header.put("agreementContext", "SalesOrderAgreementContextEdit");
    header.put("contact", "SalesOrderContactEdit");
    header.put("shippingAddress", "SalesOrderTextFieldEdit");
    header.put("billingAddress", "SalesOrderTextFieldEdit");
    header.put("shippingMethod", "SalesOrderTextFieldEdit");
    header.put("notes", "SalesOrderTextFieldEdit");
    header.put("deadline", "SalesOrderDateFieldEdit");
    assertContainer("SalesOrderHeaderEdits", header);

    assertThat(properties("SalesOrderLineEdit").keySet())
        .containsExactlyInAnyOrder("operation", "lineId", "clientLineId", "productId", "fields");
    assertThat(refOf(property("SalesOrderLineEdit", "fields")))
        .isEqualTo(REF + "SalesOrderLineFieldEdits");

    Map<String, String> lineFields = new LinkedHashMap<>();
    lineFields.put("productDesc", "SalesOrderTextFieldEdit");
    lineFields.put("colorId", "SalesOrderIdFieldEdit");
    lineFields.put("finishedWidth", "SalesOrderLineWidthEdit");
    lineFields.put("requestedDeliveryDate", "SalesOrderDateFieldEdit");
    lineFields.put("singleLotRequired", "SalesOrderFlagFieldEdit");
    lineFields.put("shipmentPreference", "SalesOrderLineShipmentPreferenceEdit");
    lineFields.put("quantity", "SalesOrderLineQuantityEdit");
    lineFields.put("pricing", "SalesOrderLinePricingEdit");
    lineFields.put("tolerance", "SalesOrderLineToleranceEdit");
    lineFields.put("specification", "SalesOrderLineSpecificationEdit");
    // S6.9: a line's product is not an instruction; it changes only by product correction.
    assertContainer("SalesOrderLineFieldEdits", lineFields);

    assertThat(properties("SalesOrderEditResolution").keySet())
        .containsExactlyInAnyOrder("key", "lineId", "clientLineId", "choice");

    FIELD_EDITS.forEach(
        (edit, value) -> {
          assertThat(properties(edit).keySet())
              .as("%s properties", edit)
              .containsExactlyInAnyOrder("operation", "value");
          if (value != null) {
            assertThat(refOf(property(edit, "value"))).as("%s.value", edit).isEqualTo(REF + value);
          }
        });
  }

  @Test
  @DisplayName(
      "CEDIT-07 L20: four lease operations, bodies only (no session or token in a path), closed"
          + " requests, the catalogue as a finite enum and no token outside the caller's own lease")
  void fieldLeaseContract() {
    String leases = ORDER_PATH + "/edit-leases";
    Map<String, Object> acquire = mapAt(document, "paths", leases, "post");
    assertThat(acquire).containsEntry("operationId", "acquireSalesOrderEditLeases");
    assertThat(mapAt(acquire, "requestBody", "content", "application/json", "schema"))
        .containsEntry("$ref", REF + "SalesOrderEditLeaseAcquireRequest");
    assertThat(responseRefs(acquire, "200"))
        .contains(REF + "ApiResponseSalesOrderEditLeaseGrantDto");
    assertThat(mapAt(acquire, "responses", "409", "content", PROBLEM_JSON, "schema"))
        .containsEntry("$ref", REF + "SalesOrderEditLeaseProblem");
    Map<String, Object> renew = mapAt(document, "paths", leases + "/renew", "post");
    assertThat(renew).containsEntry("operationId", "renewSalesOrderEditLeases");
    assertThat(responseRefs(renew, "200"))
        .contains(REF + "ApiResponseSalesOrderEditLeaseRenewalDto");
    Map<String, Object> release = mapAt(document, "paths", leases + "/release", "post");
    assertThat(release).containsEntry("operationId", "releaseSalesOrderEditLeases");
    Map<String, Object> list = mapAt(document, "paths", leases, "get");
    assertThat(list).containsEntry("operationId", "listSalesOrderEditLeases");
    assertThat(responseRefs(list, "200")).contains(REF + "ApiResponseSalesOrderEditLeasesDto");
    for (Map<String, Object> operation : List.of(acquire, renew, release, list)) {
      assertThat(pathParameters(operation)).containsExactly("orderId");
    }

    for (String closed :
        List.of(
            "SalesOrderEditLeaseAcquireRequest",
            "SalesOrderEditLeaseTokensRequest",
            "SalesOrderEditLeaseKey")) {
      assertThat(schema(closed).get("additionalProperties")).as(closed).isEqualTo(false);
    }
    assertThat(properties("SalesOrderEditLeaseTokensRequest").keySet())
        .containsExactlyInAnyOrder("editSessionId", "leaseTokens");
    assertThat(required("SalesOrderEditLeaseAcquireRequest")).contains("editSessionId", "keys");
    assertThat(property("SalesOrderEditLeaseAcquireRequest", "keys")).containsEntry("maxItems", 50);

    List<String> catalogue = new ArrayList<>();
    for (com.fabricmanagement.sales.salesorder.domain.OrderEditKey key :
        com.fabricmanagement.sales.salesorder.domain.OrderEditKey.values()) {
      catalogue.add(key.wireName());
    }
    catalogue.add("line");
    assertThat(enumOf("SalesOrderEditLeaseField")).containsExactlyInAnyOrderElementsOf(catalogue);
    assertThat(refOf(property("SalesOrderEditLeaseKey", "key")))
        .isEqualTo(REF + "SalesOrderEditLeaseField");
    // Always ENFORCED (CEDIT-07-F3); OFF stays listed until the client stops reading the mode.
    assertThat(enumOf("SalesOrderEditLeaseMode")).containsExactlyInAnyOrder("OFF", "ENFORCED");
    assertThat(enumOf("SalesOrderEditLeaseRequirementReason"))
        .containsExactlyInAnyOrder("NOT_HELD", "HELD_BY_ANOTHER");

    assertThat(properties("SalesOrderEditLeaseDto")).containsKey("leaseToken");
    assertThat(properties("SalesOrderEditLeaseHolderDto"))
        .containsKeys("userId", "displayName", "mine", "editSessionId", "expiresAt")
        .doesNotContainKey("leaseToken");
    assertThat(properties("SalesOrderEditLeasePolicy"))
        .containsKeys(
            "mode",
            "leaseSeconds",
            "renewAfterSeconds",
            "idleAfterSeconds",
            "idleWarningSeconds",
            "maxKeysPerRequest",
            "maxLeasesPerSession");

    // The save carries its proof in the body; a missing lease is its own 409 field.
    assertThat(properties("SalesOrderEditRequest")).containsKeys("editSessionId", "leaseTokens");
    assertThat(property("SalesOrderEditRequest", "leaseTokens")).containsEntry("maxItems", 200);
    assertThat(required("SalesOrderEditRequest")).doesNotContain("editSessionId", "leaseTokens");
    assertThat(refOf(asMap(property("SalesOrderEditConflictProblem", "leases").get("items"))))
        .isEqualTo(REF + "SalesOrderEditLeaseRequirement");
  }

  @Test
  @DisplayName("S10.5/§14: required lists of requests and answers, and the nullable receipt fields")
  void requiredAndNullable() {
    assertThat(required("SalesOrderEditRequest"))
        .contains("operationId", "baseId")
        .doesNotContain("header", "lines", "resolutions");
    assertThat(required("SalesOrderLineEdit"))
        .contains("operation")
        .doesNotContain("lineId", "clientLineId", "productId", "fields");
    assertThat(required("SalesOrderEditResolution"))
        .contains("key", "choice")
        .doesNotContain("lineId", "clientLineId");
    FIELD_EDITS
        .keySet()
        .forEach(
            edit ->
                assertThat(required(edit))
                    .as("%s required", edit)
                    .contains("operation")
                    .doesNotContain("value"));
    assertThat(required("SalesOrderLineQuantityValue")).contains("requestedQty", "unit");
    assertThat(required("SalesOrderDeliveryTermsValue")).contains("term", "place");
    assertThat(required("SalesOrderAgreementContextValue")).contains("context");
    assertThat(required("SalesOrderContactValue")).contains("whatsapp");

    assertThat(required("SalesOrderEditBase"))
        .contains("baseId", "orderId", "orderVersion", "capturedAt", "expiresAt", "order");
    assertThat(required("SalesOrderEditResult"))
        .contains("operationId", "outcome", "replayed", "resultVersion", "lineIds", "nextBase");
    assertThat(required("SalesOrderEditLineIdMapping")).contains("clientLineId", "lineId");
    assertThat(required("SalesOrderEditConflict")).contains("key", "reason", "choices");
    assertThat(required("SalesOrderEditOperationView"))
        .contains(
            "operationId", "outcome", "recordedAt", "resultVersion", "lineIds", "conflictBaseId");

    assertThat(isNullable(property("SalesOrderEditOperationView", "resultVersion")))
        .as("SalesOrderEditOperationView.resultVersion is nullable")
        .isTrue();
    assertThat(isNullable(property("SalesOrderEditOperationView", "conflictBaseId")))
        .as("SalesOrderEditOperationView.conflictBaseId is nullable")
        .isTrue();
    assertThat(isNullable(property("SalesOrderEditOperationView", "operationId"))).isFalse();
    assertThat(isNullable(property("SalesOrderEditResult", "resultVersion"))).isFalse();
  }

  @Test
  @DisplayName("S15.15: lines.maxItems equals the configured line-operation limit of a save")
  void linesMaxItemsMatchesTheRuntimeLimit() {
    Map<String, Object> lines = property("SalesOrderEditRequest", "lines");
    assertThat(typesOf(lines)).contains("array");
    assertThat(lines.get("maxItems")).isInstanceOf(Number.class);
    assertThat(((Number) lines.get("maxItems")).intValue())
        .isEqualTo(editProperties.getMaxLineOperations());
  }

  /**
   * Whether a JSON object fits a closed object schema at the level that decides between facet value
   * types: every property is declared, every required one is present, an enum is met.
   */
  private static boolean fits(Map<String, Object> value, Map<String, Object> schema) {
    Map<String, Object> declared =
        schema.get("properties") instanceof Map<?, ?> own ? asMap(own) : Map.of();
    if (Boolean.FALSE.equals(schema.get("additionalProperties"))
        && !declared.keySet().containsAll(value.keySet())) {
      return false;
    }
    if (schema.get("required") instanceof List<?> names
        && !value.keySet().containsAll(names.stream().map(String::valueOf).toList())) {
      return false;
    }
    for (Map.Entry<String, Object> entry : declared.entrySet()) {
      if (asMap(entry.getValue()).get("enum") instanceof List<?> allowed
          && value.containsKey(entry.getKey())
          && !allowed.contains(value.get(entry.getKey()))) {
        return false;
      }
    }
    return true;
  }

  // ── document navigation ───────────────────────────────────────────────────

  private static Map<String, String> fieldEdits() {
    Map<String, String> edits = new LinkedHashMap<>();
    edits.put("SalesOrderTextFieldEdit", null);
    edits.put("SalesOrderDateFieldEdit", null);
    edits.put("SalesOrderIdFieldEdit", null);
    edits.put("SalesOrderFlagFieldEdit", null);
    edits.put("SalesOrderLineShipmentPreferenceEdit", "LineShipmentPreference");
    edits.put("SalesOrderDeliveryTermsEdit", "SalesOrderDeliveryTermsValue");
    edits.put("SalesOrderAgreementContextEdit", "SalesOrderAgreementContextValue");
    edits.put("SalesOrderContactEdit", "SalesOrderContactValue");
    edits.put("SalesOrderLineQuantityEdit", "SalesOrderLineQuantityValue");
    edits.put("SalesOrderLinePricingEdit", "SalesOrderLinePricingValue");
    edits.put("SalesOrderLineToleranceEdit", "SalesOrderLineToleranceValue");
    edits.put("SalesOrderLineWidthEdit", "SalesOrderLineWidthValue");
    edits.put("SalesOrderLineSpecificationEdit", "SalesOrderLineSpecificationValue");
    return edits;
  }

  private void assertContainer(String name, Map<String, String> expected) {
    assertThat(properties(name).keySet())
        .as("%s properties", name)
        .containsExactlyInAnyOrderElementsOf(expected.keySet());
    expected.forEach(
        (property, target) ->
            assertThat(refOf(property(name, property)))
                .as("%s.%s", name, property)
                .isEqualTo(REF + target));
  }

  private Map<String, Object> schema(String name) {
    return mapAt(document, "components", "schemas", name);
  }

  /** A schema's own properties merged with those of its allOf parts. */
  private Map<String, Object> properties(String name) {
    Map<String, Object> merged = new LinkedHashMap<>();
    collect(schema(name), merged, new ArrayList<>(), new LinkedHashSet<>());
    assertThat(merged).as("%s properties", name).isNotEmpty();
    return merged;
  }

  /** A schema's required list merged with those of its allOf parts. */
  private List<String> required(String name) {
    List<String> required = new ArrayList<>();
    collect(schema(name), new LinkedHashMap<>(), required, new LinkedHashSet<>());
    return required;
  }

  private void collect(
      Map<String, Object> schema,
      Map<String, Object> properties,
      List<String> required,
      Set<String> visited) {
    if (schema.get("$ref") instanceof String ref) {
      if (visited.add(ref)) {
        collect(resolve(schema), properties, required, visited);
      }
      return;
    }
    if (schema.get("properties") instanceof Map<?, ?> own) {
      own.forEach((key, value) -> properties.putIfAbsent(String.valueOf(key), value));
    }
    if (schema.get("required") instanceof List<?> names) {
      names.forEach(name -> required.add(String.valueOf(name)));
    }
    if (schema.get("allOf") instanceof List<?> parts) {
      parts.forEach(part -> collect(asMap(part), properties, required, visited));
    }
  }

  private Map<String, Object> property(String schemaName, String propertyName) {
    Object property = properties(schemaName).get(propertyName);
    assertThat(property).as("%s.%s", schemaName, propertyName).isNotNull();
    return asMap(property);
  }

  private Map<String, Object> resolve(Map<String, Object> node) {
    if (node.get("$ref") instanceof String ref) {
      assertThat(ref).startsWith(REF);
      return schema(ref.substring(REF.length()));
    }
    return node;
  }

  private List<String> enumOf(String name) {
    Object values = schema(name).get("enum");
    assertThat(values).as("%s.enum", name).isInstanceOf(List.class);
    return ((List<?>) values).stream().map(String::valueOf).toList();
  }

  /** The schema a property refers to: directly, or through a single-reference composition. */
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

  /** Nullable in any of the 3.1 shapes: a type array with null, an anyOf/oneOf null arm. */
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
    return types;
  }

  private static List<String> responseRefs(Map<String, Object> operation, String status) {
    Map<String, Object> response = mapAt(operation, "responses", status);
    List<String> refs = new ArrayList<>();
    if (response.get("content") instanceof Map<?, ?> content) {
      content.values().stream()
          .map(media -> asMap(media).get("schema"))
          .filter(Map.class::isInstance)
          .map(schema -> refOf(asMap(schema)))
          .filter(ref -> ref != null)
          .forEach(refs::add);
    }
    assertThat(refs).as("%s response schemas", status).isNotEmpty();
    return refs;
  }

  private static List<String> pathParameters(Map<String, Object> operation) {
    assertThat(operation.get("parameters")).isInstanceOf(List.class);
    return ((List<?>) operation.get("parameters"))
        .stream()
            .map(SalesOrderEditOpenApiIT::asMap)
            .filter(parameter -> "path".equals(parameter.get("in")))
            .map(parameter -> String.valueOf(parameter.get("name")))
            .toList();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object node) {
    assertThat(node).isInstanceOf(Map.class);
    return (Map<String, Object>) node;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapAt(Map<String, Object> root, String... path) {
    Object current = root;
    StringBuilder traversed = new StringBuilder();
    for (String segment : path) {
      assertThat(current)
          .as("OpenAPI map at %s", traversed.isEmpty() ? "root" : traversed)
          .isInstanceOf(Map.class);
      current = ((Map<?, ?>) current).get(segment);
      traversed.append('.').append(segment);
      assertThat(current).as("OpenAPI value at %s", traversed).isNotNull();
    }
    assertThat(current).as("OpenAPI map at %s", traversed).isInstanceOf(Map.class);
    return (Map<String, Object>) current;
  }
}
