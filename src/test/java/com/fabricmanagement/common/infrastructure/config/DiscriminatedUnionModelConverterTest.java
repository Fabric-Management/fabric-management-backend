package com.fabricmanagement.common.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.platform.user.dto.SaveOnboardingStepRequest;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.DyePurchaseSpecs;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.PurchaseOrderSpecs;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** OPENAPI-UNION-1: every Jackson-discriminated union is published in one closed shape. */
class DiscriminatedUnionModelConverterTest {

  private static final String REF = "#/components/schemas/";

  @Test
  void rootIsOneOfItsMembersWithACompleteMapping() {
    Map<String, Schema> schemas = resolve(SaveOnboardingStepRequest.class);

    Schema<?> request = schemas.get("SaveOnboardingStepRequest");
    assertThat(request.getProperties().get("payload").get$ref())
        .isEqualTo(REF + "OnboardingStepPayload");
    Schema<?> root = schemas.get("OnboardingStepPayload");
    assertThat(root.getProperties()).isNull();
    assertThat(root.getAllOf()).isNull();
    assertThat(root.getDescription()).startsWith("Exactly one typed step.");
    assertThat(root.getOneOf()).extracting(Schema::get$ref).hasSize(6);
    assertThat(root.getDiscriminator().getPropertyName()).isEqualTo("stepId");
    assertThat(root.getDiscriminator().getMapping())
        .hasSize(6)
        .containsEntry("REVIEW", REF + "OnboardingReviewStep")
        .containsEntry("ORGANIZATION_DETAILS", REF + "OnboardingOrganizationStep");
    assertThat(root.getDiscriminator().getMapping().values())
        .containsExactlyElementsOf(root.getOneOf().stream().map(Schema::get$ref).toList());
  }

  @Test
  void closedMemberOwnsItsPropertiesBesideItsClosure() {
    Schema<?> review = resolve(SaveOnboardingStepRequest.class).get("OnboardingReviewStep");

    assertThat(review.getAllOf()).as("no allOf back to the root").isNull();
    assertThat(review.getAdditionalProperties()).isEqualTo(Boolean.FALSE);
    assertThat(review.getProperties()).containsKeys("stepId", "confirmed", "reviewedVersion");
    assertThat(review.getProperties().get("stepId").getEnum()).containsExactly("REVIEW");
    assertThat(review.getRequired()).contains("stepId", "confirmed", "reviewedVersion");
  }

  @Test
  void typePropertyIsAddedToMembersThatDoNotDeclareIt() {
    Map<String, Schema> schemas = resolve(PurchaseOrderSpecs.class);

    Schema<?> root = schemas.get("PurchaseOrderSpecs");
    assertThat(root.getDiscriminator().getMapping())
        .hasSize(5)
        .containsEntry("DYE_FINISHING", REF + "DyePurchaseSpecs");
    Schema<?> dye = schemas.get("DyePurchaseSpecs");
    assertThat(dye.getAllOf()).isNull();
    assertThat(dye.getProperties().get("specType").getEnum()).containsExactly("DYE_FINISHING");
    assertThat(dye.getRequired()).contains("specType");
    assertThat(dye.getProperties()).containsKey("colorCode");
  }

  @Test
  void memberReachedWithoutItsRootIsPinnedToo() {
    Schema<?> dye = resolve(DyePurchaseSpecs.class).get("DyePurchaseSpecs");

    assertThat(dye.getProperties().get("specType").getEnum()).containsExactly("DYE_FINISHING");
    assertThat(dye.getRequired()).contains("specType");
  }

  @Test
  void recursiveUnionResolvesToReferences() {
    Map<String, Schema> schemas = resolve(Tree.class);

    Schema<?> root = schemas.get("Tree");
    assertThat(root.getDiscriminator().getMapping()).containsOnlyKeys("LEAF", "NODE");
    Schema<?> node = schemas.get("Node");
    assertThat(node.getProperties().get("first").get$ref()).isEqualTo(REF + "Tree");
    assertThat(node.getProperties().get("children").getItems().get$ref()).isEqualTo(REF + "Tree");
    assertThat(node.getProperties().get("kind").getEnum()).containsExactly("NODE");
  }

  @Test
  void refusesAMemberCopyOnTheRootSchema() {
    assertThatThrownBy(() -> resolve(Duplicated.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Remove oneOf");
  }

  @Test
  void refusesASealedRootThatPermitsAnUnlistedMember() {
    assertThatThrownBy(() -> resolve(Drifted.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("permits");
  }

  @Test
  void refusesAnExistingPropertyMemberWithoutTheProperty() {
    assertThatThrownBy(() -> resolve(MissingProperty.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must declare the discriminator property 'kind'");
  }

  @Test
  void refusesAMemberWhoseDeclaredValuesContradictItsTypeName() {
    assertThatThrownBy(() -> resolve(Contradicting.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("allows [OTHER]");
  }

  private static Map<String, Schema> resolve(Class<?> type) {
    ModelConverters converters = new ModelConverters(true);
    converters.addConverter(new DiscriminatedUnionModelConverter());
    return converters.readAll(new AnnotatedType(type).resolveAsRef(true));
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Tree.Leaf.class, name = "LEAF"),
    @JsonSubTypes.Type(value = Tree.Node.class, name = "NODE")
  })
  sealed interface Tree {
    record Leaf(String value) implements Tree {}

    record Node(List<Tree> children, Tree first) implements Tree {}
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
  @JsonSubTypes(@JsonSubTypes.Type(value = Duplicated.Only.class, name = "ONLY"))
  @io.swagger.v3.oas.annotations.media.Schema(oneOf = Duplicated.Only.class)
  sealed interface Duplicated {
    record Only(String value) implements Duplicated {}
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
  @JsonSubTypes(@JsonSubTypes.Type(value = Drifted.Listed.class, name = "LISTED"))
  sealed interface Drifted permits Drifted.Listed, Drifted.Unlisted {
    record Listed(String value) implements Drifted {}

    record Unlisted(String value) implements Drifted {}
  }

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXISTING_PROPERTY,
      property = "kind")
  @JsonSubTypes(@JsonSubTypes.Type(value = MissingProperty.Only.class, name = "ONLY"))
  sealed interface MissingProperty {
    record Only(String other) implements MissingProperty {}
  }

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXISTING_PROPERTY,
      property = "kind")
  @JsonSubTypes(@JsonSubTypes.Type(value = Contradicting.Only.class, name = "ONLY"))
  sealed interface Contradicting {
    record Only(@io.swagger.v3.oas.annotations.media.Schema(allowableValues = "OTHER") String kind)
        implements Contradicting {}
  }
}
