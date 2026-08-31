package com.fabricmanagement.product.core.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.ProductDto;
import com.fabricmanagement.product.core.infra.repository.ProductAttributeRepository;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.fiber.api.facade.FiberFacade;
import com.fabricmanagement.product.fiber.dto.FiberDto;
import com.fabricmanagement.product.yarn.api.facade.YarnFacade;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus;
import com.fabricmanagement.product.yarn.dto.YarnArticleSummaryDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductDisplayNameTest {

  @Mock private ProductRepository productRepository;
  @Mock private ProductAttributeRepository productAttributeRepository;
  @Mock private FiberFacade fiberFacade;
  @Mock private YarnFacade yarnFacade;
  @Mock private DomainEventPublisher eventPublisher;

  @Test
  void enrichesFiberAndYarnInBatchesWithDesignationNameUidPrecedence() {
    UUID tenantId = UUID.randomUUID();
    Product fiber = product(tenantId, ProductType.FIBER, "PRD-FIBER");
    Product designated = product(tenantId, ProductType.YARN, "PRD-YARN-1");
    Product namedDraft = product(tenantId, ProductType.YARN, "PRD-YARN-2");
    Product bareDraft = product(tenantId, ProductType.YARN, "PRD-YARN-3");
    List<Product> products = List.of(fiber, designated, namedDraft, bareDraft);

    when(productRepository.findByTenantIdInAndIsActiveTrue(
            List.of(tenantId, TenantContext.TEMPLATE_TENANT_ID)))
        .thenReturn(products);
    when(fiberFacade.findByProductIds(List.of(fiber.getId())))
        .thenReturn(
            List.of(
                FiberDto.builder().productId(fiber.getId()).fiberName("Organic cotton").build()));
    when(yarnFacade.findByProductIds(
            List.of(designated.getId(), namedDraft.getId(), bareDraft.getId())))
        .thenReturn(
            List.of(
                new YarnArticleSummaryDto(
                    designated.getId(),
                    UUID.randomUUID(),
                    YarnArticleStatus.ACTIVE,
                    "Supplier wording",
                    "Ne 30/2"),
                new YarnArticleSummaryDto(
                    namedDraft.getId(),
                    UUID.randomUUID(),
                    YarnArticleStatus.DRAFT,
                    "Backfill draft",
                    null)));

    Map<UUID, ProductDto> result =
        new ProductService(
                productRepository,
                productAttributeRepository,
                fiberFacade,
                yarnFacade,
                eventPublisher)
            .findByTenant(tenantId).stream()
                .collect(Collectors.toMap(ProductDto::getId, Function.identity()));

    assertThat(result.get(fiber.getId()).getDisplayName()).isEqualTo("Organic cotton");
    assertThat(result.get(designated.getId()).getDisplayName()).isEqualTo("Ne 30/2");
    assertThat(result.get(namedDraft.getId()).getDisplayName()).isEqualTo("Backfill draft");
    assertThat(result.get(bareDraft.getId()).getDisplayName()).isEqualTo("PRD-YARN-3");
  }

  private Product product(UUID tenantId, ProductType type, String uid) {
    Product product = Product.create(type, "KG");
    ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(product, "tenantId", tenantId);
    ReflectionTestUtils.setField(product, "uid", uid);
    return product;
  }
}
