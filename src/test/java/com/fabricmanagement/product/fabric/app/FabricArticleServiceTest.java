package com.fabricmanagement.product.fabric.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.fabricmanagement.product.fabric.dto.FabricArticleDto;
import com.fabricmanagement.product.fabric.dto.FabricArticleRequest;
import com.fabricmanagement.product.fabric.infra.repository.FabricArticleRepository;
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

  private FabricArticleService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    service =
        new FabricArticleService(
            articles, constructions, products, productRepository, Clock.fixed(NOW, ZoneOffset.UTC));
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
        null);
  }

  private static Product fabric(String name) {
    Product product = Product.create(ProductType.FABRIC, "M", name, null);
    ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(product, "tenantId", TENANT);
    return product;
  }
}
