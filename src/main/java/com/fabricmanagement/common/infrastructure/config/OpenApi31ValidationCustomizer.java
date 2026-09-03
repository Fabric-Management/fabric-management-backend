package com.fabricmanagement.common.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Schema;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/** Restores numeric exclusive bounds when Swagger Core builds an OpenAPI 3.1 document. */
@Component
public class OpenApi31ValidationCustomizer implements OpenApiCustomizer {

  @Override
  public void customise(OpenAPI openApi) {
    if (!isOpenApi31(openApi)
        || openApi.getComponents() == null
        || openApi.getComponents().getSchemas() == null) {
      return;
    }

    Set<Schema<?>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    openApi.getComponents().getSchemas().values().forEach(schema -> visitSchema(schema, visited));
  }

  private boolean isOpenApi31(OpenAPI openApi) {
    if (openApi == null) {
      return false;
    }

    return openApi.getSpecVersion() == SpecVersion.V31
        || (openApi.getOpenapi() != null && openApi.getOpenapi().startsWith("3.1"));
  }

  private void visitSchema(Schema<?> schema, Set<Schema<?>> visited) {
    if (schema == null || !visited.add(schema)) {
      return;
    }

    convertExclusiveBounds(schema);

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
}
