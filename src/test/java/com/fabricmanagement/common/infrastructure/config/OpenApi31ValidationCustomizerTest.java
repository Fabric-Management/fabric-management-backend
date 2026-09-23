package com.fabricmanagement.common.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpenApi31ValidationCustomizerTest {

  private static final String TARGET_REF =
      "#/components/schemas/OrderCoverSelectionPreviewRejection";

  private final OpenApi31ValidationCustomizer customizer = new OpenApi31ValidationCustomizer();

  @Test
  void convertsExclusiveMinimumToOpenApi31NumericBound() {
    Schema<?> schema = new Schema<>();
    schema.setMinimum(BigDecimal.ZERO);
    schema.setExclusiveMinimum(true);

    customizer.customise(openApi(SpecVersion.V31, schema));

    assertThat(schema.getExclusiveMinimumValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(schema.getMinimum()).isNull();
    assertThat(schema.getExclusiveMinimum()).isNull();
  }

  @Test
  void convertsExclusiveMaximumToOpenApi31NumericBound() {
    Schema<?> schema = new Schema<>();
    schema.setMaximum(BigDecimal.valueOf(100));
    schema.setExclusiveMaximum(true);

    customizer.customise(openApi(SpecVersion.V31, schema));

    assertThat(schema.getExclusiveMaximumValue()).isEqualByComparingTo("100");
    assertThat(schema.getMaximum()).isNull();
    assertThat(schema.getExclusiveMaximum()).isNull();
  }

  @Test
  void leavesInclusiveMinimumUnchanged() {
    Schema<?> schema = new Schema<>();
    schema.setMinimum(BigDecimal.TEN);
    schema.setExclusiveMinimum(false);

    customizer.customise(openApi(SpecVersion.V31, schema));

    assertThat(schema.getMinimum()).isEqualByComparingTo(BigDecimal.TEN);
    assertThat(schema.getExclusiveMinimum()).isFalse();
    assertThat(schema.getExclusiveMinimumValue()).isNull();
  }

  @Test
  void leavesPositiveOrZeroStyleMinimumInclusive() {
    Schema<?> schema = new Schema<>();
    schema.setMinimum(BigDecimal.ZERO);

    customizer.customise(openApi(SpecVersion.V31, schema));

    assertThat(schema.getMinimum()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(schema.getExclusiveMinimum()).isNull();
    assertThat(schema.getExclusiveMinimumValue()).isNull();
  }

  @Test
  void traversesNestedPropertiesArrayItemsAndComposedSchemasWithoutCycles() {
    Schema<?> itemSchema = new Schema<>();
    itemSchema.setMinimum(BigDecimal.ZERO);
    itemSchema.setExclusiveMinimum(true);

    Schema<?> nestedSchema = new Schema<>();
    nestedSchema.addProperty("values", new ArraySchema().items(itemSchema));

    Schema<?> rootSchema = new Schema<>();
    rootSchema.addAllOfItem(nestedSchema);
    nestedSchema.addProperty("parent", rootSchema);

    customizer.customise(openApi(SpecVersion.V31, rootSchema));

    assertThat(itemSchema.getExclusiveMinimumValue()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(itemSchema.getMinimum()).isNull();
    assertThat(itemSchema.getExclusiveMinimum()).isNull();
  }

  @Test
  void doesNotModifyOpenApi30Document() {
    Schema<?> schema = new Schema<>();
    schema.setMinimum(BigDecimal.ZERO);
    schema.setExclusiveMinimum(true);

    customizer.customise(openApi(SpecVersion.V30, schema));

    assertThat(schema.getMinimum()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(schema.getExclusiveMinimum()).isTrue();
    assertThat(schema.getExclusiveMinimumValue()).isNull();
  }

  // --- OPENAPI-NULLREF-1: nullable references ----------------------------------------------

  @Test
  void rewritesNullableReferenceToAnyOfReferenceAndNullType() {
    Schema<?> property = nullableRef(TARGET_REF);
    Schema<?> owner = new Schema<>().addProperty("rejection", property);
    owner.setRequired(List.of("rejection"));

    customizer.customise(openApi(SpecVersion.V31, owner));

    JsonNode rendered = render(owner.getProperties().get("rejection"));
    assertThat(rendered.has("$ref")).isFalse();
    assertThat(rendered.has("type")).isFalse();
    assertThat(rendered.get("anyOf")).hasSize(2);
    assertThat(rendered.get("anyOf").get(0).get("$ref").asText()).isEqualTo(TARGET_REF);
    assertThat(rendered.get("anyOf").get(0).has("type")).isFalse();
    assertThat(rendered.get("anyOf").get(1).get("type").isTextual()).isTrue();
    assertThat(rendered.get("anyOf").get(1).get("type").asText()).isEqualTo("null");
    assertThat(rendered.get("anyOf").get(1).has("$ref")).isFalse();
    // Repaired in place: same instance under the same key, parent required membership intact.
    assertThat(owner.getProperties().get("rejection")).isSameAs(property);
    assertThat(owner.getRequired()).containsExactly("rejection");
  }

  @Test
  void leavesNonNullableReferenceByteIdentical() throws JsonProcessingException {
    Schema<?> reference = new Schema<>().$ref("#/components/schemas/OrderCoverCase");
    reference.setDescription("The case");
    Schema<?> owner = new Schema<>().addProperty("case", reference);
    String before = Json31.mapper().writeValueAsString(owner);

    customizer.customise(openApi(SpecVersion.V31, owner));

    assertThat(Json31.mapper().writeValueAsString(owner)).isEqualTo(before);
    assertThat(reference.get$ref()).isEqualTo("#/components/schemas/OrderCoverCase");
    assertThat(reference.getAnyOf()).isNull();
  }

  @Test
  void leavesNullableScalarUnchanged() throws JsonProcessingException {
    Schema<?> scalar = new Schema<>();
    scalar.setTypes(new LinkedHashSet<>(List.of("string", "null")));
    scalar.setFormat("date");
    Schema<?> owner = new Schema<>().addProperty("dueDate", scalar);
    String before = Json31.mapper().writeValueAsString(owner);

    customizer.customise(openApi(SpecVersion.V31, owner));

    assertThat(Json31.mapper().writeValueAsString(owner)).isEqualTo(before);
    assertThat(scalar.getAnyOf()).isNull();
  }

  @Test
  void doesNotMatchReferenceWithNullTypes() throws JsonProcessingException {
    // OpenAPI 3.0-style marker only: getTypes() == null is outside the 3.1 repair predicate.
    Schema<?> reference = new Schema<>().$ref(TARGET_REF);
    reference.setNullable(true);
    Schema<?> owner = new Schema<>().addProperty("rejection", reference);
    String before = Json31.mapper().writeValueAsString(owner);

    customizer.customise(openApi(SpecVersion.V31, owner));

    assertThat(reference.getTypes()).isNull();
    assertThat(reference.get$ref()).isEqualTo(TARGET_REF);
    assertThat(reference.getAnyOf()).isNull();
    assertThat(Json31.mapper().writeValueAsString(owner)).isEqualTo(before);
  }

  @Test
  void repairsNullableReferenceUnderItemsAllOfAndAdditionalProperties() {
    Schema<?> underItems = nullableRef("#/components/schemas/ItemTarget");
    Schema<?> underAllOf = nullableRef("#/components/schemas/AllOfTarget");
    Schema<?> underAdditional = nullableRef("#/components/schemas/MapTarget");

    Schema<?> root = new Schema<>();
    root.addProperty("list", new ArraySchema().items(underItems));
    root.addAllOfItem(underAllOf);
    root.addProperty("byKey", new Schema<>().additionalProperties(underAdditional));

    customizer.customise(openApi(SpecVersion.V31, root));

    assertRepaired(underItems, "#/components/schemas/ItemTarget");
    assertRepaired(underAllOf, "#/components/schemas/AllOfTarget");
    assertRepaired(underAdditional, "#/components/schemas/MapTarget");
  }

  @Test
  void repairsNullableReferenceUnderPrefixItemsAndContains() {
    Schema<?> underPrefixItems = nullableRef("#/components/schemas/PrefixTarget");
    Schema<?> underContains = nullableRef("#/components/schemas/ContainsTarget");

    Schema<?> tuple = new Schema<>();
    tuple.addPrefixItem(underPrefixItems);
    tuple.setContains(underContains);
    Schema<?> root = new Schema<>().addProperty("tuple", tuple);

    customizer.customise(openApi(SpecVersion.V31, root));

    assertRepaired(underPrefixItems, "#/components/schemas/PrefixTarget");
    assertRepaired(underContains, "#/components/schemas/ContainsTarget");
  }

  @Test
  void terminatesOnCyclicSchemaAndRepairsInsideTheCycle() {
    Schema<?> nullable = nullableRef(TARGET_REF);
    Schema<?> nested = new Schema<>().addProperty("rejection", nullable);
    Schema<?> root = new Schema<>();
    root.addAllOfItem(nested);
    nested.addProperty("parent", root);
    nested.addProperty("self", nested);

    customizer.customise(openApi(SpecVersion.V31, root));

    assertRepaired(nullable, TARGET_REF);
  }

  @Test
  void repairsInlineRequestAndResponseSchemasWithoutComponents() {
    Schema<?> requestRoot = nullableRef("#/components/schemas/RequestTarget");
    Schema<?> responseProperty = nullableRef("#/components/schemas/ResponseTarget");
    Schema<?> responseRoot = new Schema<>().addProperty("data", responseProperty);

    OpenAPI openApi = new OpenAPI(SpecVersion.V31);
    openApi.setPaths(
        new Paths()
            .addPathItem(
                "/api/v1/things",
                new PathItem()
                    .post(
                        new Operation()
                            .requestBody(new RequestBody().content(json(requestRoot)))
                            .responses(
                                new ApiResponses()
                                    .addApiResponse(
                                        "200", new ApiResponse().content(json(responseRoot)))))));
    assertThat(openApi.getComponents()).isNull();

    customizer.customise(openApi);

    assertRepaired(requestRoot, "#/components/schemas/RequestTarget");
    assertRepaired(responseProperty, "#/components/schemas/ResponseTarget");
  }

  @Test
  void sharesOneVisitedSetAcrossComponentAndPathRoots() {
    Schema<?> shared = nullableRef(TARGET_REF);
    OpenAPI openApi = openApi(SpecVersion.V31, new Schema<>().addProperty("rejection", shared));
    openApi.setPaths(
        new Paths()
            .addPathItem(
                "/api/v1/things",
                new PathItem()
                    .get(
                        new Operation()
                            .responses(
                                new ApiResponses()
                                    .addApiResponse(
                                        "200", new ApiResponse().content(json(shared)))))));

    customizer.customise(openApi);

    assertRepaired(shared, TARGET_REF);
    assertThat(shared.getAnyOf()).hasSize(2);
  }

  @Test
  void preservesMetadataOnNodeAndMovesSiblingConstraintsToReferenceBranch() {
    Schema<Object> property = new Schema<>();
    property.set$ref(TARGET_REF);
    property.setTypes(new LinkedHashSet<>(List.of("object", "null")));
    property.setDescription("Rejection; null when the preview is accepted");
    property.setTitle("Rejection");
    property.setExamples(List.of(Map.of("code", "X")));
    property.setDeprecated(true);
    property.setReadOnly(true);
    property.setWriteOnly(false);
    property.addExtension("x-owner", "sales");
    property.setMinProperties(1);

    Schema<?> owner = new Schema<>().addProperty("rejection", property);
    owner.setRequired(List.of("rejection"));

    customizer.customise(openApi(SpecVersion.V31, owner));

    assertThat(property.getDescription()).isEqualTo("Rejection; null when the preview is accepted");
    assertThat(property.getTitle()).isEqualTo("Rejection");
    assertThat(property.getExamples()).containsExactly(Map.of("code", "X"));
    assertThat(property.getDeprecated()).isTrue();
    assertThat(property.getReadOnly()).isTrue();
    assertThat(property.getWriteOnly()).isFalse();
    assertThat(property.getExtensions()).containsEntry("x-owner", "sales");
    assertThat(property.get$ref()).isNull();
    assertThat(property.getTypes()).isNull();
    assertThat(property.getMinProperties()).isNull();
    assertThat(owner.getRequired()).containsExactly("rejection");

    Schema<?> referenceBranch = property.getAnyOf().get(0);
    assertThat(referenceBranch.get$ref()).isEqualTo(TARGET_REF);
    assertThat(referenceBranch.getTypes()).containsExactly("object");
    assertThat(referenceBranch.getMinProperties()).isEqualTo(1);
    assertThat(referenceBranch.getDescription()).isNull();

    Schema<?> nullBranch = property.getAnyOf().get(1);
    assertThat(nullBranch.getTypes()).containsExactly("null");
    assertThat(nullBranch.get$ref()).isNull();

    JsonNode rendered = render(property);
    assertThat(rendered.has("type")).isFalse();
    assertThat(rendered.get("anyOf").get(0).get("type").asText()).isEqualTo("object");
    assertThat(rendered.get("anyOf").get(1).get("type").asText()).isEqualTo("null");
  }

  @Test
  void secondPassProducesNoFurtherChange() throws JsonProcessingException {
    Schema<?> property = nullableRef(TARGET_REF);
    property.setDescription("Batch colour; null for explicitly colourless stock");
    Schema<?> owner = new Schema<>().addProperty("colour", property);
    owner.addProperty("list", new ArraySchema().items(nullableRef("#/components/schemas/Item")));
    OpenAPI openApi = openApi(SpecVersion.V31, owner);

    customizer.customise(openApi);
    String afterFirstPass = Json31.mapper().writeValueAsString(openApi);
    customizer.customise(openApi);

    assertThat(Json31.mapper().writeValueAsString(openApi)).isEqualTo(afterFirstPass);
  }

  @Test
  void doesNotRepairNullableReferenceInOpenApi30Document() {
    Schema<?> property = nullableRef(TARGET_REF);

    customizer.customise(openApi(SpecVersion.V30, new Schema<>().addProperty("r", property)));

    assertThat(property.get$ref()).isEqualTo(TARGET_REF);
    assertThat(property.getTypes()).containsExactly("null");
    assertThat(property.getAnyOf()).isNull();
  }

  private static Schema<?> nullableRef(String ref) {
    Schema<?> schema = new Schema<>().$ref(ref);
    // Swagger Core 2.2.52 records @Schema(nullable = true) on the 3.1 path as addType("null").
    schema.addType("null");
    return schema;
  }

  private static void assertRepaired(Schema<?> schema, String expectedRef) {
    assertThat(schema.get$ref()).isNull();
    assertThat(schema.getTypes()).isNull();
    assertThat(schema.getAnyOf()).hasSize(2);
    assertThat(schema.getAnyOf().get(0).get$ref()).isEqualTo(expectedRef);
    assertThat(schema.getAnyOf().get(0).getTypes()).isNull();
    assertThat(schema.getAnyOf().get(1).getTypes()).isEqualTo(Set.of("null"));
    assertThat(schema.getAnyOf().get(1).get$ref()).isNull();
  }

  private static JsonNode render(Schema<?> schema) {
    return Json31.mapper().valueToTree(schema);
  }

  private static Content json(Schema<?> schema) {
    return new Content().addMediaType("application/json", new MediaType().schema(schema));
  }

  private OpenAPI openApi(SpecVersion specVersion, Schema<?> schema) {
    return new OpenAPI(specVersion).components(new Components().addSchemas("TestSchema", schema));
  }
}
