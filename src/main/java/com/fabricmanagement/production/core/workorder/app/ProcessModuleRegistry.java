package com.fabricmanagement.production.core.workorder.app;

import com.fabricmanagement.production.core.workorder.app.validation.WorkOrderProductionValidator;
import com.fabricmanagement.production.core.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.core.workorder.domain.specs.GenericProductionSpecs;
import com.fabricmanagement.production.core.workorder.domain.specs.WorkOrderProductionSpecs;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Component;

/** Runtime registry for process-owned production specification plug-ins. */
@Component
public final class ProcessModuleRegistry
    implements Jackson2ObjectMapperBuilderCustomizer, OpenApiCustomizer {

  private final List<Registration> registrations;

  public ProcessModuleRegistry(
      List<ProcessSpecsContribution> contributions, List<WorkOrderProductionValidator> validators) {
    this.registrations = buildRegistrations(contributions, validators);
  }

  @Override
  public void customize(Jackson2ObjectMapperBuilder builder) {
    NamedType[] subtypes =
        registrations.stream()
            .map(
                registration ->
                    new NamedType(registration.specsClass(), registration.type().name()))
            .toArray(NamedType[]::new);
    builder.postConfigurer(objectMapper -> objectMapper.registerSubtypes(subtypes));
  }

  @Override
  public void customise(OpenAPI openApi) {
    if (openApi.getComponents() == null) {
      openApi.setComponents(new Components());
    }

    registrations.forEach(registration -> registerOpenApiSchema(openApi, registration));
  }

  public List<Registration> registrations() {
    return registrations;
  }

  private static List<Registration> buildRegistrations(
      List<ProcessSpecsContribution> contributions, List<WorkOrderProductionValidator> validators) {
    Map<WorkOrderModuleType, ProcessSpecsContribution> contributionsByType =
        uniqueByType(contributions, ProcessSpecsContribution::type, "contribution");
    Map<WorkOrderModuleType, WorkOrderProductionValidator> validatorsByType =
        uniqueByType(validators, WorkOrderProductionValidator::getSupportedType, "validator");

    if (contributionsByType.containsKey(WorkOrderModuleType.GENERIC)) {
      throw new IllegalStateException(
          "GENERIC specs are core-owned and must not have a contribution");
    }

    Set<WorkOrderModuleType> processTypes = EnumSet.allOf(WorkOrderModuleType.class);
    processTypes.remove(WorkOrderModuleType.GENERIC);
    assertExactTypes("contribution", contributionsByType.keySet(), processTypes);

    Set<WorkOrderModuleType> allTypes = EnumSet.allOf(WorkOrderModuleType.class);
    assertExactTypes("validator", validatorsByType.keySet(), allTypes);

    List<Registration> ordered = new ArrayList<>();
    Set<Class<? extends WorkOrderProductionSpecs>> registeredSpecsClasses = new HashSet<>();
    for (WorkOrderModuleType type : WorkOrderModuleType.values()) {
      Class<? extends WorkOrderProductionSpecs> specsClass;
      if (type == WorkOrderModuleType.GENERIC) {
        specsClass = GenericProductionSpecs.class;
      } else {
        specsClass = contributionsByType.get(type).specsClass();
      }

      if (specsClass == null || !WorkOrderProductionSpecs.class.isAssignableFrom(specsClass)) {
        throw new IllegalStateException(
            "Specs class for " + type + " must implement WorkOrderProductionSpecs");
      }
      if (!registeredSpecsClasses.add(specsClass)) {
        throw new IllegalStateException(
            "Duplicate specs class mapping for " + specsClass.getName());
      }
      ordered.add(new Registration(type, specsClass));
    }
    return List.copyOf(ordered);
  }

  private static <T> Map<WorkOrderModuleType, T> uniqueByType(
      List<T> values, Function<T, WorkOrderModuleType> typeExtractor, String label) {
    Map<WorkOrderModuleType, T> result = new EnumMap<>(WorkOrderModuleType.class);
    for (T value : values) {
      WorkOrderModuleType type = typeExtractor.apply(value);
      if (type == null) {
        throw new IllegalStateException("Process " + label + " type must not be null");
      }
      if (result.putIfAbsent(type, value) != null) {
        throw new IllegalStateException("Duplicate process " + label + " for " + type);
      }
    }
    return result;
  }

  private static void assertExactTypes(
      String label, Set<WorkOrderModuleType> actual, Set<WorkOrderModuleType> expected) {
    if (!actual.equals(expected)) {
      Set<WorkOrderModuleType> missing = EnumSet.noneOf(WorkOrderModuleType.class);
      missing.addAll(expected);
      missing.removeAll(actual);
      Set<WorkOrderModuleType> unexpected = EnumSet.noneOf(WorkOrderModuleType.class);
      unexpected.addAll(actual);
      unexpected.removeAll(expected);
      throw new IllegalStateException(
          "Invalid process "
              + label
              + " registry; missing="
              + missing
              + ", unexpected="
              + unexpected);
    }
  }

  private static void registerOpenApiSchema(OpenAPI openApi, Registration registration) {
    ResolvedSchema resolvedSchema =
        ModelConverters.getInstance().readAllAsResolvedSchema(registration.specsClass());
    if (resolvedSchema.referencedSchemas != null) {
      resolvedSchema.referencedSchemas.forEach(
          (name, schema) -> addSchemaIfAbsent(openApi.getComponents(), name, schema));
    }
    String schemaName = registration.specsClass().getSimpleName();
    if (resolvedSchema.schema != null) {
      addSchemaIfAbsent(openApi.getComponents(), schemaName, resolvedSchema.schema);
    }
  }

  private static void addSchemaIfAbsent(
      Components components, String schemaName, Schema<?> schema) {
    if (components.getSchemas() == null || !components.getSchemas().containsKey(schemaName)) {
      components.addSchemas(schemaName, schema);
    }
  }

  public record Registration(
      WorkOrderModuleType type, Class<? extends WorkOrderProductionSpecs> specsClass) {}
}
