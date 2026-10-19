package com.fabricmanagement.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.testsupport.PostgresImage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
public class OpenApiExportIT {

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      PostgresImage.container()
          .withDatabaseName("fabric_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

  private static final NullableReference NULLABLE_REFERENCE_PROBE =
      new NullableReference(
          "OrderCoverSelectionPreview", "rejection", "OrderCoverSelectionPreviewRejection");

  /**
   * OPENAPI-NULLREF-1 reviewed inventory: every nullable reference in the export as (schema,
   * property, target). This is the regression pin, not a production allowlist — the repair is
   * generic. A new nullable reference is added here by its author.
   */
  private static final List<NullableReference> NULLABLE_REFERENCES =
      List.of(
          new NullableReference("DraftStockSelection", "option", "QuantityProposalOption"),
          new NullableReference(
              "DraftStockSelectionCapability", "reason", "DraftStockSelectionReason"),
          new NullableReference(
              "OrderIntakeStockPreviewPiece", "ownerType", "StockProposalOwnerType"),
          new NullableReference("TenantBrandingDto", "logo", "TenantBrandAssetDto"),
          new NullableReference("TenantBrandingDto", "icon", "TenantBrandAssetDto"),
          new NullableReference("TenantBrandingDto", "logoDark", "TenantBrandAssetDto"),
          new NullableReference("TenantBrandingDto", "iconDark", "TenantBrandAssetDto"),
          new NullableReference("DecisionCapability", "reason", "DecisionBlockedReason"),
          new NullableReference("DecisionCapability", "routesTo", "DecisionRouteTarget"),
          new NullableReference("DecisionReasonParameters", "requiredPermission", "PermissionKey"),
          new NullableReference("OrderCoverDetail", "evidence", "OrderCoverEvidence"),
          new NullableReference("OrderDeliveryOutlook", "arrival", "ArrivalEstimateView"),
          new NullableReference(
              "OrderIntakeReadinessLine", "stockChoice", "QuantityAcceptanceBasis"),
          new NullableReference(
              "OrderCoverLineDecision", "blockReason", "OrderCoverLineBlockReason"),
          new NullableReference(
              "OrderCoverLineDecision", "productionQuantity", "OrderCoverQuantity"),
          NULLABLE_REFERENCE_PROBE,
          new NullableReference(
              "ProductionStockAvailabilityLot", "colour", "ProductionStockAvailabilityColour"),
          new NullableReference(
              "ProductionStockAvailabilityQualityBreakdown",
              "grade",
              "ProductionStockAvailabilityQualityGrade"),
          new NullableReference(
              "ReactivateColorPartnerRefRequest", "newPrimaryCode", "ColorPartnerCodeInput"),
          new NullableReference(
              "YarnReconciliationItemDto",
              "resolvedCandidate",
              "YarnReconciliationResolvedCandidateDto"),
          // FIBER-CATALOG-1: a blend has no ISO code of its own.
          new NullableReference("FiberDto", "isoCode", "FiberIsoCodeDto"),
          // STOCK-PREVIEW-1: a free quantity that cannot be determined is null, never a number.
          new NullableReference(
              "OrderIntakeStockPreviewLot", "free", "OrderIntakeStockPreviewQuantity"),
          new NullableReference(
              "OrderIntakeStockPreviewTotals", "free", "OrderIntakeStockPreviewQuantity"),
          new NullableReference(
              "OrderIntakeStockPreviewTotals",
              "notCoveredByReadyStock",
              "OrderIntakeStockPreviewQuantity"),
          // STOCK-PROPOSAL-2: an applicable option has no blocked reason.
          new NullableReference(
              "StockProposalApplyCapability", "reason", "StockProposalBlockedReason"),
          // CONSIGNEE-1 S1: a quick-create that needs a decision wrote nothing, so it has no
          // partner and no link yet.
          new NullableReference("QuickCreatePartnerLinkResponse", "partner", "TradingPartnerDto"),
          new NullableReference("QuickCreatePartnerLinkResponse", "link", "PartnerLinkDto"));

  /** Non-nullable reference control: the plain {@code $ref} form must survive unchanged. */
  private static final NullableReference NON_NULLABLE_CONTROL =
      new NullableReference("OrderCoverDetail", "case", "OrderCoverCase");

  private static final Set<String> HTTP_METHODS =
      Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

  /** Keywords whose value is a single schema. */
  private static final Set<String> SCHEMA_KEYWORDS =
      Set.of(
          "items",
          "not",
          "additionalProperties",
          "contains",
          "if",
          "then",
          "else",
          "propertyNames",
          "unevaluatedProperties",
          "unevaluatedItems",
          "additionalItems",
          "contentSchema");

  /** Keywords whose value is a list of schemas. */
  private static final Set<String> SCHEMA_LIST_KEYWORDS =
      Set.of("allOf", "anyOf", "oneOf", "prefixItems");

  /** Keywords whose value is a name → schema map. */
  private static final Set<String> SCHEMA_MAP_KEYWORDS =
      Set.of("properties", "patternProperties", "dependentSchemas");

  private record NullableReference(String schema, String property, String target) {}

  private record NullableScalar(String schema, String property, String valueType) {}

  private static final List<NullableScalar> STOCK_NULLABLE_SCALARS =
      List.of(
          new NullableScalar("QuantityProposalLot", "closesLot", "boolean"),
          new NullableScalar("QuantityProposalLot", "depletesFreePool", "boolean"),
          new NullableScalar("StockProposalCoverage", "coveringOptionKey", "string"),
          new NullableScalar("QuantityProposal", "effectiveMinHealthyPieces", "integer"),
          new NullableScalar(
              "OrderIntakeStockPreviewEvaluation", "effectiveMinHealthyPieces", "integer"));

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void exportAndVerifyOpenApiSpec() throws Exception {
    // 1. Generate fresh spec from running app
    ResponseEntity<String> response = restTemplate.getForEntity("/api-docs.yaml", String.class);

    // Assert successful generation and valid OpenAPI signature
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String generatedSpec = response.getBody();
    assertThat(generatedSpec).isNotNull();
    assertThat(generatedSpec).contains("openapi: 3.");
    assertThat(generatedSpec).doesNotContainPattern("(?m)^    [A-Za-z][A-Za-z0-9]*_1:$");

    Map<String, Object> generatedDocument =
        new YAMLMapper().readValue(generatedSpec, new TypeReference<Map<String, Object>>() {});
    assertValidationContracts(generatedDocument);
    assertPermissionCatalogueContract(generatedDocument);
    assertNavPreferencesImportContract(generatedDocument);
    assertOrderIntakeStockPreviewContract(generatedDocument);
    assertDraftStockSelectionContract(generatedDocument);
    assertNoNullTypedReferences(generatedDocument);
    assertNullableReferenceContract(generatedDocument);

    // Red probe uses a separate copy: the actual export must never contain the mutated value.
    Map<String, Object> corruptedDocument =
        new YAMLMapper().readValue(generatedSpec, new TypeReference<Map<String, Object>>() {});
    mapAt(corruptedDocument, "components", "schemas", "PermissionKey")
        .put("enum", List.of("sales:teleport"));
    assertThatThrownBy(() -> assertPermissionCatalogueContract(corruptedDocument))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("PermissionKey");

    assertNullableReferenceRedProbes(generatedSpec);
    assertStockNullableScalarRedProbes(generatedSpec);
    assertRetiredQuantityAcceptanceRedProbe(generatedSpec);

    // 2. Define the target spec file location
    File specFile = new File("api/openapi.yaml");

    // 3. Determine if we are in update mode
    boolean updateMode =
        Boolean.parseBoolean(System.getProperty("UPDATE_OPENAPI", "false"))
            || Boolean.parseBoolean(System.getenv("UPDATE_OPENAPI"));

    // 4. Update or Validate
    if (!specFile.exists() || updateMode) {
      specFile.getParentFile().mkdirs();
      Files.writeString(specFile.toPath(), generatedSpec);
      System.out.println("✅ OpenAPI spec written to " + specFile.getAbsolutePath());
    } else {
      String existingSpec = Files.readString(specFile.toPath());

      assertThat(generatedSpec)
          .as(
              "OpenAPI contract has drifted! Run 'UPDATE_OPENAPI=true ./mvnw verify"
                  + " -Dit.test=OpenApiExportIT' to accept changes and commit.")
          .isEqualTo(existingSpec);
    }
  }

  private void assertNavPreferencesImportContract(Map<String, Object> document) {
    Map<String, Object> operation =
        mapAt(document, "paths", "/api/v1/common/users/{id}/nav-preferences/import", "post");
    assertThat(mapAt(operation, "requestBody", "content", "application/json", "schema"))
        .containsEntry("$ref", "#/components/schemas/NavPreferencesRequest");
    assertThat(mapAt(operation, "responses", "200", "content", "application/json", "schema"))
        .containsEntry("$ref", "#/components/schemas/ApiResponseNavPreferencesImportResponse");
    assertThat(schemaProperty(document, "ApiResponseNavPreferencesImportResponse", "data"))
        .containsEntry("$ref", "#/components/schemas/NavPreferencesImportResponse");
    assertThat(schemaProperty(document, "NavPreferencesImportResponse", "imported"))
        .containsEntry("type", "boolean");
    assertThat(schemaProperty(document, "NavPreferencesImportResponse", "preferences"))
        .containsEntry("$ref", "#/components/schemas/NavPreferencesResponse");
    Object required =
        mapAt(document, "components", "schemas", "NavPreferencesImportResponse").get("required");
    assertThat(required).isInstanceOf(List.class);
    assertThat(((List<?>) required).stream().map(Object::toString).toList())
        .containsExactlyInAnyOrder("imported", "preferences");
  }

  /** STOCK-PREVIEW-1: the stock preview is published with its request bounds and response. */
  private void assertOrderIntakeStockPreviewContract(Map<String, Object> document) {
    Map<String, Object> operation =
        mapAt(document, "paths", "/api/v1/sales/order-intake/stock-preview", "post");
    assertThat(operation).containsEntry("operationId", "previewOrderIntakeStock");
    assertThat(mapAt(operation, "requestBody", "content", "application/json", "schema"))
        .containsEntry("$ref", "#/components/schemas/OrderIntakeStockPreviewRequest");
    assertThat(mapAt(operation, "responses", "200", "content", "*/*", "schema"))
        .containsEntry("$ref", "#/components/schemas/ApiResponseOrderIntakeStockPreview");
    assertThat(schemaProperty(document, "ApiResponseOrderIntakeStockPreview", "data"))
        .containsEntry("$ref", "#/components/schemas/OrderIntakeStockPreview");

    // Both tolerance limits are required; 0 is a value (Fatih, 2026-10-13).
    assertThat(requiredOf(document, "OrderIntakeStockPreviewRequest"))
        .containsExactlyInAnyOrder(
            "productId",
            "requestedQty",
            "unit",
            "singleLotRequired",
            "toleranceUpPct",
            "toleranceDownPct");
    assertThat(requiredOf(document, "SalesOrderLineRequest"))
        .contains("toleranceUpPct", "toleranceDownPct");
    assertThat(schemaProperty(document, "OrderIntakeStockPreviewRequest", "manualPieceIds"))
        .containsEntry("type", "array");
    assertPercentageBounds(document, "OrderIntakeStockPreviewRequest", "toleranceUpPct");
    assertPercentageBounds(document, "OrderIntakeStockPreviewRequest", "toleranceDownPct");

    assertThat(requiredOf(document, "OrderIntakeStockPreview"))
        .containsExactlyInAnyOrder(
            "unit", "canonicalUnit", "evaluation", "lots", "totals", "unconvertibleIntentCount");
    assertThat(mapAt(document, "components", "schemas"))
        .doesNotContainKey("OrderIntakeStockPreviewOptionCheck");
    // STOCK-PROPOSAL-2: ordered options with the main index; each option carries its capability.
    assertThat(requiredOf(document, "OrderIntakeStockPreviewEvaluation"))
        .contains(
            "options",
            "mainOptionIndex",
            "searchComplete",
            "policyCode",
            "effectiveMinHealthyPieces",
            "coverage")
        .doesNotContain("optionsOutsideAgreedTolerance");
    assertThat(requiredOf(document, "QuantityProposal"))
        .contains(
            "options",
            "mainOptionIndex",
            "searchComplete",
            "policyCode",
            "effectiveMinHealthyPieces",
            "coverage");
    assertThat(requiredOf(document, "QuantityProposalOption"))
        .contains(
            "optionKey",
            "placement",
            "source",
            "quantity",
            "difference",
            "toleranceStatus",
            "reasons",
            "lots",
            "heldStock",
            "apply")
        .doesNotContain("nearest");
    assertThat(requiredOf(document, "QuantityProposalLot"))
        .contains(
            "closesLot", "depletesFreePool", "remainingPieces", "remainingQuantity", "pieceIds");
    assertStockNullableScalars(document);
    assertThat(requiredOf(document, "StockProposalCoverage"))
        .containsExactlyInAnyOrder("status", "coveringOptionKey", "reasons");
    assertThat(mapAt(document, "components", "schemas", "StockProposalCoverageStatus"))
        .containsEntry(
            "enum", List.of("COVERABLE", "BELOW_REQUEST_ONLY", "NO_APPLICABLE_OPTION", "UNKNOWN"));
    assertThat(mapAt(document, "components", "schemas", "StockProposalPolicyCode"))
        .containsEntry("enum", List.of("STANDARD", "FABRIC_ROLL_BASE", "UNKNOWN"));
    assertThat(requiredOf(document, "StockProposalSettingsView"))
        .contains("fabricRollMinHealthyPieces");
    assertThat(requiredOf(document, "StockProposalApplyCapability"))
        .containsExactlyInAnyOrder(
            "action", "allowed", "reason", "routesTo", "needsApproval", "needsConfirmation");
    assertThat(mapAt(document, "components", "schemas", "StockProposalPlacement"))
        .containsEntry("enum", List.of("MAIN", "BELOW", "ABOVE", "MANUAL"));
    assertThat(schemaProperty(document, "OrderIntakeStockPreviewHeldPiece", "holdStatus"))
        .containsEntry("$ref", "#/components/schemas/OrderIntakeStockPreviewHoldStatus");
    assertThat(mapAt(document, "components", "schemas", "OrderIntakeStockPreviewHoldStatus"))
        .containsEntry("enum", List.of("VERIFIED", "UNVERIFIED"));
  }

  /** DRAFT-HOLD-BE-1: published ownership, capabilities and command identity. */
  private void assertDraftStockSelectionContract(Map<String, Object> document) {
    String root = "/api/v1/sales/order-intake/stock-selections";
    assertThat(mapAt(document, "paths", root, "post"))
        .containsEntry("operationId", "openDraftStockSelection");
    assertThat(mapAt(document, "paths", root + "/{id}", "get"))
        .containsEntry("operationId", "getDraftStockSelection");
    for (String action : List.of("acquire", "renew", "release", "close")) {
      assertThat(mapAt(document, "paths", root + "/{id}/" + action, "post"))
          .containsKey("requestBody");
    }
    assertThat(mapAt(document, "paths", root + "/orders/{orderId}/lines/{lineId}/apply", "post"))
        .containsEntry("operationId", "applyDraftStockSelection");
    assertThat(
            mapAt(document, "paths", root + "/orders/{orderId}/lines/{lineId}/selection", "delete"))
        .containsEntry("operationId", "removeAppliedStockSelection");
    assertThat(mapAt(document, "paths").keySet())
        .noneMatch(path -> path.contains("/quantity-acceptances"));
    // Historical acceptance records remain internal; the retired HTTP DTO must not be exported.
    assertThat(mapAt(document, "components", "schemas")).doesNotContainKey("QuantityAcceptance");
    assertThat(
            mapAt(
                document,
                "paths",
                "/api/v1/sales/order-intake/products/{productId}/stock-live-events",
                "get"))
        .containsEntry("operationId", "subscribeDraftStockLiveEvents");
    assertThat(requiredOf(document, "DraftStockSelection"))
        .contains(
            "selectionId",
            "sessionId",
            "clientLineId",
            "selectionRevision",
            "state",
            "policy",
            "expiresAt",
            "remainingMs",
            "option",
            "grants",
            "capabilities",
            "salesOrderId",
            "salesOrderLineId",
            "orderVersion");
    assertThat(requiredOf(document, "DraftStockSelectionPolicy"))
        .containsExactlyInAnyOrder(
            "leaseSeconds",
            "renewAfterSeconds",
            "idleAfterSeconds",
            "idleWarningSeconds",
            "maxKeysPerRequest");
    assertThat(requiredOf(document, "DraftStockSelectionCapability"))
        .containsExactlyInAnyOrder(
            "action", "allowed", "reason", "routesTo", "needsApproval", "needsConfirmation");
    assertThat(requiredOf(document, "AcquireDraftStockSelectionRequest"))
        .contains("operationId", "expectedRevision", "line", "optionKey", "previousTokens");
    assertThat(requiredOf(document, "DraftStockSelectionProof"))
        .contains("selectionId", "selectionRevision", "tokens");
    assertThat(requiredOf(document, "ApplyDraftStockSelectionRequest"))
        .containsExactlyInAnyOrder("operationId", "expectedVersion", "selection");
    assertThat(requiredOf(document, "RemoveAppliedStockSelectionRequest"))
        .containsExactlyInAnyOrder("operationId", "expectedVersion");
    assertThat(mapAt(document, "components", "schemas", "DraftStockSelectionState").get("enum"))
        .isEqualTo(
            List.of(
                "OPEN",
                "HELD",
                "BLOCKED",
                "EXPIRED",
                "RELEASED",
                "TRANSFERRED",
                "CONVERTED",
                "SUPERSEDED"));
    assertThat(mapAt(document, "components", "schemas", "StockProposalOwnerType").get("enum"))
        .isEqualTo(List.of("QUOTE", "SALES_ORDER", "DRAFT", "UNKNOWN"));
    assertThat(requiredOf(document, "OrderIntakeStockPreviewPiece")).contains("ownerType");
    assertThat(schemaProperty(document, "OrderIntakeStockPreviewRequest", "ownStockSelection"))
        .isNotEmpty();
    for (String property :
        List.of(
            "customerRequestQty",
            "clientLineId",
            "stockSelection",
            "previewStockSelection",
            "sourceQuoteLineId")) {
      assertThat(schemaProperty(document, "SalesOrderLineRequest", property)).isNotEmpty();
    }
    assertThat(schemaProperty(document, "SalesOrderLineResponse", "customerRequestQty"))
        .isNotEmpty();
    for (String property : List.of("sessionId", "expiresAt", "orderVersion")) {
      assertThat(schemaProperty(document, "DraftStockSelection", property).get("type"))
          .as("DraftStockSelection.%s accepts null under OpenAPI 3.1", property)
          .isInstanceOf(List.class);
      assertThat(
              ((List<?>) schemaProperty(document, "DraftStockSelection", property).get("type"))
                  .stream().map(Object::toString).toList())
          .contains("null");
    }
    assertThat(
            mapAt(
                document,
                "paths",
                root + "/{id}/acquire",
                "post",
                "responses",
                "409",
                "content",
                "application/problem+json",
                "schema"))
        .containsEntry("$ref", "#/components/schemas/DraftStockSelectionProblem");
  }

  /** OpenAPI 3.1 scalar nullability is a type union, independent of the array order. */
  private void assertStockNullableScalars(Map<String, Object> document) {
    for (NullableScalar field : STOCK_NULLABLE_SCALARS) {
      String name = field.schema() + "." + field.property();
      assertThat(requiredOf(document, field.schema()))
          .as("%s is required", name)
          .contains(field.property());
      Object type = schemaProperty(document, field.schema(), field.property()).get("type");
      assertThat(type).as("%s allows its scalar value and null", name).isInstanceOf(List.class);
      List<?> types = (List<?>) type;
      assertThat(types).as("%s has exactly two types", name).hasSize(2);
      assertThat(new HashSet<>(types))
          .as("%s preserves both value and null", name)
          .isEqualTo(Set.of(field.valueType(), "null"));
    }
  }

  /** Regression probes alter a separate document, never the exported contract. */
  private void assertStockNullableScalarRedProbes(String generatedSpec) throws Exception {
    Map<String, Object> document = parse(generatedSpec);
    for (NullableScalar field : STOCK_NULLABLE_SCALARS) {
      String name = field.schema() + "." + field.property();
      Map<String, Object> property = schemaProperty(document, field.schema(), field.property());
      Object originalType = property.get("type");
      // Missing null, missing the value type, and YAML null instead of the type name "null".
      for (Object invalidType :
          List.of(field.valueType(), "null", Arrays.asList(field.valueType(), null))) {
        property.put("type", invalidType);
        assertThatThrownBy(() -> assertStockNullableScalars(document))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining(name);
      }
      property.put("type", originalType);
      Map<String, Object> schema = mapAt(document, "components", "schemas", field.schema());
      Object required = schema.get("required");
      schema.put(
          "required",
          requiredOf(document, field.schema()).stream()
              .filter(value -> !value.equals(field.property()))
              .toList());
      assertThatThrownBy(() -> assertStockNullableScalars(document))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining(name);
      schema.put("required", required);
    }
  }

  private List<String> requiredOf(Map<String, Object> document, String schemaName) {
    Object required = mapAt(document, "components", "schemas", schemaName).get("required");
    assertThat(required).as("%s required", schemaName).isInstanceOf(List.class);
    return ((List<?>) required).stream().map(Object::toString).toList();
  }

  private void assertPermissionCatalogueContract(Map<String, Object> document) {
    // Validate the live springdoc document BEFORE either writing or comparing the stored YAML.
    Map<String, Object> schema = mapAt(document, "components", "schemas", "PermissionKey");
    assertThat(schema).containsEntry("type", "string");
    assertThat(schema.get("enum")).isInstanceOf(List.class);
    List<?> values = (List<?>) schema.get("enum");
    assertThat(values).doesNotHaveDuplicates();
    assertThat(new HashSet<>(values))
        .as("PermissionKey must expose exactly the catalogue's resource:action wire values")
        .isEqualTo(
            new HashSet<>(Arrays.stream(PermissionKey.values()).map(PermissionKey::key).toList()));
    assertThat(schemaProperty(document, "PermissionCatalogueEntryDto", "key"))
        .containsEntry("$ref", "#/components/schemas/PermissionKey");
    assertThat(
            mapAt(document, "components", "schemas", "PermissionCatalogueEntryDto", "properties"))
        .doesNotContainKey("enforced");
    Object required =
        mapAt(document, "components", "schemas", "PermissionCatalogueEntryDto").get("required");
    assertThat(required).isInstanceOf(List.class);
    assertThat(((List<?>) required).stream().map(Object::toString).toList())
        .containsExactlyInAnyOrder("action", "key", "resource");
    assertThat(
            mapAt(document, "paths", "/api/v1/platform/permissions/catalogue", "get", "responses"))
        .containsKey("200");
  }

  private void assertValidationContracts(Map<String, Object> document) {
    assertThat(document.get("openapi")).isEqualTo("3.1.0");

    assertPercentageBounds(document, "CreateFiberQualityStandardRequest", "elongationPctMin");
    assertPercentageBounds(document, "UpdateFiberQualityStandardRequest", "moisturePctTarget");
    assertPercentageBounds(document, "CreateFiberTestResultRequest", "trashContentPercent");
    assertNoInvalidRangeMaximum(document, "openapi");

    Map<String, Object> positive = schemaProperty(document, "AddOutputItemRequest", "netWeight");
    assertNumericValue(positive, "exclusiveMinimum", BigDecimal.ZERO);
    assertThat(positive).doesNotContainKey("minimum");

    Map<String, Object> positiveOrZero =
        schemaProperty(document, "CreateFiberQualityStandardRequest", "finenessMin");
    assertNumericValue(positiveOrZero, "minimum", BigDecimal.ZERO);
    assertThat(positiveOrZero).doesNotContainKey("exclusiveMinimum");

    assertThat(schemaProperty(document, "CreateExternalUserRequest", "department"))
        .containsEntry("deprecated", true);
    assertThat(schemaProperty(document, "CreateInternalUserRequest", "department"))
        .doesNotContainKey("deprecated");
    assertThat(schemaProperty(document, "AssignContactRequest", "department"))
        .doesNotContainKey("deprecated");
  }

  private void assertPercentageBounds(
      Map<String, Object> document, String schemaName, String propertyName) {
    Map<String, Object> percentage = schemaProperty(document, schemaName, propertyName);
    assertNumericValue(percentage, "minimum", BigDecimal.ZERO);
    assertNumericValue(percentage, "maximum", BigDecimal.valueOf(100));
  }

  private void assertNumericValue(Map<String, Object> schema, String keyword, BigDecimal expected) {
    Object actual = schema.get(keyword);
    assertThat(actual).as("OpenAPI keyword '%s'", keyword).isInstanceOf(Number.class);
    assertThat(new BigDecimal(actual.toString())).isEqualByComparingTo(expected);
  }

  private void assertNoInvalidRangeMaximum(Object node, String path) {
    if (node instanceof Map<?, ?> map) {
      if (map.containsKey("maximum")) {
        assertThat(String.valueOf(map.get("maximum")))
            .as("maximum at %s", path)
            .isNotEqualTo(String.valueOf(Long.MAX_VALUE));
      }
      map.forEach((key, value) -> assertNoInvalidRangeMaximum(value, path + "." + key));
    } else if (node instanceof Iterable<?> iterable) {
      int index = 0;
      for (Object value : iterable) {
        assertNoInvalidRangeMaximum(value, path + "[" + index + "]");
        index++;
      }
    }
  }

  // --- OPENAPI-NULLREF-1 -------------------------------------------------------------------

  /**
   * Forbidden rendering: no schema node under {@code components.schemas} or under the inline
   * request-body / response media schemas of {@code paths} (the set the customizer walks) carries
   * {@code $ref} together with a {@code type} of {@code "null"} or a type array containing it.
   */
  private void assertNoNullTypedReferences(Map<String, Object> document) {
    List<String> violations = new ArrayList<>();
    Object schemas = mapAt(document, "components").get("schemas");
    if (schemas instanceof Map<?, ?> schemaMap) {
      schemaMap.forEach(
          (name, schema) ->
              collectNullTypedReferences(schema, "components.schemas." + name, violations));
    }
    if (document.get("paths") instanceof Map<?, ?> paths) {
      paths.forEach(
          (path, pathItem) -> {
            if (!(pathItem instanceof Map<?, ?> operations)) {
              return;
            }
            operations.forEach(
                (method, operation) -> {
                  if (!HTTP_METHODS.contains(String.valueOf(method))
                      || !(operation instanceof Map<?, ?> operationMap)) {
                    return;
                  }
                  String operationPath = "paths." + path + "." + method;
                  if (operationMap.get("requestBody") instanceof Map<?, ?> requestBody) {
                    collectMediaSchemas(
                        requestBody.get("content"), operationPath + ".requestBody", violations);
                  }
                  if (operationMap.get("responses") instanceof Map<?, ?> responses) {
                    responses.forEach(
                        (status, response) -> {
                          if (response instanceof Map<?, ?> responseMap) {
                            collectMediaSchemas(
                                responseMap.get("content"),
                                operationPath + ".responses." + status,
                                violations);
                          }
                        });
                  }
                });
          });
    }

    assertThat(violations)
        .as("$ref must not carry a sibling null type; express nullability as anyOf [$ref, null]")
        .isEmpty();
  }

  private void collectMediaSchemas(Object content, String path, List<String> violations) {
    if (content instanceof Map<?, ?> mediaTypes) {
      mediaTypes.forEach(
          (mediaType, media) -> {
            if (media instanceof Map<?, ?> mediaMap) {
              collectNullTypedReferences(
                  mediaMap.get("schema"), path + ".content." + mediaType + ".schema", violations);
            }
          });
    }
  }

  /** Visits schema nodes only — never example/default/const/enum payloads. */
  private void collectNullTypedReferences(Object node, String path, List<String> violations) {
    if (!(node instanceof Map<?, ?> schema)) {
      return;
    }
    if (schema.containsKey("$ref") && typeIncludesNull(schema.get("type"))) {
      violations.add(path + " -> " + schema);
    }
    schema.forEach(
        (key, value) -> {
          String keyword = String.valueOf(key);
          if (SCHEMA_KEYWORDS.contains(keyword)) {
            collectNullTypedReferences(value, path + "." + keyword, violations);
          } else if (SCHEMA_LIST_KEYWORDS.contains(keyword) && value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
              collectNullTypedReferences(
                  list.get(index), path + "." + keyword + "[" + index + "]", violations);
            }
          } else if (SCHEMA_MAP_KEYWORDS.contains(keyword) && value instanceof Map<?, ?> map) {
            map.forEach(
                (name, child) ->
                    collectNullTypedReferences(
                        child, path + "." + keyword + "." + name, violations));
          }
        });
  }

