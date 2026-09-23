package com.fabricmanagement.common.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Repairs Swagger Core output for OpenAPI 3.1 documents in one schema walk.
 *
 * <ul>
 *   <li>Restores numeric exclusive bounds ({@code exclusiveMinimum: true} + {@code minimum} becomes
 *       the numeric {@code exclusiveMinimum}).
 *   <li>Rewrites a nullable reference ({@code $ref} with a {@code "null"} member in {@code type})
 *       into {@code anyOf: [{$ref}, {type: "null"}]}. Swagger Core 2.2.52 records {@code nullable =
 *       true} on the 3.1 path as {@code addType("null")} beside the {@code $ref}; generators
 *       resolve the reference and drop the sibling null type (OPENAPI-NULLREF-1).
 * </ul>
 *
 * <p>The walk covers {@code components.schemas} and the inline request-body and response media
 * schemas under {@code paths}, sharing one identity-based visited set, and descends into every
 * schema-valued keyword (properties, items, compositions, not, additionalProperties and the 3.1
 * applicators prefixItems, contains, patternProperties, propertyNames, dependentSchemas,
 * if/then/else, unevaluated*, additionalItems, contentSchema). Remote references are never fetched.
 */
@Component
public class OpenApi31ValidationCustomizer implements OpenApiCustomizer {

  private static final String NULL_TYPE = "null";

