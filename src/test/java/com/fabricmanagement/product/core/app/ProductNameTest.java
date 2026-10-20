package com.fabricmanagement.product.core.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.CreateProductRequest;
import com.fabricmanagement.product.core.dto.ProductDto;
import com.fabricmanagement.product.core.infra.repository.ProductAttributeRepository;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.fiber.api.facade.FiberFacade;
import com.fabricmanagement.product.fiber.dto.FiberDto;
import com.fabricmanagement.product.yarn.api.facade.YarnFacade;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * FABRIC-0: a product has its own name and description. Fabric, chemical and consumable have no
 * other master record to carry a name, so they cannot be created without one; fibre and yarn keep
 * the name of their own record, and the product's name is only their fallback.
 */
@ExtendWith(MockitoExtension.class)
class ProductNameTest {

  @Mock private ProductRepository productRepository;
  @Mock private ProductAttributeRepository productAttributeRepository;
  @Mock private FiberFacade fiberFacade;
  @Mock private YarnFacade yarnFacade;
  @Mock private DomainEventPublisher eventPublisher;

  private static final UUID TENANT_ID = UUID.randomUUID();

  private ProductService service;

  @BeforeEach
  void setUp() {
    service =
        new ProductService(
            productRepository, productAttributeRepository, fiberFacade, yarnFacade, eventPublisher);
  }

  @Test
  void aFabricIsCreatedWithItsTrimmedNameAndDescriptionAndShowsTheName() {
    when(productRepository.save(any(Product.class)))
        .thenAnswer(call -> persisted(call.getArgument(0)));

    ProductDto created =
        service.createProduct(
            CreateProductRequest.builder()
                .productType(ProductType.FABRIC)
                .unit("M")
                .name("  Cotton Poplin 120gsm  ")
                .description(" Plain-weave cotton for shirts. ")
                .build());

    assertThat(created.getName()).isEqualTo("Cotton Poplin 120gsm");
    assertThat(created.getDescription()).isEqualTo("Plain-weave cotton for shirts.");
    assertThat(created.getDisplayName()).isEqualTo("Cotton Poplin 120gsm");
    verify(eventPublisher).publish(any());
  }

  @Test
  void fabricChemicalAndConsumableNeedANameAndABlankOneIsNoName() {
    for (ProductType type :
        List.of(ProductType.FABRIC, ProductType.CHEMICAL, ProductType.CONSUMABLE)) {
      for (String name : new String[] {null, "", "   "}) {
        CreateProductRequest request =
            CreateProductRequest.builder().productType(type).unit("M").name(name).build();
        assertThatThrownBy(() -> service.createProduct(request))
            .as("%s with name '%s'", type, name)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("needs a name");
      }
    }
    verify(productRepository, never()).save(any());
  }

  @Test
  void fibreAndYarnMayBeCreatedWithoutAProductName() {
    when(productRepository.save(any(Product.class)))
        .thenAnswer(call -> persisted(call.getArgument(0)));

    for (ProductType type : List.of(ProductType.FIBER, ProductType.YARN)) {
      ProductDto created =
          service.createProduct(
              CreateProductRequest.builder().productType(type).unit("KG").build());
      assertThat(created.getName()).isNull();
      assertThat(created.getDescription()).isNull();
    }
  }

  @Test
  void displayNameIsTheFibreRecordThenTheProductsNameThenTheUid() {
    UUID tenantId = UUID.randomUUID();
    Product namedFabric = product(tenantId, ProductType.FABRIC, "PRD-F1", "Cotton Drill 240gsm");
    Product unnamedFabric = product(tenantId, ProductType.FABRIC, "PRD-F2", null);
    Product fibreWithRecord = product(tenantId, ProductType.FIBER, "PRD-C1", "Fallback name");
    Product fibreWithoutRecord = product(tenantId, ProductType.FIBER, "PRD-C2", "Raw cotton");
    when(productRepository.findByTenantIdInAndIsActiveTrue(
            List.of(tenantId, TenantContext.TEMPLATE_TENANT_ID)))
        .thenReturn(List.of(namedFabric, unnamedFabric, fibreWithRecord, fibreWithoutRecord));
    when(fiberFacade.findByProductIds(List.of(fibreWithRecord.getId(), fibreWithoutRecord.getId())))
        .thenReturn(
            List.of(
                FiberDto.builder()
                    .productId(fibreWithRecord.getId())
                    .fiberName("Organic cotton")
                    .build()));

    Map<UUID, ProductDto> shown =
        service.findByTenant(tenantId).stream()
            .collect(Collectors.toMap(ProductDto::getId, Function.identity()));

    assertThat(shown.get(namedFabric.getId()).getDisplayName()).isEqualTo("Cotton Drill 240gsm");
    assertThat(shown.get(unnamedFabric.getId()).getDisplayName()).isEqualTo("PRD-F2");
    assertThat(shown.get(fibreWithRecord.getId()).getDisplayName()).isEqualTo("Organic cotton");
    assertThat(shown.get(fibreWithoutRecord.getId()).getDisplayName()).isEqualTo("Raw cotton");
  }

  /** What persisting does that the mocked repository does not: assign the id and the tenant. */
  private Product persisted(Product product) {
    ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(product, "tenantId", TENANT_ID);
    return product;
  }

  private Product product(UUID tenantId, ProductType type, String uid, String name) {
    Product product = Product.create(type, "M", name, null);
    ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(product, "tenantId", tenantId);
    ReflectionTestUtils.setField(product, "uid", uid);
    return product;
  }
}