  private static boolean typeIncludesNull(Object type) {
    return "null".equals(type) || (type instanceof List<?> types && types.contains("null"));
  }

  /**
   * Expected shape, per field: each inventory row is exactly {@code anyOf: [{$ref: target}, {type:
   * "null"}]} with no outer {@code $ref}/{@code type}; the control row keeps its plain {@code
   * $ref}.
   */
  private void assertNullableReferenceContract(Map<String, Object> document) {
    for (NullableReference expected : NULLABLE_REFERENCES) {
      String field = expected.schema() + "." + expected.property();
      Map<String, Object> property =
          schemaProperty(document, expected.schema(), expected.property());

      assertThat(property).as("%s outer $ref", field).doesNotContainKey("$ref");
      assertThat(property).as("%s outer type", field).doesNotContainKey("type");
      assertThat(property.get("anyOf")).as("%s anyOf", field).isInstanceOf(List.class);

      Map<String, Object> referenceArm = new LinkedHashMap<>();
      referenceArm.put("$ref", SCHEMA_REF_PREFIX + expected.target());
      Map<String, Object> nullArm = new LinkedHashMap<>();
      nullArm.put("type", "null");
      List<Object> anyOf = new ArrayList<>((List<?>) property.get("anyOf"));
      assertThat(anyOf)
          .as("%s must be anyOf [{$ref: %s}, {type: \"null\"}]", field, expected.target())
          .containsExactly(referenceArm, nullArm);
      assertThat(mapAt(document, "components", "schemas", expected.target()))
          .as("%s reference target exists", field)
          .isNotNull();
    }

    Map<String, Object> control =
        schemaProperty(document, NON_NULLABLE_CONTROL.schema(), NON_NULLABLE_CONTROL.property());
    assertThat(control)
        .as(
            "non-nullable control %s.%s",
            NON_NULLABLE_CONTROL.schema(), NON_NULLABLE_CONTROL.property())
        .containsEntry("$ref", SCHEMA_REF_PREFIX + NON_NULLABLE_CONTROL.target())
        .doesNotContainKey("anyOf")
        .doesNotContainKey("type");
  }

