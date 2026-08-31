package com.fabricmanagement.product.fiber.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FiberMaterialSourceTest {

  private final Product product =
      Product.create(com.fabricmanagement.product.core.domain.ProductType.FIBER, "KG");
  private final FiberCategory category =
      FiberCategory.builder().categoryCode("SYNTHETIC_POLYMER").categoryName("Synthetic").build();
  private final FiberIsoCode isoCode =
      FiberIsoCode.builder()
          .isoCode("PES")
          .fiberName("Polyester")
          .fiberType("SYNTHETIC_POLYMER")
          .isOfficialIso(true)
          .build();

  @Test
  void pureFactoryAcceptsBothValuesAndAnUndeclaredLegacyState() {
    assertThat(pure(MaterialSource.VIRGIN).getMaterialSource()).isEqualTo(MaterialSource.VIRGIN);
    assertThat(pure(MaterialSource.RECYCLED).getMaterialSource())
        .isEqualTo(MaterialSource.RECYCLED);
    assertThat(pure(null).getMaterialSource()).isNull();
  }

  @Test
  void declarationAllowsOnlyNullToValue() {
    Fiber legacy = pure(null);

    legacy.declareMaterialSource(MaterialSource.RECYCLED);

    assertThat(legacy.getMaterialSource()).isEqualTo(MaterialSource.RECYCLED);
    assertThatThrownBy(() -> legacy.declareMaterialSource(MaterialSource.VIRGIN))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_MATERIAL_SOURCE_IMMUTABLE");
    assertThatThrownBy(() -> pure(null).declareMaterialSource(null))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_MATERIAL_SOURCE_REQUIRED");
  }

  @Test
  void blendFactoryHasNoSourceAndDeclarationRejectsBlend() {
    Fiber blend =
        Fiber.createBlendedFiber(
            product,
            category,
            isoCode,
            "Polyester Cotton",
            Map.of(UUID.randomUUID(), new BigDecimal("60.00")));

    assertThat(blend.getMaterialSource()).isNull();
    assertThatThrownBy(() -> blend.declareMaterialSource(MaterialSource.RECYCLED))
        .isInstanceOf(FiberDomainException.class)
        .extracting("errorCode")
        .isEqualTo("FIBER_BLEND_MATERIAL_SOURCE_FORBIDDEN");
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
  void blendedFactorySignatureCannotAcceptMaterialSource() {
    assertThat(Fiber.class.getDeclaredMethods())
        .filteredOn(method -> method.getName().equals("createBlendedFiber"))
        .allSatisfy(
            method -> assertThat(method.getParameterTypes()).doesNotContain(MaterialSource.class));
  }

  private Fiber pure(MaterialSource source) {
    return Fiber.createPureFiber(product, category, isoCode, "Polyester", source);
  }
}
