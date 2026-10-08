package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.sales.salesorder.domain.OrderEditKey;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacetValue;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Publishes what the safe-edit annotations cannot state alone, so that the document and the server
 * refuse the same requests (CEDIT-02 §4.2, S15.15):
 *
 * <ul>
 *   <li>the server's limit of line operations per save, as {@code maxItems} of the request's lines;
 *   <li>the length limit of each limited header text, as {@code maxLength} of that key's value only
 *       ({@code allOf} the shared text instruction and the key's limit), from the same limits the
 *       request parse applies;
 *   <li>closed requirement-facet value types: each subtype is one object with its own {@code
 *       valueType} (the one name it is read under), its fields and {@code additionalProperties:
 *       false}; the parent is the {@code oneOf} of the subtypes with the discriminator mapping.
 *       Springdoc otherwise writes each subtype as {@code allOf} the parent, where {@code
 *       valueType} lives, which cannot be closed without refusing every valid value.
 *   <li>the lease key catalogue (CEDIT-07) as a closed string enum of the wire names, taken from
 *       the same Java catalogue the server reads with, so the document never drifts from it.
 * </ul>
 *
 * The typed 409 problem schema comes from the save endpoint's response annotation.
 */
@Component
public class SalesOrderEditOpenApiCustomizer implements OpenApiCustomizer {

  static final String REQUEST_SCHEMA = "SalesOrderEditRequest";
  static final String HEADER_SCHEMA = "SalesOrderHeaderEdits";
  static final String FACET_VALUE_SCHEMA = "RequirementFacetValue";
  static final String LEASE_FIELD_SCHEMA = "SalesOrderEditLeaseField";
  static final String DISCRIMINATOR = "valueType";
  private static final String REF = "#/components/schemas/";

  private final SalesOrderEditProperties properties;

  public SalesOrderEditOpenApiCustomizer(SalesOrderEditProperties properties) {
    this.properties = properties;
  }

  @Override
  public void customise(OpenAPI openApi) {
    Map<String, Schema> schemas =
        openApi.getComponents() == null ? null : openApi.getComponents().getSchemas();
    if (schemas == null) {
      return;
    }
    limitLineOperations(schemas.get(REQUEST_SCHEMA));
    limitHeaderTexts(schemas.get(HEADER_SCHEMA));
    closeFacetValueTypes(schemas);
    leaseFieldValues(schemas.get(LEASE_FIELD_SCHEMA));
  }

  /** The lease keys are exactly the catalogue's wire names. */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void leaseFieldValues(Schema leaseField) {
    if (leaseField == null) {
      return;
    }
    leaseField.setTypes(new LinkedHashSet<>(List.of("string")));
    leaseField.setType("string");
    leaseField.setEnum(
        new ArrayList<>(
            java.util.Arrays.stream(
                    com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseField.values())
                .map(com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseField::wireName)
                .toList()));
  }

  private void limitLineOperations(Schema<?> request) {
    if (request == null || request.getProperties() == null) {
      return;
    }
    Schema<?> lines = request.getProperties().get("lines");
    if (lines != null) {
      lines.setMaxItems(properties.getMaxLineOperations());
    }
  }

  /** Each limited key's value gets its own maxLength; the shared text instruction stays as is. */
  private static void limitHeaderTexts(Schema<?> header) {
    if (header == null || header.getProperties() == null) {
      return;
    }
    Map<OrderEditKey, Integer> limits = SalesOrderEditInstructions.textLimits();
    limits.forEach(
        (key, limit) -> {
          Schema<?> property = header.getProperties().get(key.wireName());
          if (property != null && property.get$ref() != null) {
            header.getProperties().put(key.wireName(), limited(property, limit));
          }
        });
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Schema<?> limited(Schema<?> reference, int limit) {
    SpecVersion version = reference.getSpecVersion();
    Schema instruction = schema(version);
    instruction.set$ref(reference.get$ref());

    Schema value = schema(version);
    value.setTypes(new LinkedHashSet<>(List.of("string")));
    value.setType("string");
    value.setMaxLength(limit);

    Schema keyLimit = schema(version);
    keyLimit.setTypes(new LinkedHashSet<>(List.of("object")));
    keyLimit.setType("object");
    Map<String, Schema> keyProperties = new LinkedHashMap<>();
    keyProperties.put("value", value);
    keyLimit.setProperties(keyProperties);

    Schema composed = schema(version);
    composed.setAllOf(new ArrayList<>(List.of(instruction, keyLimit)));
    composed.setDescription(reference.getDescription());
    return composed;
  }

  /**
   * Rewrites the ten facet value subtypes into closed objects that carry their own discriminator,
   * and the parent into the plain {@code oneOf} with a mapping. Names come from the runtime's
   * {@link JsonSubTypes}, so the document and the reader cannot disagree.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void closeFacetValueTypes(Map<String, Schema> schemas) {
    Schema parent = schemas.get(FACET_VALUE_SCHEMA);
    JsonSubTypes subTypes = RequirementFacetValue.class.getAnnotation(JsonSubTypes.class);
    if (parent == null || subTypes == null) {
      return;
    }
    Map<String, String> mapping = new LinkedHashMap<>();
    for (JsonSubTypes.Type type : subTypes.value()) {
      String name = type.value().getSimpleName();
      Schema subtype = schemas.get(name);
      if (subtype == null) {
        continue;
      }
      schemas.put(name, closed(subtype, type.name(), parent.getSpecVersion()));
      mapping.put(type.name(), REF + name);
    }
    // valueType is now each subtype's own; the parent only chooses among them.
    parent.setProperties(null);
    parent.setRequired(null);
    Discriminator discriminator =
        parent.getDiscriminator() == null ? new Discriminator() : parent.getDiscriminator();
    discriminator.setPropertyName(DISCRIMINATOR);
    discriminator.setMapping(mapping);
    parent.setDiscriminator(discriminator);
  }

  /** One closed object: valueType, then the subtype's own fields, nothing else. */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Schema<?> closed(Schema<?> subtype, String typeName, SpecVersion version) {
    Map<String, Schema> fields = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    String description = subtype.getDescription();
    if (subtype.getAllOf() != null) {
      for (Schema<?> part : subtype.getAllOf()) {
        if (part.get$ref() != null) {
          continue; // the parent: it contributed valueType only
        }
        collect(part, fields, required);
        description = description == null ? part.getDescription() : description;
      }
    } else {
      collect(subtype, fields, required);
    }
    fields.remove(DISCRIMINATOR);
    required.remove(DISCRIMINATOR);

    Schema discriminatorValue = schema(version);
    discriminatorValue.setTypes(new LinkedHashSet<>(List.of("string")));
    discriminatorValue.setType("string");
    discriminatorValue.setEnum(new ArrayList<>(List.of(typeName)));

    Map<String, Schema> ordered = new LinkedHashMap<>();
    ordered.put(DISCRIMINATOR, discriminatorValue);
    ordered.putAll(fields);
    List<String> requiredWithType = new ArrayList<>();
    requiredWithType.add(DISCRIMINATOR);
    requiredWithType.addAll(required);

    Schema result = schema(version);
    result.setTypes(new LinkedHashSet<>(List.of("object")));
    result.setType("object");
    result.setDescription(description);
    result.setProperties(ordered);
    result.setRequired(requiredWithType);
    result.setAdditionalProperties(Boolean.FALSE);
    return result;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void collect(Schema<?> part, Map<String, Schema> fields, List<String> required) {
    if (part.getProperties() != null) {
      fields.putAll((Map) part.getProperties());
    }
    if (part.getRequired() != null) {
      part.getRequired().stream().filter(name -> !required.contains(name)).forEach(required::add);
    }
  }

  private static Schema<Object> schema(SpecVersion version) {
    Schema<Object> schema = new Schema<>();
    schema.setSpecVersion(version == null ? SpecVersion.V31 : version);
    return schema;
  }
}
