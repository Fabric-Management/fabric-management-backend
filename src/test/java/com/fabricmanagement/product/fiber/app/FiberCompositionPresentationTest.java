package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.product.fiber.dto.FiberCompositionComponentDto;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Composition labels and ordering (FIBER-CATALOG-1 §6, A09/A10 unit level). */
class FiberCompositionPresentationTest {

  private final FiberCategory natural = category("NATURAL_PLANT");
  private final FiberCategory synthetic = category("SYNTHETIC_POLYMER");
  private final FiberCategory mixed = category(FiberCatalog.MIXED_BLEND_CATEGORY_CODE);
  private final FiberIsoCode co = iso("CO", "NATURAL_PLANT");
  private final FiberIsoCode pes = iso("PES", "SYNTHETIC_POLYMER");

  private static FiberCategory category(String code) {
    FiberCategory category = FiberCategory.builder().categoryCode(code).categoryName(code).build();
    category.setId(UUID.randomUUID());
    return category;
  }

  private static FiberIsoCode iso(String code, String type) {
    FiberIsoCode iso =
        FiberIsoCode.builder()
            .isoCode(code)
            .fiberName(code)
            .fiberType(type)
            .isOfficialIso(true)
            .build();
    iso.setId(UUID.randomUUID());
    return iso;
  }

  private static Product product() {
    Product product = Product.create(ProductType.FIBER, "KG");
    product.setId(UUID.randomUUID());
    return product;
  }

  private Fiber canonical(FiberIsoCode iso, FiberCategory category) {
    Fiber fiber = Fiber.createCanonicalPure(product(), category, iso, iso.getIsoCode() + " (100%)");
    fiber.setId(UUID.randomUUID());
    return fiber;
  }

  private Fiber variant(FiberIsoCode iso, FiberCategory category, MaterialSource source) {
    Fiber fiber =
        Fiber.createSourceVariant(
            product(), category, iso, iso.getIsoCode() + " " + source, source);
    fiber.setId(UUID.randomUUID());
    return fiber;
  }

  private Fiber blend(Map<UUID, BigDecimal> composition) {
    Fiber fiber = Fiber.createBlend(product(), mixed, "Blend", composition);
    fiber.setId(UUID.randomUUID());
    return fiber;
  }

  private static Map<UUID, Fiber> byId(Fiber... fibers) {
    Map<UUID, Fiber> map = new LinkedHashMap<>();
    for (Fiber fiber : fibers) {
      map.put(fiber.getId(), fiber);
    }
    return map;
  }

  @Test
  void blendLabelUsesSharedIsoCodesExactDecimalsAndPercentageDescendingOrder() {
    Fiber cotton = canonical(co, natural);
    Fiber polyester = canonical(pes, synthetic);
    Map<UUID, BigDecimal> composition = new LinkedHashMap<>();
    composition.put(polyester.getId(), new BigDecimal("37.50"));
    composition.put(cotton.getId(), new BigDecimal("62.5"));

    List<FiberCompositionComponentDto> components =
        FiberCompositionPresenter.components(blend(composition), byId(cotton, polyester));

    assertThat(components)
        .extracting(FiberCompositionComponentDto::isoCode)
        .containsExactly("CO", "PES");
    assertThat(FiberCompositionPresenter.label(components)).isEqualTo("62.5% CO / 37.5% PES");
  }

  @Test
  void equalSharesAreOrderedByIsoCodeThenSourceAndSourcesAreNeverCollapsed() {
    Fiber recycled = variant(pes, synthetic, MaterialSource.RECYCLED);
    Fiber virgin = variant(pes, synthetic, MaterialSource.VIRGIN);
    Map<UUID, BigDecimal> composition = new LinkedHashMap<>();
    composition.put(virgin.getId(), new BigDecimal("50"));
    composition.put(recycled.getId(), new BigDecimal("50.00"));

    String label =
        FiberCompositionPresenter.label(
            FiberCompositionPresenter.components(blend(composition), byId(recycled, virgin)));

    assertThat(label).isEqualTo("50% PES (recycled) / 50% PES (virgin)");
  }

  @Test
  void undeclaredSourcesAreNamedWhenAnyComponentDeclaresOne() {
    Fiber cotton = canonical(co, natural);
    Fiber recycledPes = variant(pes, synthetic, MaterialSource.RECYCLED);
    Map<UUID, BigDecimal> composition = new LinkedHashMap<>();
    composition.put(cotton.getId(), new BigDecimal("50"));
    composition.put(recycledPes.getId(), new BigDecimal("50"));

    String label =
        FiberCompositionPresenter.label(
            FiberCompositionPresenter.components(blend(composition), byId(cotton, recycledPes)));

    assertThat(label).isEqualTo("50% CO (undeclared) / 50% PES (recycled)");
  }

  @Test
  void aPureFibreIsItselfAtOneHundredPercent() {
    Fiber cotton = canonical(co, natural);

    String label =
        FiberCompositionPresenter.label(FiberCompositionPresenter.components(cotton, Map.of()));

    assertThat(label).isEqualTo("100% CO");
  }

  @Test
  void anUnresolvedComponentFailsInsteadOfPrintingAPartialLabel() {
    Fiber cotton = canonical(co, natural);
    Map<UUID, BigDecimal> composition = new LinkedHashMap<>();
    composition.put(cotton.getId(), new BigDecimal("60"));
    composition.put(UUID.randomUUID(), new BigDecimal("40"));

    assertThatThrownBy(() -> FiberCompositionPresenter.components(blend(composition), byId(cotton)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void percentStripsOnlyInsignificantZeroes() {
    assertThat(FiberCompositionPresenter.percent(new BigDecimal("60.00"))).isEqualTo("60");
    assertThat(FiberCompositionPresenter.percent(new BigDecimal("62.50"))).isEqualTo("62.5");
    assertThat(FiberCompositionPresenter.percent(new BigDecimal("33.333"))).isEqualTo("33.333");
    assertThat(FiberCompositionPresenter.percent(new BigDecimal("1E+2"))).isEqualTo("100");
  }
}
