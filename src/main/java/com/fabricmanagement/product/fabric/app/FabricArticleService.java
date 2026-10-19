package com.fabricmanagement.product.fabric.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.core.app.ProductService;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.CreateProductRequest;
import com.fabricmanagement.product.core.dto.ProductDto;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.fabric.domain.article.FabricArticle;
import com.fabricmanagement.product.fabric.domain.article.FabricArticleSpec;
import com.fabricmanagement.product.fabric.domain.exception.FabricDomainException;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricArticleStatus;
import com.fabricmanagement.product.fabric.dto.FabricArticleDto;
import com.fabricmanagement.product.fabric.dto.FabricArticleListItemDto;
import com.fabricmanagement.product.fabric.dto.FabricArticleRequest;
import com.fabricmanagement.product.fabric.infra.repository.FabricArticleRepository;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fabric article lifecycle (FABRIC-1). Creating an article creates its FABRIC product in the same
 * transaction, so a fabric never exists without its name; the product keeps the name and
 * description, the article everything else.
 */
@Service
@RequiredArgsConstructor
public class FabricArticleService {

  /** Fabric is sold and stocked in metres unless a sales definition adds other units. */
  static final String FABRIC_UNIT = "M";

  private final FabricArticleRepository articles;
  private final FabricConstructionCatalogService constructions;
  private final ProductService products;
  private final ProductRepository productRepository;
  private final Clock clock;

  @Transactional
  public FabricArticleDto create(FabricArticleRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    requireFreeCode(tenantId, request.articleCode(), null);
    ProductDto created =
        products.createProduct(
            CreateProductRequest.builder()
                .productType(ProductType.FABRIC)
                .unit(FABRIC_UNIT)
                .name(request.name())
                .description(request.description())
                .build());
    Product product =
        productRepository
            .findByTenantIdInAndId(List.of(tenantId), created.getId())
            .orElseThrow(() -> new IllegalStateException("Created product not found"));
    FabricArticle article = FabricArticle.draft(product, request.formation());
    article.apply(spec(request));
    return FabricArticleDto.from(articles.save(article));
  }

  /**
   * Gives an existing FABRIC product its article: fabrics created before FABRIC-1, or by a flow
   * that creates the product first. The request's name and description become the product's.
   */
  @Transactional
  public FabricArticleDto attach(UUID productId, FabricArticleRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    Product product =
        productRepository
            .findByTenantIdInAndId(List.of(tenantId), productId)
            .filter(candidate -> candidate.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Product not found: " + productId));
    if (articles.findLiveByProduct(tenantId, productId).isPresent()) {
      throw new FabricDomainException("The product already has a fabric article");
    }
    requireFreeCode(tenantId, request.articleCode(), null);
    product.describe(request.name(), request.description());
    FabricArticle article = FabricArticle.draft(product, request.formation());
    article.apply(spec(request));
    return FabricArticleDto.from(articles.save(article));
  }

  @Transactional
  public FabricArticleDto update(UUID id, FabricArticleRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    FabricArticle article = require(id);
    if (request.formation() != article.getFormation()) {
      throw new FabricDomainException(
          "A fabric's formation cannot change; create a new " + request.formation() + " article");
    }
    requireFreeCode(tenantId, request.articleCode(), article.getId());
    products.describe(article.getProductId(), request.name(), request.description());
    article.apply(spec(request));
    return FabricArticleDto.from(articles.save(article));
  }

  @Transactional(readOnly = true)
  public FabricArticleDto get(UUID id) {
    return FabricArticleDto.from(require(id));
  }

  @Transactional(readOnly = true)
  public FabricArticleDto getByProduct(UUID productId) {
    return findByProduct(productId)
        .orElseThrow(() -> new NotFoundException("No fabric article for product: " + productId));
  }

  /** The product's live article, if any; never throws, so a caller's transaction stays usable. */
  @Transactional(readOnly = true)
  public Optional<FabricArticleDto> findByProduct(UUID productId) {
    return articles
        .findLiveByProduct(TenantContext.requireTenantId(), productId)
        .map(FabricArticleDto::from);
  }

  /** Live articles whose name or article code contains {@code q}, optionally of one status. */
  @Transactional(readOnly = true)
  public Page<FabricArticleListItemDto> list(
      FabricArticleStatus status, String q, Pageable pageable) {
    UUID tenantId = TenantContext.requireTenantId();
    String pattern = "%" + escapeLike(q == null ? "" : q.trim().toLowerCase(Locale.ROOT)) + "%";
    Page<FabricArticle> page =
        status == null
            ? articles.search(tenantId, pattern, pageable)
            : articles.searchByStatus(tenantId, status, pattern, pageable);
    return page.map(FabricArticleListItemDto::from);
  }

  @Transactional
  public FabricArticleDto activate(UUID id) {
    FabricArticle article = require(id);
    article.activate();
    return FabricArticleDto.from(articles.save(article));
  }

  @Transactional
  public FabricArticleDto obsolete(UUID id) {
    FabricArticle article = require(id);
    article.obsolete();
    return FabricArticleDto.from(articles.save(article));
  }

  /** Deletes a never-used draft and retires its product with it. */
  @Transactional
  public void delete(UUID id) {
    FabricArticle article = require(id);
    article.delete(clock.instant());
    Product product = article.getProduct();
    product.setIsActive(false);
    product.setDeletedAt(clock.instant());
    articles.save(article);
    productRepository.save(product);
  }

  private FabricArticle require(UUID id) {
    return articles
        .findByTenantIdAndIdAndDeletedAtIsNull(TenantContext.requireTenantId(), id)
        .orElseThrow(() -> new NotFoundException("Fabric article not found: " + id));
  }

  private FabricArticleSpec spec(FabricArticleRequest request) {
    return new FabricArticleSpec(
        request.articleCode(),
        request.constructionId() == null ? null : constructions.require(request.constructionId()),
        request.colourState(),
        request.gsmFinished(),
        request.gsmGreige(),
        request.nominalFinishedWidthCm(),
        request.usableWidthCm(),
        request.nominalGreigeWidthCm(),
        request.standardRollLengthM(),
        request.remarks(),
        request.woven() == null
            ? null
            : new FabricArticleSpec.Woven(
                request.woven().endsPerCm(),
                request.woven().picksPerCm(),
                request.woven().reedNotation(),
                request.woven().weaveNotation()),
        request.knitted() == null
            ? null
            : new FabricArticleSpec.Knitted(
                request.knitted().machineGauge(),
                request.knitted().machineDiameterIn(),
                request.knitted().coursesPerCm(),
                request.knitted().walesPerCm(),
                request.knitted().stitchLengthMm(),
                request.knitted().feeders()));
  }

  private void requireFreeCode(UUID tenantId, String code, UUID self) {
    if (code == null || code.isBlank()) return;
    String trimmed = code.trim();
    boolean taken =
        self == null
            ? articles.existsByTenantIdAndArticleCodeIgnoreCaseAndDeletedAtIsNull(tenantId, trimmed)
            : articles.existsByTenantIdAndArticleCodeIgnoreCaseAndDeletedAtIsNullAndIdNot(
                tenantId, trimmed, self);
    if (taken) {
      throw new FabricDomainException("Another fabric already uses article code " + trimmed);
    }
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
