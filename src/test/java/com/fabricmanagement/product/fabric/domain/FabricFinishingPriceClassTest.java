package com.fabricmanagement.product.fabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.pricing.FabricFinishingPriceClass;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** FABRIC-3c: a price class is a tier a dyehouse prices by, named by the tenant. */
class FabricFinishingPriceClassTest {

  private static final UUID TENANT = UUID.randomUUID();

  @Test
  void aTenantDefinedClassIsNotSystemDefinedAndStartsUsable() {
    FabricFinishingPriceClass tier =
        FabricFinishingPriceClass.defineTenant(
            TENANT, "NAVY_DEEP", "Deep navy", " Priced alone ", 8);

    assertThat(tier.getCode()).isEqualTo("NAVY_DEEP");
    assertThat(tier.getName()).isEqualTo("Deep navy");
    assertThat(tier.getDescription()).isEqualTo("Priced alone");
    assertThat(tier.getDisplayOrder()).isEqualTo(8);
    assertThat(tier.isSystemDefined()).isFalse();
    assertThat(tier.isUsable()).isTrue();
  }

  @Test
  void aSystemClassIsMarkedAsSuch() {
    assertThat(
            FabricFinishingPriceClass.defineSystem(TENANT, "LIGHT", "Light", null, 1)
                .isSystemDefined())
        .isTrue();
  }

  @Test
  void theCodeIsUpperSnakeCase() {
    assertThatThrownBy(
            () -> FabricFinishingPriceClass.defineTenant(TENANT, "navy", "Navy", null, null))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(
            () -> FabricFinishingPriceClass.defineTenant(TENANT, "NAVY DEEP", "Navy", null, null))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(
            () -> FabricFinishingPriceClass.defineTenant(TENANT, null, "Navy", null, null))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(
            () -> FabricFinishingPriceClass.defineTenant(TENANT, " NAVY", "Navy", null, null))
        .isInstanceOf(FabricDomainException.class);
  }

  @Test
  void aClassNeedsATenantAndAName() {
    assertThatThrownBy(
            () -> FabricFinishingPriceClass.defineTenant(null, "NAVY", "Navy", null, null))
        .isInstanceOf(FabricDomainException.class);
    assertThatThrownBy(
            () -> FabricFinishingPriceClass.defineTenant(TENANT, "NAVY", " ", null, null))
        .isInstanceOf(FabricDomainException.class);
  }

  @Test
  void updatingRenamesReordersAndRetiresButKeepsTheCode() {
    FabricFinishingPriceClass tier =
        FabricFinishingPriceClass.defineSystem(TENANT, "DARK", "Dark", null, 3);

    tier.update("Deep shades", "A high dye percentage", 9, false);

    assertThat(tier.getCode()).isEqualTo("DARK");
    assertThat(tier.getName()).isEqualTo("Deep shades");
    assertThat(tier.getDescription()).isEqualTo("A high dye percentage");
    assertThat(tier.getDisplayOrder()).isEqualTo(9);
    assertThat(tier.isUsable()).isFalse();
    assertThat(tier.isSystemDefined()).isTrue();
    assertThatThrownBy(() -> tier.update(" ", null, null, true))
        .isInstanceOf(FabricDomainException.class);
  }
}
