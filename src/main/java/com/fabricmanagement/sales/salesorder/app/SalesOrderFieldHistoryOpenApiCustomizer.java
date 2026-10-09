package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.sales.salesorder.domain.OrderEditKey;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Publishes what the field-history annotations cannot state alone (CEDIT-09 §3.3):
 *
 * <ul>
 *   <li>the edit key catalogue as the closed string enum {@code SalesOrderFieldHistoryEditKey}: the
 *       wire names of {@link OrderEditKey} and {@code line} for a whole line, from the same Java
 *       catalogue the history is written with. An entry's {@code knownEditKey} is that enum or
 *       null; its {@code editKey} stays the open stored string, so an entry of a key this version
 *       does not know is still a valid answer (CEDIT-09 R1);
 *   <li>a value's {@code data} as the exact {@code anyOf} of its shapes: a string, a calendar date,
 *       a boolean, each named composite shape, and null. {@code anyOf}, not {@code oneOf}: a date
 *       is also a string, and the entry's knownEditKey decides which shape applies;
 *   <li>null among the values of a nullable inline enum of the history's schemas.
 * </ul>
 */
@Component
public class SalesOrderFieldHistoryOpenApiCustomizer implements OpenApiCustomizer {

  static final String ENTRY_SCHEMA = "SalesOrderFieldHistoryEntry";
  static final String VALUE_SCHEMA = "SalesOrderFieldHistoryValue";
  static final String EDIT_KEY_SCHEMA = "SalesOrderFieldHistoryEditKey";
  static final String HISTORY_PREFIX = "SalesOrderFieldHistory";
  private static final String REF = "#/components/schemas/";

  /** The named shapes of a value, in the order the description lists them. */
  static final Map<String, Class<?>> SHAPES = shapes();

  @Override
  @SuppressWarnings("rawtypes")
  public void customise(OpenAPI openApi) {
    if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
      return;
    }
    Map<String, Schema> schemas = openApi.getComponents().getSchemas();
    Schema entry = schemas.get(ENTRY_SCHEMA);
    Schema value = schemas.get(VALUE_SCHEMA);
    if (entry == null || value == null) {
      return; // No field-history operation in this document.
    }
    SpecVersion version = openApi.getSpecVersion();
    registerShapes(schemas, version);
    schemas.put(EDIT_KEY_SCHEMA, editKeys(version));
    replaceProperty(entry, "knownEditKey", knownEditKey(version));
    replaceProperty(value, "data", data(version));
    schemas.forEach(
        (name, schema) -> {
          if (name.startsWith(HISTORY_PREFIX)) {
            allowNullInNullableEnums(schema);
          }
        });
  }

  /**
   * A nullable inline enum (for example {@code moduleType}) is written as {@code type: [string,
   * null]} with an enum that lacks null, which JSON Schema then refuses for null. The history's own
   * schemas list null among the enum values, so a saved empty value validates as published.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void allowNullInNullableEnums(Schema schema) {
    if (schema.getProperties() == null) {
      return;
    }
    ((Map<String, Schema>) schema.getProperties())
        .values()
        .forEach(
            property -> {
              List values = property.getEnum();
              boolean nullable =
                  property.getTypes() != null && property.getTypes().contains("null");
              if (nullable && values != null && !values.contains(null)) {
                List widened = new ArrayList<>(values);
                widened.add(null);
                property.setEnum(widened);
              }
            });
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void registerShapes(Map<String, Schema> schemas, SpecVersion version) {
    if (schemas.keySet().containsAll(SHAPES.keySet())) {
      return;
    }
    ModelConverters converters = ModelConverters.getInstance(version == SpecVersion.V31);
    SHAPES.values().forEach(type -> converters.readAll(type).forEach(schemas::putIfAbsent));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Schema<?> editKeys(SpecVersion version) {
    Schema keys = typed("string", version);
    keys.setEnum(new ArrayList<>(SalesOrderFieldHistoryDtos.EDIT_KEY_CATALOGUE));
    keys.setDescription(
        "Safe-edit key wire names (CEDIT-02 §2.2/§2.3), and `line` for a whole line added or"
            + " removed.");
    return keys;
  }

  /** The catalogue key, or null for a stored key this version does not know. */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Schema<?> knownEditKey(SpecVersion version) {
    Schema known = schema(version);
    known.setAnyOf(new ArrayList<>(List.of(ref(EDIT_KEY_SCHEMA, version), typed("null", version))));
    return known;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Schema<?> data(SpecVersion version) {
    List<Schema> arms = new ArrayList<>();
    arms.add(typed("string", version));
    Schema date = typed("string", version);
    date.setFormat("date");
    arms.add(date);
    arms.add(typed("boolean", version));
    SHAPES.keySet().forEach(name -> arms.add(ref(name, version)));
    arms.add(typed("null", version));
    Schema data = schema(version);
    data.setAnyOf(arms);
    data.setDescription(
        "The value in the shape its entry's editKey names; null when KNOWN as empty and always"
            + " null when UNAVAILABLE.");
    return data;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void replaceProperty(Schema owner, String name, Schema<?> replacement) {
    Map<String, Schema> properties =
        owner.getProperties() == null ? new LinkedHashMap<>() : owner.getProperties();
    Schema previous = properties.get(name);
    if (previous != null && previous.getDescription() != null) {
      String described = replacement.getDescription();
      replacement.setDescription(
          described == null
              ? previous.getDescription()
              : previous.getDescription() + " " + described);
    }
    properties.put(name, replacement);
    owner.setProperties(properties);
  }

  private static Map<String, Class<?>> shapes() {
    Map<String, Class<?>> shapes = new LinkedHashMap<>();
    shapes.put(
        "SalesOrderFieldHistoryRequestedDate", SalesOrderFieldHistoryDtos.RequestedDate.class);
    shapes.put(
        "SalesOrderFieldHistoryDeliveryTerms", SalesOrderFieldHistoryDtos.DeliveryTerms.class);
    shapes.put("SalesOrderFieldHistoryAgreement", SalesOrderFieldHistoryDtos.Agreement.class);
    shapes.put("SalesOrderFieldHistoryContact", SalesOrderFieldHistoryDtos.Contact.class);
    shapes.put("SalesOrderFieldHistoryWidth", SalesOrderFieldHistoryDtos.Width.class);
    shapes.put("SalesOrderFieldHistoryQuantity", SalesOrderFieldHistoryDtos.Quantity.class);
    shapes.put("SalesOrderFieldHistoryPricing", SalesOrderFieldHistoryDtos.Pricing.class);
    shapes.put("SalesOrderFieldHistoryTolerance", SalesOrderFieldHistoryDtos.Tolerance.class);
    shapes.put(
        "SalesOrderFieldHistorySpecification", SalesOrderFieldHistoryDtos.Specification.class);
    shapes.put("SalesOrderFieldHistoryLine", SalesOrderFieldHistoryDtos.Line.class);
    return java.util.Collections.unmodifiableMap(shapes);
  }

  private static Schema<Object> ref(String name, SpecVersion version) {
    Schema<Object> reference = schema(version);
    reference.set$ref(REF + name);
    return reference;
  }

  private static Schema<Object> typed(String type, SpecVersion version) {
    Schema<Object> schema = schema(version);
    schema.setTypes(new LinkedHashSet<>(List.of(type)));
    schema.setType(type);
    return schema;
  }

  private static Schema<Object> schema(SpecVersion version) {
    Schema<Object> schema = new Schema<>();
    schema.setSpecVersion(version == null ? SpecVersion.V31 : version);
    return schema;
  }
}
