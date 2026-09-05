package com.fabricmanagement.product.yarn.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class YarnArticleApiOpenApiTest {

  private static final String BASE_PATH = "/api/v1/production/yarns";
  private static final Set<String> FORBIDDEN_REQUEST_FIELDS =
      Set.of(
          "resultantLinearDensityTex",
          "canonicalDesignation",
          "canonicalKey",
          "canonicalKeyAlgorithmVersion",
          "articleSpecVersion",
          "componentLinearDensityTex",
          "fiberIsoCode",
          "fiberName",
          "materialSource",
          "testMethodCode");

  @Test
  @SuppressWarnings("unchecked")
  void generatedYarnRequestSchemasContainNoDerivedOrSnapshotFields() throws IOException {
    Map<String, Object> document = new Yaml().load(Files.readString(Path.of("api/openapi.yaml")));
    Map<String, Object> paths = (Map<String, Object>) document.get("paths");
    Map<String, Object> components = (Map<String, Object>) document.get("components");
    Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

    Map<String, Object> yarnPaths =
        paths.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(BASE_PATH))
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    assertThat(yarnPaths).as("regenerate OpenAPI: yarn paths must exist").isNotEmpty();
    assertThat(yarnPaths).containsKey(BASE_PATH);

    List<Map<String, Object>> requestSchemas = new ArrayList<>();
    for (Object pathItemValue : yarnPaths.values()) {
      Map<String, Object> pathItem = (Map<String, Object>) pathItemValue;
      for (String method : List.of("post", "put", "patch")) {
        Object operationValue = pathItem.get(method);
        if (!(operationValue instanceof Map<?, ?> operation)) {
          continue;
        }
        Object requestBodyValue = operation.get("requestBody");
        if (!(requestBodyValue instanceof Map<?, ?> requestBody)) {
          continue;
        }
        Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
        Map<String, Object> json = (Map<String, Object>) content.get("application/json");
        requestSchemas.add((Map<String, Object>) json.get("schema"));
      }
    }
    assertThat(requestSchemas).as("yarn write operations must expose request bodies").isNotEmpty();

    Set<String> requestFields = new LinkedHashSet<>();
    Set<String> visitedRefs = new HashSet<>();
    requestSchemas.forEach(schema -> collectFields(schema, schemas, visitedRefs, requestFields));
    assertThat(requestFields).contains("productId", "originalCountSystem", "name");
    assertThat(requestFields).doesNotContainAnyElementsOf(FORBIDDEN_REQUEST_FIELDS);
  }

  @Test
  @SuppressWarnings("unchecked")
  void reconciliationAndReadinessSchemasAreTypedRequiredAndAdditive() throws IOException {
    Map<String, Object> document = new Yaml().load(Files.readString(Path.of("api/openapi.yaml")));
    Map<String, Object> paths = (Map<String, Object>) document.get("paths");
    Map<String, Object> schemas =
        (Map<String, Object>) ((Map<String, Object>) document.get("components")).get("schemas");

    assertThat(paths)
        .containsKeys(
            BASE_PATH + "/reconciliations",
            BASE_PATH + "/reconciliations/{id}/candidates",
            BASE_PATH + "/reconciliations/{id}/choose",
            BASE_PATH + "/reconciliations/{id}/dismiss",
            BASE_PATH + "/readiness");
    assertThat(schemas)
        .containsKeys(
            "YarnReconciliationChooseRequest",
            "YarnReconciliationItemDto",
            "YarnReconciliationResolvedCandidateDto",
            "YarnReconciliationCandidateGroupDto",
            "YarnReconciliationCandidatePageDto",
            "YarnReadinessDto",
            "YarnReadinessBlockerDto",
            "YarnUnlinkedOpenDocumentsDto");

    Map<String, Object> resolved =
        (Map<String, Object>) schemas.get("YarnReconciliationResolvedCandidateDto");
    assertThat((List<String>) resolved.get("required"))
        .containsExactlyInAnyOrder("rawValue", "sourceKind", "recordedAt", "sourceRecordId");
    Map<String, Object> resolvedProperties = (Map<String, Object>) resolved.get("properties");
    assertThat(((Map<String, Object>) resolvedProperties.get("rawValue")).get("type"))
        .isEqualTo("string");
    assertThat(((Map<String, Object>) resolvedProperties.get("sourceKind")).get("type"))
        .isEqualTo("string");
    assertThat(((Map<String, Object>) resolvedProperties.get("recordedAt")).get("type"))
        .isEqualTo("string");
    assertThat(((Map<String, Object>) resolvedProperties.get("sourceRecordId")).get("type"))
        .isEqualTo("string");

    Map<String, Object> item = (Map<String, Object>) schemas.get("YarnReconciliationItemDto");
    assertThat((List<String>) item.get("required")).contains("resolvedCandidate");
    Map<String, Object> itemProperties = (Map<String, Object>) item.get("properties");
    Map<String, Object> resolvedCandidate =
        (Map<String, Object>) itemProperties.get("resolvedCandidate");
    assertThat(resolvedCandidate.toString())
        .contains("YarnReconciliationResolvedCandidateDto")
        .doesNotContain("additionalProperties");
    assertThat(isNullable(resolvedCandidate)).isTrue();
  }

  @SuppressWarnings("unchecked")
  private static boolean isNullable(Map<String, Object> schema) {
    if (Boolean.TRUE.equals(schema.get("nullable"))) {
      return true;
    }
    Object type = schema.get("type");
    if ("null".equals(type)) {
      return true;
    }
    if (type instanceof List<?> types && types.contains("null")) {
      return true;
    }
    Object oneOf = schema.get("oneOf");
    return oneOf instanceof List<?> variants
        && variants.stream()
            .filter(Map.class::isInstance)
            .map(value -> (Map<String, Object>) value)
            .anyMatch(value -> "null".equals(value.get("type")));
  }

  @SuppressWarnings("unchecked")
  private static void collectFields(
      Object node,
      Map<String, Object> schemas,
      Set<String> visitedRefs,
      Set<String> requestFields) {
    if (node instanceof List<?> list) {
      list.forEach(item -> collectFields(item, schemas, visitedRefs, requestFields));
      return;
    }
    if (!(node instanceof Map<?, ?> raw)) {
      return;
    }
    Map<String, Object> map = (Map<String, Object>) raw;
    Object refValue = map.get("$ref");
    if (refValue instanceof String ref && ref.startsWith("#/components/schemas/")) {
      String schemaName = ref.substring(ref.lastIndexOf('/') + 1);
      if (visitedRefs.add(schemaName)) {
        collectFields(schemas.get(schemaName), schemas, visitedRefs, requestFields);
      }
      return;
    }
    Object propertiesValue = map.get("properties");
    if (propertiesValue instanceof Map<?, ?> properties) {
      requestFields.addAll(((Map<String, Object>) properties).keySet());
    }
    map.values().forEach(value -> collectFields(value, schemas, visitedRefs, requestFields));
  }
}
