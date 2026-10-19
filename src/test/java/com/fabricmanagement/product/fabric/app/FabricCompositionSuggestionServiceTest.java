package com.fabricmanagement.product.fabric.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricYarnRole;
import com.fabricmanagement.product.fabric.dto.FabricCompositionDto;
import com.fabricmanagement.product.fabric.dto.FabricCompositionSuggestionDto;
import com.fabricmanagement.product.fabric.dto.FabricYarnRequest;
import com.fabricmanagement.product.fiber.api.facade.FiberFacade;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.yarn.api.facade.YarnFacade;
import com.fabricmanagement.product.yarn.dto.YarnCompositionLineDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** FABRIC-2: a fabric's composition worked out from its yarns, adding up to exactly 100. */
@ExtendWith(MockitoExtension.class)
class FabricCompositionSuggestionServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID CO = UUID.randomUUID();
  private static final UUID LI = UUID.randomUUID();
  private static final UUID PES = UUID.randomUUID();

  @Mock private ProductRepository products;
  @Mock private YarnFacade yarns;
  @Mock private FiberFacade fibers;

  private FabricCompositionSuggestionService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    service =
        new FabricCompositionSuggestionService(
            new FabricMaterialResolver(products, yarns, fibers), yarns);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void aCottonWarpAndALinenWeftGiveTheFabricTheirSharesOfEachFibre() {
    Product warp = yarn();
    Product weft = yarn();
    known(warp, weft);
    when(yarns.findDeclaredCompositions(any()))
        .thenReturn(
            Map.of(
                warp.getId(), List.of(fibre(CO, "CO", "100")),
                weft.getId(), List.of(fibre(LI, "LI", "50"), fibre(CO, "CO", "50"))));

    FabricCompositionSuggestionDto suggestion =
        service.suggest(
            List.of(line(FabricYarnRole.WARP, warp, "60"), line(FabricYarnRole.WEFT, weft, "40")));

    assertThat(suggestion.composition())
        .extracting(FabricCompositionDto::isoCode, line -> line.percentage().toPlainString())
        .containsExactly(tuple("CO", "80.00"), tuple("LI", "20.00"));
    assertThat(suggestion.problems()).isEmpty();
    assertThat(suggestion.yarnsWithoutComposition()).isEmpty();
  }

  @Test
  void halvesOfHundredthsAreRoundedSoTheLinesStillAddUpToExactlyOneHundred() {
    Product blend = yarn();
    Product polyester = yarn();
    known(blend, polyester);
    when(yarns.findDeclaredCompositions(any()))
        .thenReturn(
            Map.of(
                blend.getId(), List.of(fibre(CO, "CO", "33.33"), fibre(LI, "LI", "66.67")),
                polyester.getId(), List.of(fibre(PES, "PES", "100"))));

    FabricCompositionSuggestionDto suggestion =
        service.suggest(
            List.of(
                line(FabricYarnRole.WARP, blend, "50"),
                line(FabricYarnRole.WEFT, polyester, "50")));

    // 16.665 + 33.335 + 50: one hundredth is left over and goes to CO, the first of the ties.
    assertThat(suggestion.composition())
        .extracting(FabricCompositionDto::isoCode, line -> line.percentage().toPlainString())
        .containsExactly(tuple("PES", "50.00"), tuple("LI", "33.33"), tuple("CO", "16.67"));
  }

  @Test
  void sharesShortOfOneHundredAreScaledAndAYarnWithoutCompositionIsReported() {
    Product warp = yarn();
    Product weft = yarn();
    known(warp, weft);
    when(yarns.findDeclaredCompositions(any()))
        .thenReturn(Map.of(warp.getId(), List.of(fibre(CO, "CO", "100"))));

    FabricCompositionSuggestionDto suggestion =
        service.suggest(
            List.of(line(FabricYarnRole.WARP, warp, "50"), line(FabricYarnRole.WEFT, weft, "30")));

    assertThat(suggestion.composition())
        .extracting(FabricCompositionDto::isoCode, line -> line.percentage().toPlainString())
        .containsExactly(tuple("CO", "100.00"));
    assertThat(suggestion.problems())
        .containsExactly(
            FabricCompositionSuggestionService.YARN_SHARES_INCOMPLETE,
            FabricCompositionSuggestionService.YARN_COMPOSITION_MISSING);
    assertThat(suggestion.yarnsWithoutComposition()).containsExactly(weft.getId());
  }

  @Test
  void noYarnsMeansNoSuggestion() {
    assertThat(service.suggest(List.of()).problems())
        .containsExactly(FabricCompositionSuggestionService.NO_YARNS);
  }

  private void known(Product... yarnProducts) {
    when(products.findByTenantIdAndIdInAndProductType(eq(TENANT), any(), eq(ProductType.YARN)))
        .thenReturn(List.of(yarnProducts));
    when(yarns.findByProductIds(any())).thenReturn(List.of());
  }

  private static Product yarn() {
    Product product = Product.create(ProductType.YARN, "KG", null, null);
    ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(product, "uid", "YARN-" + product.getId());
    return product;
  }

  private static FabricYarnRequest line(FabricYarnRole role, Product yarn, String share) {
    return new FabricYarnRequest(role, yarn.getId(), new BigDecimal(share));
  }

  private static YarnCompositionLineDto fibre(UUID id, String iso, String percentage) {
    return new YarnCompositionLineDto(
        id, iso, iso + " fibre", MaterialSource.VIRGIN, new BigDecimal(percentage));
  }
}
