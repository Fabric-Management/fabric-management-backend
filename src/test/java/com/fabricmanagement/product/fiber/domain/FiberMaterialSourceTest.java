package com.fabricmanagement.product.fiber.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Material source and kind invariants of the FIBER-CATALOG-1 fibre factories. */
class FiberMaterialSourceTest {

  private final Product product = Product.create(ProductType.FIBER, "KG");
  private final FiberCategory category =
      FiberCategory.builder().categoryCode("SYNTHETIC_POLYMER").categoryName("Synthetic").build();
  private final FiberCategory mixedBlend =
      FiberCategory.builder()
          .categoryCode(FiberCatalog.MIXED_BLEND_CATEGORY_CODE)
          .categoryName("Mixed blend")
          .build();
  private final FiberIsoCode isoCode =
      FiberIsoCode.builder()
          .isoCode("PES")
          .fiberName("Polyester")
          .fiberType("SYNTHETIC_POLYMER")
          .isOfficialIso(true)
          .build();

  @Test
  void canonicalPureIsUndeclaredAndPrivateVariantRequiresASource() {
    Fiber canonical = Fiber.createCanonicalPure(product, category, isoCode, "Polyester (100%)");
    assertThat(canonical.getMaterialSource()).isNull();
    assertThat(canonical.getKind()).isEqualTo(FiberKind.PURE);
    assertThat(canonical.getFiberIsoCode()).isSameAs(isoCode);

    assertThat(variant(MaterialSource.VIRGIN).getMaterialSource()).isEqualTo(MaterialSource.VIRGIN);
    assertThat(variant(MaterialSource.RECYCLED).getMaterialSource())
        .isEqualTo(MaterialSource.RECYCLED);
    assertThatThrownBy(() -> variant(null))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_MATERIAL_SOURCE_REQUIRED");
  }

  @Test
  void pureFactoriesRequireASharedIsoCode() {
    assertThatThrownBy(() -> Fiber.createCanonicalPure(product, category, null, "Nameless"))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_ISO_REQUIRED");
  }

  @Test
  void declarationAllowsOnlyNullToValue() {
    Fiber legacy = Fiber.createCanonicalPure(product, category, isoCode, "Polyester (100%)");

    legacy.declareMaterialSource(MaterialSource.RECYCLED);

    assertThat(legacy.getMaterialSource()).isEqualTo(MaterialSource.RECYCLED);
    assertThatThrownBy(() -> legacy.declareMaterialSource(MaterialSource.VIRGIN))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_MATERIAL_SOURCE_IMMUTABLE");
    assertThatThrownBy(
            () ->
                Fiber.createCanonicalPure(product, category, isoCode, "Polyester (100%)")
                    .declareMaterialSource(null))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_MATERIAL_SOURCE_REQUIRED");
  }

  @Test
  void blendHasNoIsoNoSourceAndANormalisedComposition() {
    UUID cotton = UUID.randomUUID();
    UUID polyester = UUID.randomUUID();
    Map<UUID, BigDecimal> composition = new LinkedHashMap<>();
    composition.put(polyester, new BigDecimal("40.00"));
    composition.put(cotton, new BigDecimal("60.0"));

    Fiber blend = Fiber.createBlend(product, mixedBlend, "CO 60% / PES 40%", composition);

    assertThat(blend.getKind()).isEqualTo(FiberKind.BLEND);
    assertThat(blend.getFiberIsoCode()).isNull();
    assertThat(blend.getFiberIsoCodeId()).isNull();
    assertThat(blend.getMaterialSource()).isNull();
    assertThat(blend.getComposition())
        .containsEntry(cotton, new BigDecimal("60"))
        .containsEntry(polyester, new BigDecimal("40"));
    assertThatThrownBy(() -> blend.declareMaterialSource(MaterialSource.RECYCLED))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_BLEND_MATERIAL_SOURCE_FORBIDDEN");
  }

  @Test
  void blendNeedsTwoComponentsAndAPureFibreNeverBecomesABlend() {
    assertThatThrownBy(
            () ->
                Fiber.createBlend(
                    product, mixedBlend, "One", Map.of(UUID.randomUUID(), new BigDecimal("100"))))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_BLEND_MIN_COMPONENTS");

    Fiber pure = Fiber.createCanonicalPure(product, category, isoCode, "Polyester (100%)");
    Map<UUID, BigDecimal> blend =
        Map.of(UUID.randomUUID(), new BigDecimal("50"), UUID.randomUUID(), new BigDecimal("50"));
    assertThatThrownBy(() -> pure.changeBlendComposition(blend))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_KIND_CHANGE_FORBIDDEN");
  }

  @Test
  void settersAndFiberBuilderAreNotPublicWritePaths() throws Exception {
    assertThat(Fiber.class.getMethods())
        .noneMatch(method -> method.getName().equals("setMaterialSource"));
    assertThat(FiberRequest.class.getMethods())
        .noneMatch(method -> method.getName().equals("setMaterialSource"));
    assertThat(Modifier.isPrivate(Fiber.class.getDeclaredMethod("builder").getModifiers()))
        .isTrue();
  }

  @Test
  void blendFactorySignatureCannotAcceptAnIsoCodeOrMaterialSource() {
    assertThat(Fiber.class.getDeclaredMethods())
        .filteredOn(method -> method.getName().equals("createBlend"))
        .isNotEmpty()
        .allSatisfy(
            method ->
                assertThat(method.getParameterTypes())
                    .doesNotContain(MaterialSource.class, FiberIsoCode.class));
    assertThat(Fiber.class.getDeclaredMethods())
        .noneMatch(
            method ->
                method.getName().equals("createPureFiber")
                    || method.getName().equals("createBlendedFiber"));
  }

  private Fiber variant(MaterialSource source) {
    return Fiber.createSourceVariant(product, category, isoCode, "Polyester (100%)", source);
  }
}
