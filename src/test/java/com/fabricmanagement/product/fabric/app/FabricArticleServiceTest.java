package com.fabricmanagement.product.fabric.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.app.ProductService;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.CreateProductRequest;
import com.fabricmanagement.product.core.dto.ProductDto;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.fabric.domain.article.FabricArticle;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricArticleStatus;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricFormation;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricYarnRole;
import com.fabricmanagement.product.fabric.dto.FabricArticleDto;
import com.fabricmanagement.product.fabric.dto.FabricArticleRequest;
import com.fabricmanagement.product.fabric.dto.FabricCompositionDto;
import com.fabricmanagement.product.fabric.dto.FabricCompositionRequest;
import com.fabricmanagement.product.fabric.dto.FabricYarnRequest;
import com.fabricmanagement.product.fabric.infra.repository.FabricArticleRepository;
import com.fabricmanagement.product.fiber.api.facade.FiberFacade;
import com.fabricmanagement.product.fiber.domain.FiberKind;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.fiber.dto.FiberDto;
import com.fabricmanagement.product.fiber.dto.FiberIsoCodeDto;
import com.fabricmanagement.product.yarn.api.facade.YarnFacade;
import com.fabricmanagement.product.yarn.dto.YarnArticleSummaryDto;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** FABRIC-1: creating, editing, attaching and deleting a fabric article around its product. */
@ExtendWith(MockitoExtension.class)
class FabricArticleServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

  @Mock private FabricArticleRepository articles;
  @Mock private FabricConstructionCatalogService constructions;
  @Mock private ProductService products;
  @Mock private ProductRepository productRepository;
  @Mock private YarnFacade yarns;
  @Mock private FiberFacade fibers;

  private FabricArticleService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    service =
        new FabricArticleService(
            articles,
            constructions,
            products,
            productRepository,
            new FabricMaterialResolver(productRepository, yarns, fibers),
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void creatingAnArticleCreatesItsNamedFabricProductInMetresAsADraft() {
    Product product = fabric("Cotton Poplin 120gsm");
    when(products.createProduct(any(CreateProductRequest.class)))
        .thenReturn(ProductDto.builder().id(product.getId()).build());
    when(productRepository.findByTenantIdInAndId(List.of(TENANT), product.getId()))
        .thenReturn(Optional.of(product));
    when(articles.save(any(FabricArticle.class))).thenAnswer(call -> call.getArgument(0));

    FabricArticleDto created = service.create(request("Cotton Poplin 120gsm", "PM-1454"));

    ArgumentCaptor<CreateProductRequest> productRequest =
        ArgumentCaptor.forClass(CreateProductRequest.class);
    verify(products).createProduct(productRequest.capture());
    assertThat(productRequest.getValue().getProductType()).isEqualTo(ProductType.FABRIC);
    assertThat(productRequest.getValue().getUnit()).isEqualTo("M");
    assertThat(productRequest.getValue().getName()).isEqualTo("Cotton Poplin 120gsm");
    assertThat(created.status()).isEqualTo(FabricArticleStatus.DRAFT);
    assertThat(created.name()).isEqualTo("Cotton Poplin 120gsm");
    assertThat(created.articleCode()).isEqualTo("PM-1454");
    assertThat(created.woven()).isNotNull();
  }

  @Test
  void anArticleCodeIsUniqueWithinTheTenantAndNothingIsCreatedWhenItIsTaken() {
    when(articles.existsByTenantIdAndArticleCodeIgnoreCaseAndDeletedAtIsNull(TENANT, "PM-1454"))
        .thenReturn(true);

    assertThatThrownBy(() -> service.create(request("Another poplin", " PM-1454 ")))
        .isInstanceOf(FabricDomainException.class)
        .hasMessageContaining("PM-1454");
    verify(products, never()).createProduct(any());
  }

  @Test
  void anUpdateRenamesTheProductButCannotChangeTheFormation() {
    FabricArticle article = FabricArticle.draft(fabric("Cotton Poplin"), FabricFormation.WOVEN);
    ReflectionTestUtils.setField(article, "id", UUID.randomUUID());
    when(articles.findByTenantIdAndIdAndDeletedAtIsNull(TENANT, article.getId()))
        .thenReturn(Optional.of(article));

    FabricArticleRequest knitted =
        new FabricArticleRequest(
            "Cotton Poplin",
            null,
            FabricFormation.KNITTED,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThatThrownBy(() -> service.update(article.getId(), knitted))
        .isInstanceOf(FabricDomainException.class)
        .hasMessageContaining("formation");

    when(articles.save(any(FabricArticle.class))).thenAnswer(call -> call.getArgument(0));
    service.update(article.getId(), request("Cotton Poplin 120gsm", null));
    verify(products).describe(article.getProductId(), "Cotton Poplin 120gsm", null);
  }

  @Test
  void deletingADraftRetiresItsProductToo() {
    Product product = fabric("Draft fabric");
    FabricArticle article = FabricArticle.draft(product, FabricFormation.WOVEN);
    ReflectionTestUtils.setField(article, "id", UUID.randomUUID());
    when(articles.findByTenantIdAndIdAndDeletedAtIsNull(TENANT, article.getId()))
        .thenReturn(Optional.of(article));

    service.delete(article.getId());

    assertThat(article.getDeletedAt()).isEqualTo(NOW);
    assertThat(product.getIsActive()).isFalse();
    assertThat(product.getDeletedAt()).isEqualTo(NOW);
    verify(productRepository).save(product);
  }

  @Test
  void aProductThatAlreadyHasAnArticleIsNotGivenASecond() {
    Product product = fabric("Gabardine");
    when(productRepository.findByTenantIdInAndId(List.of(TENANT), product.getId()))
        .thenReturn(Optional.of(product));
    when(articles.findLiveByProduct(TENANT, product.getId()))
        .thenReturn(Optional.of(FabricArticle.draft(product, FabricFormation.WOVEN)));

    assertThatThrownBy(() -> service.attach(product.getId(), request("Gabardine", null)))
        .isInstanceOf(FabricDomainException.class)
        .hasMessageContaining("already");
    verify(articles, never()).save(any());
    verify(products, never()).describe(eq(product.getId()), anyString(), any());
  }

  @Test
  void yarnsAndFibresAreTheTenantsOwnAndKeepTheirNamesAsSnapshots() {
    FabricArticle article = FabricArticle.draft(fabric("Linen Blend"), FabricFormation.WOVEN);
    ReflectionTestUtils.setField(article, "id", UUID.randomUUID());
    when(articles.findByTenantIdAndIdAndDeletedAtIsNull(TENANT, article.getId()))
        .thenReturn(Optional.of(article));
    when(articles.save(any(FabricArticle.class))).thenAnswer(call -> call.getArgument(0));
    Product warp = Product.create(ProductType.YARN, "KG", null, null);
    ReflectionTestUtils.setField(warp, "id", UUID.randomUUID());
    UUID strangerYarn = UUID.randomUUID();
    UUID linen = UUID.randomUUID();
    UUID cotton = UUID.randomUUID();
    UUID blend = UUID.randomUUID();
    when(productRepository.findByTenantIdAndIdInAndProductType(
            eq(TENANT), any(), eq(ProductType.YARN)))
        .thenReturn(List.of(warp));
    when(yarns.findByProductIds(any()))
        .thenReturn(
            List.of(
                new YarnArticleSummaryDto(
                    warp.getId(), UUID.randomUUID(), null, null, "Nm 34/1 LI")));
    when(fibers.findById(linen))
        .thenReturn(Optional.of(fibre(linen, "LI", "Linen", FiberKind.PURE)));
    when(fibers.findById(cotton))
        .thenReturn(Optional.of(fibre(cotton, "CO", "Cotton", FiberKind.PURE)));
    when(fibers.findById(blend))
        .thenReturn(Optional.of(fibre(blend, "CO", "Cotton-linen blend", FiberKind.BLEND)));

    FabricArticleDto saved =
        service.update(
            article.getId(),
            withMaterials(
                request("Linen Blend", null),
                List.of(
                    new FabricYarnRequest(
                        FabricYarnRole.WARP, warp.getId(), new BigDecimal("100"))),
                List.of(
                    new FabricCompositionRequest(linen, new BigDecimal("55")),
                    new FabricCompositionRequest(cotton, new BigDecimal("45")))));

    assertThat(saved.yarns())
        .singleElement()
        .satisfies(
            yarn -> {
              assertThat(yarn.designation()).isEqualTo("Nm 34/1 LI");
              assertThat(yarn.role()).isEqualTo(FabricYarnRole.WARP);
            });
    assertThat(saved.composition())
        .extracting(FabricCompositionDto::isoCode, FabricCompositionDto::fiberName)
        .containsExactly(tuple("LI", "Linen"), tuple("CO", "Cotton"));
    assertThat(service.update(article.getId(), request("Linen Blend", null)).composition())
        .as("an update that leaves the composition out keeps it")
        .hasSize(2);

    assertThatThrownBy(
            () ->
                service.update(
                    article.getId(),
                    withMaterials(
                        request("Linen Blend", null),
                        List.of(
                            new FabricYarnRequest(
                                FabricYarnRole.WARP, strangerYarn, new BigDecimal("100"))),
                        null)))
        .isInstanceOf(FabricDomainException.class)
        .hasMessageContaining("Not a yarn");
    assertThatThrownBy(
            () ->
                service.update(
                    article.getId(),
                    withMaterials(
                        request("Linen Blend", null),
                        null,
                        List.of(new FabricCompositionRequest(blend, new BigDecimal("100"))))))
        .isInstanceOf(FabricDomainException.class)
        .hasMessageContaining("blend");
  }

  private static FabricArticleRequest request(String name, String code) {
    return new FabricArticleRequest(
        name,
        null,
        FabricFormation.WOVEN,
        code,
        null,
        null,
        new BigDecimal("120"),
        null,
        null,
        null,
        null,
        new BigDecimal("120"),
        null,
        null,
        null,
        null,
        null);
  }

  private static FabricArticleRequest withMaterials(
      FabricArticleRequest request,
      List<FabricYarnRequest> yarns,
      List<FabricCompositionRequest> composition) {
    return new FabricArticleRequest(
        request.name(),
        request.description(),
        request.formation(),
        request.articleCode(),
        request.constructionId(),
        request.colourState(),
        request.gsmFinished(),
        request.gsmGreige(),
        request.nominalFinishedWidthCm(),
        request.usableWidthCm(),
        request.nominalGreigeWidthCm(),
        request.standardRollLengthM(),
        request.remarks(),
        request.woven(),
        request.knitted(),
        yarns,
        composition);
  }

  private static FiberDto fibre(UUID id, String iso, String name, FiberKind kind) {
    return FiberDto.builder()
        .id(id)
        .kind(kind)
        .fiberName(name)
        .materialSource(MaterialSource.VIRGIN)
        .isoCode(FiberIsoCodeDto.builder().isoCode(iso).build())
        .build();
  }

  private static Product fabric(String name) {
    Product product = Product.create(ProductType.FABRIC, "M", name, null);
    ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(product, "tenantId", TENANT);
    return product;
  }
}