  /** A retired HTTP schema cannot return unnoticed; the real export remains untouched. */
  private void assertRetiredQuantityAcceptanceRedProbe(String generatedSpec) throws Exception {
    Map<String, Object> restored = parse(generatedSpec);
    mapAt(restored, "components", "schemas").put("QuantityAcceptance", Map.of("type", "object"));
    assertThatThrownBy(() -> assertDraftStockSelectionContract(restored))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("QuantityAcceptance");
  }

  /** Every probe parses its own copy; the exported document is never mutated. */
  private void assertNullableReferenceRedProbes(String generatedSpec) throws Exception {
    NullableReference row = NULLABLE_REFERENCE_PROBE;
    String target = SCHEMA_REF_PREFIX + row.target();

    // 1. The old $ref + sibling type "null" pair is rejected by both assertions.
    Map<String, Object> oldPair = parse(generatedSpec);
    Map<String, Object> reverted = schemaProperty(oldPair, row.schema(), row.property());
    reverted.clear();
    reverted.put("type", "null");
    reverted.put("$ref", target);
    assertThatThrownBy(() -> assertNoNullTypedReferences(oldPair))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(row.schema());
    assertThatThrownBy(() -> assertNullableReferenceContract(oldPair))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(row.schema() + "." + row.property());

    // 2. Missing null arm.
    Map<String, Object> missingNullArm = parse(generatedSpec);
    ((List<?>) schemaProperty(missingNullArm, row.schema(), row.property()).get("anyOf")).remove(1);
    assertThatThrownBy(() -> assertNullableReferenceContract(missingNullArm))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(row.schema() + "." + row.property());

    // 3. Null arm written as a JSON null value instead of the type name "null".
    Map<String, Object> nullValueArm = parse(generatedSpec);
    nullArmOf(nullValueArm, row).put("type", null);
    assertThatThrownBy(() -> assertNullableReferenceContract(nullValueArm))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(row.schema() + "." + row.property());

    // 4. Wrong reference target.
    Map<String, Object> wrongTarget = parse(generatedSpec);
    referenceArmOf(wrongTarget, row).put("$ref", SCHEMA_REF_PREFIX + "OrderCoverEvidence");
    assertThatThrownBy(() -> assertNullableReferenceContract(wrongTarget))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(row.schema() + "." + row.property());

    // 5. Bad inline path schemas (response root with a type array; request body nested property).
    Map<String, Object> badResponse = parse(generatedSpec);
    mapAt(
            badResponse,
            "paths",
            "/api/v1/platform/permissions/catalogue",
            "get",
            "responses",
            "200",
            "content",
            "*/*",
            "schema")
        .put("type", List.of("object", "null"));
    assertThatThrownBy(() -> assertNoNullTypedReferences(badResponse))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("paths./api/v1/platform/permissions/catalogue.get.responses.200");

    Map<String, Object> badRequest = parse(generatedSpec);
    Map<String, Object> requestSchema =
        mapAt(
            badRequest,
            "paths",
            "/api/v1/common/users/{id}/nav-preferences/import",
            "post",
            "requestBody",
            "content",
            "application/json",
            "schema");
    Map<String, Object> nestedNullTypedRef = new LinkedHashMap<>();
    nestedNullTypedRef.put("type", "null");
    nestedNullTypedRef.put("$ref", target);
    Map<String, Object> inlineProperties = new LinkedHashMap<>();
    inlineProperties.put("injected", nestedNullTypedRef);
    requestSchema.clear();
    requestSchema.put("type", "object");
    requestSchema.put("properties", inlineProperties);
    assertThatThrownBy(() -> assertNoNullTypedReferences(badRequest))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(
            "paths./api/v1/common/users/{id}/nav-preferences/import.post.requestBody");

    // 6. Example payloads are data, not schema nodes: keys named $ref/type there are ignored.
    Map<String, Object> examplePayload = parse(generatedSpec);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("$ref", target);
    payload.put("type", "null");
    mapAt(examplePayload, "components", "schemas", row.schema()).put("example", payload);
    assertNoNullTypedReferences(examplePayload);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> referenceArmOf(Map<String, Object> document, NullableReference row) {
    List<?> anyOf = (List<?>) schemaProperty(document, row.schema(), row.property()).get("anyOf");
    return (Map<String, Object>) anyOf.get(0);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> nullArmOf(Map<String, Object> document, NullableReference row) {
    List<?> anyOf = (List<?>) schemaProperty(document, row.schema(), row.property()).get("anyOf");
    return (Map<String, Object>) anyOf.get(1);
  }

  private static Map<String, Object> parse(String spec) throws Exception {
    return new YAMLMapper().readValue(spec, new TypeReference<Map<String, Object>>() {});
  }

  private Map<String, Object> schemaProperty(
      Map<String, Object> document, String schemaName, String propertyName) {
    return mapAt(document, "components", "schemas", schemaName, "properties", propertyName);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> mapAt(Map<String, Object> root, String... path) {
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
