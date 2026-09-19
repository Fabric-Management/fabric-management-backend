package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacet;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementFacetValue;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileBasis;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileInput;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest;
import com.fabricmanagement.sales.salesorder.dto.UpdateSalesOrderLineRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModuleSpecsValidatorTest {

  private final ModuleSpecsValidator validator = new ModuleSpecsValidator();

  @Test
  void typedProfileIsAcceptedWithoutLegacyModuleSpecs() {
    SalesOrderLineRequest line =
        SalesOrderLineRequest.builder()
            .moduleType(ModuleType.FABRIC)
            .requirementProfile(profile())
            .build();

    assertThatCode(() -> validator.validate(line)).doesNotThrowAnyException();
  }

  @Test
  void conflictingLegacyRequirementKeyIsRejected() {
    SalesOrderLineRequest line =
        SalesOrderLineRequest.builder()
            .moduleType(ModuleType.FABRIC)
            .moduleSpecs(Map.of("width", "150 cm"))
            .requirementProfile(profile())
            .build();

    assertThatThrownBy(() -> validator.validate(line))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("conflicts with typed requirement facet WIDTH");
  }

  @Test
  void omittedProfileKeepsCurrentTypedOwnershipAndRejectsALegacyRequirementKey() {
    RequirementProfileInput currentInput = profile();
    RequirementProfileSnapshot current =
        RequirementProfileSnapshot.resolve(UUID.randomUUID(), 1, currentInput, null, false);
    UpdateSalesOrderLineRequest update =
        UpdateSalesOrderLineRequest.builder()
            .moduleType(ModuleType.FABRIC)
            .moduleSpecs(Map.of("width", "151 cm"))
            .build();

    assertThatThrownBy(() -> validator.validate(update, current))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("conflicts with typed requirement facet WIDTH");
  }

  @Test
  void omittedProfileRejectsEveryLegacyRequirementKeyEvenWhenTheCurrentFacetKindDiffers() {
    RequirementProfileSnapshot current =
        RequirementProfileSnapshot.resolve(UUID.randomUUID(), 1, profile(), null, false);
    UpdateSalesOrderLineRequest update =
        UpdateSalesOrderLineRequest.builder()
            .moduleType(ModuleType.FABRIC)
            .moduleSpecs(Map.of("originReq", "TR"))
            .build();

    assertThatThrownBy(() -> validator.validate(update, current))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining(
            "cannot be written while an existing typed requirement profile is retained");
  }

  private RequirementProfileInput profile() {
    Instant instant = Instant.parse("2026-09-18T10:00:00Z");
    UUID actor = UUID.randomUUID();
    RequirementFacet width =
        new RequirementFacet(
            RequirementFacet.Kind.WIDTH,
            "finished",
            RequirementFacet.State.BOUNDED,
            RequirementFacet.Comparison.MINIMUM,
            new RequirementFacetValue.Width(
                new RequirementFacetValue.NumericBounds(
                    RequirementFacetValue.BoundType.MIN,
                    null,
                    new BigDecimal("150"),
                    null,
                    true,
                    false,
                    "cm"),
                RequirementFacetValue.WidthForm.OPEN,
                RequirementFacetValue.MaterialState.FINISHED),
            new RequirementFacet.DecisionBasis(
                RequirementFacet.DecisionBasis.Source.CUSTOMER_INSTRUCTION,
                "contract",
                actor,
                instant));
    return new RequirementProfileInput(
        new RequirementProfileBasis(
            RequirementProfileBasis.Kind.LINE_EXPLICIT,
            null,
            null,
            null,
            null,
            actor,
            instant,
            "contract"),
        "fabric-v1",
        "sales-req-v1",
        Set.of("WIDTH:FINISHED"),
        List.of(width),
        List.of(),
        List.of());
  }
}
