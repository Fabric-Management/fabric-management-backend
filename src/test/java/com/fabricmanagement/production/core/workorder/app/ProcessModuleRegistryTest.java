package com.fabricmanagement.production.core.workorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.production.core.workorder.app.validation.WorkOrderProductionValidator;
import com.fabricmanagement.production.core.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.core.workorder.domain.specs.GenericProductionSpecs;
import com.fabricmanagement.production.core.workorder.domain.specs.WorkOrderProductionSpecs;
import com.fabricmanagement.production.dyeing.domain.specs.DyeingProductionSpecs;
import com.fabricmanagement.production.finishing.domain.specs.FinishingProductionSpecs;
import com.fabricmanagement.production.knitting.domain.specs.KnittingProductionSpecs;
import com.fabricmanagement.production.spinning.domain.specs.SpinningProductionSpecs;
import com.fabricmanagement.production.weaving.domain.specs.WeavingProductionSpecs;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class ProcessModuleRegistryTest {

  private static final String BASE_SCHEMA_NAME = WorkOrderProductionSpecs.class.getSimpleName();
  private static final String BASE_SCHEMA_REF = "#/components/schemas/" + BASE_SCHEMA_NAME;

  @Test
  void registersEverySubtypeInEnumOrderIncludingCoreOwnedGeneric() {
    ProcessModuleRegistry registry = new ProcessModuleRegistry(contributions(), validators());

    assertThat(registry.registrations())
        .extracting(ProcessModuleRegistry.Registration::type)
        .containsExactly(WorkOrderModuleType.values());
    assertThat(registry.registrations().get(WorkOrderModuleType.GENERIC.ordinal()).specsClass())
        .isEqualTo(GenericProductionSpecs.class);
  }

  @Test
  void registersEverySubtypeWithTheSpringJacksonBuilder() throws Exception {
    ProcessModuleRegistry registry = new ProcessModuleRegistry(contributions(), validators());
    Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
    registry.customize(builder);
    ObjectMapper objectMapper = builder.build();

    for (ProcessModuleRegistry.Registration registration : registry.registrations()) {
      WorkOrderProductionSpecs specs =
          objectMapper.readValue(
              "{\"specType\":\"" + registration.type().name() + "\"}",
              WorkOrderProductionSpecs.class);

      assertThat(specs).isInstanceOf(registration.specsClass());
    }
  }

  @Test
  void restoresConcreteOpenApiSchemasWithoutAddingBaseOneOfOrMapping() {
    ProcessModuleRegistry registry = new ProcessModuleRegistry(contributions(), validators());
    Schema<?> baseSchema =
        new Schema<>()
            .discriminator(new Discriminator().propertyName("specType"))
            .addProperty("specType", new StringSchema())
            .addRequiredItem("specType");
    Schema<?> productionSpecsProperty =
        new Schema<>()
            .$ref(BASE_SCHEMA_REF)
            .description("Module-specific production specifications");
    Schema<?> consumerSchema =
        new Schema<>().addProperty("productionSpecs", productionSpecsProperty);
    OpenAPI openApi =
        new OpenAPI()
            .components(
                new Components()
                    .addSchemas(BASE_SCHEMA_NAME, baseSchema)
                    .addSchemas("WorkOrderRequest", consumerSchema));

    registry.customise(openApi);

    Schema<?> actualBaseSchema = openApi.getComponents().getSchemas().get(BASE_SCHEMA_NAME);
    assertThat(actualBaseSchema).isSameAs(baseSchema);
    assertThat(actualBaseSchema.getOneOf()).isNullOrEmpty();
    assertThat(actualBaseSchema.getDiscriminator()).isNotNull();
    assertThat(actualBaseSchema.getDiscriminator().getPropertyName()).isEqualTo("specType");
    assertThat(actualBaseSchema.getDiscriminator().getMapping()).isNullOrEmpty();

    for (ProcessModuleRegistry.Registration registration : registry.registrations()) {
      Schema<?> concreteSchema =
          openApi.getComponents().getSchemas().get(registration.specsClass().getSimpleName());
      assertThat(concreteSchema).isNotNull();
      assertThat(concreteSchema.getAllOf()).hasSize(2);
      assertThat(concreteSchema.getAllOf().getFirst().get$ref()).isEqualTo(BASE_SCHEMA_REF);
      assertThat(concreteSchema.getAllOf().getLast().getType()).isEqualTo("object");
      assertThat(concreteSchema.getAllOf().getLast().getTypes()).containsExactly("object");
      assertThat(concreteSchema.getAllOf().getLast().getProperties()).isNotEmpty();
    }

    assertThat(productionSpecsProperty.get$ref()).isNull();
    assertThat(productionSpecsProperty.getDescription())
        .isEqualTo("Module-specific production specifications");
    assertThat(productionSpecsProperty.getOneOf())
        .extracting(Schema::get$ref)
        .containsExactly(
            "#/components/schemas/DyeingProductionSpecs",
            "#/components/schemas/FinishingProductionSpecs",
            "#/components/schemas/GenericProductionSpecs",
            "#/components/schemas/KnittingProductionSpecs",
            "#/components/schemas/SpinningProductionSpecs",
            "#/components/schemas/WeavingProductionSpecs");

    Schema<?> dyeingSchema = openApi.getComponents().getSchemas().get("DyeingProductionSpecs");
    Schema<?> dyeingBody = dyeingSchema.getAllOf().getLast();
    Schema<?> fastnessTargets = dyeingBody.getProperties().get("fastnessTargets");
    assertThat(fastnessTargets.getItems().getDescription()).isNull();
    assertThat(fastnessTargets.getItems().getExample()).isNull();
    assertThat(fastnessTargets.getItems().getExampleSetFlag()).isFalse();
  }

  @Test
  void rejectsMissingContribution() {
    List<ProcessSpecsContribution> contributions = new ArrayList<>(contributions());
    contributions.removeFirst();

    assertThatThrownBy(() -> new ProcessModuleRegistry(contributions, validators()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid process contribution registry")
        .hasMessageContaining("SPINNING");
  }

  @Test
  void rejectsDuplicateContribution() {
    List<ProcessSpecsContribution> contributions = new ArrayList<>(contributions());
    contributions.add(contributions.getFirst());

    assertThatThrownBy(() -> new ProcessModuleRegistry(contributions, validators()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Duplicate process contribution for SPINNING");
  }

  @Test
  void rejectsGenericContribution() {
    List<ProcessSpecsContribution> contributions = new ArrayList<>(contributions());
    contributions.add(
        new TestContribution(WorkOrderModuleType.GENERIC, GenericProductionSpecs.class));

    assertThatThrownBy(() -> new ProcessModuleRegistry(contributions, validators()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("GENERIC specs are core-owned and must not have a contribution");
  }

  @Test
  void rejectsMissingValidator() {
    List<WorkOrderProductionValidator> validators = new ArrayList<>(validators());
    validators.removeFirst();

    assertThatThrownBy(() -> new ProcessModuleRegistry(contributions(), validators))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid process validator registry")
        .hasMessageContaining("SPINNING");
  }

  @Test
  void rejectsDuplicateValidator() {
    List<WorkOrderProductionValidator> validators = new ArrayList<>(validators());
    validators.add(validators.getFirst());

    assertThatThrownBy(() -> new ProcessModuleRegistry(contributions(), validators))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Duplicate process validator for SPINNING");
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void rejectsContributionWhoseClassDoesNotImplementSpecsContract() {
    List<ProcessSpecsContribution> contributions = new ArrayList<>(contributions());
    contributions.set(
        0,
        new TestContribution(
            WorkOrderModuleType.SPINNING,
            (Class<? extends WorkOrderProductionSpecs>) (Class) String.class));

    assertThatThrownBy(() -> new ProcessModuleRegistry(contributions, validators()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Specs class for SPINNING must implement WorkOrderProductionSpecs");
  }

  @Test
  void rejectsTheSameSpecsClassMappedToMultipleModuleTypes() {
    List<ProcessSpecsContribution> contributions = new ArrayList<>(contributions());
    contributions.set(
        1, new TestContribution(WorkOrderModuleType.WEAVING, SpinningProductionSpecs.class));

    assertThatThrownBy(() -> new ProcessModuleRegistry(contributions, validators()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Duplicate specs class mapping for " + SpinningProductionSpecs.class.getName());
  }

  private static List<ProcessSpecsContribution> contributions() {
    return List.of(
        new TestContribution(WorkOrderModuleType.SPINNING, SpinningProductionSpecs.class),
        new TestContribution(WorkOrderModuleType.WEAVING, WeavingProductionSpecs.class),
        new TestContribution(WorkOrderModuleType.KNITTING, KnittingProductionSpecs.class),
        new TestContribution(WorkOrderModuleType.DYEING, DyeingProductionSpecs.class),
        new TestContribution(WorkOrderModuleType.FINISHING, FinishingProductionSpecs.class));
  }

  private static List<WorkOrderProductionValidator> validators() {
    return Arrays.stream(WorkOrderModuleType.values())
        .<WorkOrderProductionValidator>map(TestValidator::new)
        .toList();
  }

  private record TestContribution(
      WorkOrderModuleType type, Class<? extends WorkOrderProductionSpecs> specsClass)
      implements ProcessSpecsContribution {}

  private record TestValidator(WorkOrderModuleType type) implements WorkOrderProductionValidator {

    @Override
    public WorkOrderModuleType getSupportedType() {
      return type;
    }

    @Override
    public List<String> validateOnCreate(WorkOrderProductionSpecs specs) {
      return List.of();
    }

    @Override
    public List<String> validateOnStart(WorkOrderProductionSpecs specs) {
      return List.of();
    }
  }
}