  @Override
  public void customise(OpenAPI openApi) {
    if (!isOpenApi31(openApi)) {
      return;
    }

    Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
      openApi.getComponents().getSchemas().values().forEach(schema -> visitSchema(schema, visited));
    }
    if (openApi.getPaths() != null) {
      openApi
          .getPaths()
          .values()
          .forEach(
              pathItem ->
                  pathItem
                      .readOperations()
                      .forEach(operation -> visitOperation(operation, visited)));
    }
  }

  private boolean isOpenApi31(OpenAPI openApi) {
    if (openApi == null) {
      return false;
    }

    return openApi.getSpecVersion() == SpecVersion.V31
        || (openApi.getOpenapi() != null && openApi.getOpenapi().startsWith("3.1"));
  }

  private void visitOperation(Operation operation, Set<Schema<?>> visited) {
    if (operation.getRequestBody() != null) {
      visitContent(operation.getRequestBody().getContent(), visited);
    }
    if (operation.getResponses() != null) {
      operation.getResponses().values().forEach(response -> visitResponse(response, visited));
    }
  }

  private void visitResponse(ApiResponse response, Set<Schema<?>> visited) {
    if (response != null) {
      visitContent(response.getContent(), visited);
    }
  }

  private void visitContent(Content content, Set<Schema<?>> visited) {
    if (content != null) {
      content
          .values()
          .forEach(
              mediaType -> {
                if (mediaType != null) {
                  visitSchema(mediaType.getSchema(), visited);
                }
              });
    }
  }

  private void visitSchema(Schema<?> schema, Set<Schema<?>> visited) {
    if (schema == null || !visited.add(schema)) {
      return;
    }

    convertExclusiveBounds(schema);
    repairNullableReference(schema);

    if (schema.getProperties() != null) {
      schema.getProperties().values().forEach(property -> visitSchema(property, visited));
    }

    visitSchema(schema.getItems(), visited);
    visitSchemas(schema.getAllOf(), visited);
    visitSchemas(schema.getAnyOf(), visited);
    visitSchemas(schema.getOneOf(), visited);
    visitSchema(schema.getNot(), visited);

    if (schema.getAdditionalProperties() instanceof Schema<?> additionalPropertiesSchema) {
      visitSchema(additionalPropertiesSchema, visited);
    }

    // OpenAPI 3.1 / JSON Schema 2020-12 applicators — the same set OpenApiExportIT scans.
    visitSchemas(schema.getPrefixItems(), visited);
    visitSchema(schema.getContains(), visited);
    visitSchema(schema.getAdditionalItems(), visited);
    visitSchema(schema.getUnevaluatedItems(), visited);
    if (schema.getPatternProperties() != null) {
      visitSchemas(schema.getPatternProperties().values(), visited);
    }
    visitSchema(schema.getPropertyNames(), visited);
    visitSchema(schema.getUnevaluatedProperties(), visited);
    if (schema.getDependentSchemas() != null) {
      visitSchemas(schema.getDependentSchemas().values(), visited);
    }
    visitSchema(schema.getIf(), visited);
    visitSchema(schema.getThen(), visited);
    visitSchema(schema.getElse(), visited);
    visitSchema(schema.getContentSchema(), visited);
  }

  // Swagger Core exposes composed schemas as List<Schema>.
  @SuppressWarnings("rawtypes")
  private void visitSchemas(Iterable<Schema> schemas, Set<Schema<?>> visited) {
    if (schemas != null) {
      schemas.forEach(schema -> visitSchema(schema, visited));
    }
  }

  private void convertExclusiveBounds(Schema<?> schema) {
    BigDecimal minimum = schema.getMinimum();
    if (minimum != null && Boolean.TRUE.equals(schema.getExclusiveMinimum())) {
      schema.setExclusiveMinimumValue(minimum);
      schema.setMinimum(null);
      schema.setExclusiveMinimum(null);
    }

    BigDecimal maximum = schema.getMaximum();
    if (maximum != null && Boolean.TRUE.equals(schema.getExclusiveMaximum())) {
      schema.setExclusiveMaximumValue(maximum);
      schema.setMaximum(null);
      schema.setExclusiveMaximum(null);
    }
  }

  /**
   * Rewrites {@code {$ref: X, type: [..., "null"], ...}} in place (so parent {@code required}
   * membership and shared instances stay intact) into {@code {anyOf: [{$ref: X, ...constraints},
   * {type: "null"}], ...metadata}}. Metadata (description, title, example(s), default, deprecated,
   * read/write flags, extensions, $comment, externalDocs, xml, identifiers) stays on the node;
   * every validation keyword and applicator moves to the reference branch, including non-null
   * members of {@code type}.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private void repairNullableReference(Schema<?> schema) {
    Set<String> types = schema.getTypes();
    if (schema.get$ref() == null || types == null || !types.contains(NULL_TYPE)) {
      return;
    }

    Schema source = schema;
    Schema referenceBranch = new Schema<>();
    referenceBranch.setSpecVersion(schema.getSpecVersion());
    referenceBranch.set$ref(schema.get$ref());

    Set<String> nonNullTypes = new LinkedHashSet<>(types);
    nonNullTypes.remove(NULL_TYPE);
    if (!nonNullTypes.isEmpty()) {
      referenceBranch.setTypes(nonNullTypes);
    }
    if (schema.getType() != null && !NULL_TYPE.equals(schema.getType())) {
      referenceBranch.setType(schema.getType());
    }

    moveConstraints(source, referenceBranch);

    Schema<?> nullBranch = new Schema<>();
    nullBranch.setSpecVersion(schema.getSpecVersion());
    nullBranch.setTypes(new LinkedHashSet<>(List.of(NULL_TYPE)));

    source.set$ref(null);
    source.setTypes(null);
    source.setType(null);
    source.setAnyOf(new ArrayList<>(List.of(referenceBranch, nullBranch)));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void moveConstraints(Schema source, Schema target) {
    move(source::getFormat, source::setFormat, target::setFormat);
    move(source::getEnum, source::setEnum, target::setEnum);
    move(source::getConst, source::setConst, target::setConst);
    move(source::getMultipleOf, source::setMultipleOf, target::setMultipleOf);
    move(source::getMinimum, source::setMinimum, target::setMinimum);
    move(source::getMaximum, source::setMaximum, target::setMaximum);
    move(source::getExclusiveMinimum, source::setExclusiveMinimum, target::setExclusiveMinimum);
    move(source::getExclusiveMaximum, source::setExclusiveMaximum, target::setExclusiveMaximum);
    move(
        source::getExclusiveMinimumValue,
        source::setExclusiveMinimumValue,
        target::setExclusiveMinimumValue);
    move(
        source::getExclusiveMaximumValue,
        source::setExclusiveMaximumValue,
        target::setExclusiveMaximumValue);
    move(source::getMinLength, source::setMinLength, target::setMinLength);
    move(source::getMaxLength, source::setMaxLength, target::setMaxLength);
    move(source::getPattern, source::setPattern, target::setPattern);
    move(source::getContentEncoding, source::setContentEncoding, target::setContentEncoding);
    move(source::getContentMediaType, source::setContentMediaType, target::setContentMediaType);
    move(source::getContentSchema, source::setContentSchema, target::setContentSchema);
    move(source::getMinItems, source::setMinItems, target::setMinItems);
    move(source::getMaxItems, source::setMaxItems, target::setMaxItems);
    move(source::getUniqueItems, source::setUniqueItems, target::setUniqueItems);
    move(source::getMinContains, source::setMinContains, target::setMinContains);
    move(source::getMaxContains, source::setMaxContains, target::setMaxContains);
    move(source::getContains, source::setContains, target::setContains);
    move(source::getItems, source::setItems, target::setItems);
    move(source::getPrefixItems, source::setPrefixItems, target::setPrefixItems);
    move(source::getAdditionalItems, source::setAdditionalItems, target::setAdditionalItems);
    move(source::getUnevaluatedItems, source::setUnevaluatedItems, target::setUnevaluatedItems);
    move(source::getMinProperties, source::setMinProperties, target::setMinProperties);
    move(source::getMaxProperties, source::setMaxProperties, target::setMaxProperties);
    // required before properties: Swagger's setRequired filters against already-set properties.
    move(source::getRequired, source::setRequired, target::setRequired);
    move(source::getProperties, source::setProperties, target::setProperties);
    move(source::getPatternProperties, source::setPatternProperties, target::setPatternProperties);
    move(
        source::getAdditionalProperties,
        source::setAdditionalProperties,
        target::setAdditionalProperties);
    move(source::getPropertyNames, source::setPropertyNames, target::setPropertyNames);
    move(
        source::getUnevaluatedProperties,
        source::setUnevaluatedProperties,
        target::setUnevaluatedProperties);
    move(source::getDependentRequired, source::setDependentRequired, target::setDependentRequired);
    move(source::getDependentSchemas, source::setDependentSchemas, target::setDependentSchemas);
    move(source::getAllOf, source::setAllOf, target::setAllOf);
    move(source::getAnyOf, source::setAnyOf, target::setAnyOf);
    move(source::getOneOf, source::setOneOf, target::setOneOf);
    move(source::getNot, source::setNot, target::setNot);
    move(source::getIf, source::setIf, target::setIf);
    move(source::getThen, source::setThen, target::setThen);
    move(source::getElse, source::setElse, target::setElse);
    move(source::getDiscriminator, source::setDiscriminator, target::setDiscriminator);
    move(source::get$dynamicRef, source::set$dynamicRef, target::set$dynamicRef);
  }

  private static <V> void move(Supplier<V> getter, Consumer<V> clearSource, Consumer<V> target) {
    V value = getter.get();
    if (value != null) {
      target.accept(value);
      clearSource.accept(null);
    }
  }
}
