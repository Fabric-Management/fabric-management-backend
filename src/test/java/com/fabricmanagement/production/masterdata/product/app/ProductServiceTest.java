package com.fabricmanagement.production.masterdata.product.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.masterdata.fiber.api.facade.FiberFacade;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import com.fabricmanagement.production.masterdata.product.domain.Product;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.domain.reference.ProductAttribute;
import com.fabricmanagement.production.masterdata.product.dto.ProductAttributeDto;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
import com.fabricmanagement.production.masterdata.product.infra.repository.ProductAttributeRepository;
import com.fabricmanagement.production.masterdata.product.infra.repository.ProductRepository;
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

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock private ProductRepository productRepository;
  @Mock private ProductAttributeRepository productAttributeRepository;
  @Mock private FiberFacade fiberFacade;
  @Mock private DomainEventPublisher eventPublisher;

  private ProductService productService;

  @BeforeEach
  void setUp() {
    productService =
        new ProductService(
            productRepository, productAttributeRepository, fiberFacade, eventPublisher);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void findByType_whenFiber_shouldEnrichWithFiberName() {
    // Arrange
    UUID tenantId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    Product fiberProduct = Product.create(ProductType.FIBER, "KG");
    ReflectionTestUtils.setField(fiberProduct, "id", productId);
    ReflectionTestUtils.setField(fiberProduct, "tenantId", tenantId);
    ReflectionTestUtils.setField(fiberProduct, "uid", "PRD-1000");

    when(productRepository.findByTenantIdInAndProductTypeAndIsActiveTrue(
            List.of(
                tenantId,
                com.fabricmanagement.common.infrastructure.persistence.TenantContext
                    .TEMPLATE_TENANT_ID),
            ProductType.FIBER))
        .thenReturn(List.of(fiberProduct));

    FiberDto fiberDto = FiberDto.builder().productId(productId).fiberName("Cotton Organic").build();

    when(fiberFacade.findByProductIds(List.of(productId))).thenReturn(List.of(fiberDto));

    // Act
    List<ProductDto> result = productService.findByType(tenantId, ProductType.FIBER);

    // Assert
    assertThat(result).hasSize(1);
    ProductDto dto = result.get(0);
    assertThat(dto.getProductType()).isEqualTo(ProductType.FIBER);
    assertThat(dto.getDisplayName()).isEqualTo("Cotton Organic");
  }

  @Test
  void findByType_whenYarn_shouldFallbackToUid() {
    // Arrange
    UUID tenantId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    Product yarnProduct = Product.create(ProductType.YARN, "KG");
    ReflectionTestUtils.setField(yarnProduct, "id", productId);
    ReflectionTestUtils.setField(yarnProduct, "tenantId", tenantId);
    ReflectionTestUtils.setField(yarnProduct, "uid", "PRD-2000");

    when(productRepository.findByTenantIdInAndProductTypeAndIsActiveTrue(
            List.of(
                tenantId,
                com.fabricmanagement.common.infrastructure.persistence.TenantContext
                    .TEMPLATE_TENANT_ID),
            ProductType.YARN))
        .thenReturn(List.of(yarnProduct));

    // Act
    List<ProductDto> result = productService.findByType(tenantId, ProductType.YARN);

    // Assert
    assertThat(result).hasSize(1);
    ProductDto dto = result.get(0);
    assertThat(dto.getProductType()).isEqualTo(ProductType.YARN);
    assertThat(dto.getDisplayName()).isEqualTo("PRD-2000");
  }

  @Test
  void ensureAttribute_whenAttributeExistsForTenant_returnsItWithoutCreating() {
    // Arrange
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);

    ProductAttribute existing =
        ProductAttribute.builder()
            .attributeCode("COLOR")
            .attributeName("Colour")
            .attributeGroup("VARIANT")
            .productScope("ALL")
            .build();
    existing.setId(UUID.randomUUID());
    existing.setTenantId(tenantId);

    when(productAttributeRepository.findFirstByTenantIdAndAttributeCode(tenantId, "COLOR"))
        .thenReturn(Optional.of(existing));

    // Act
    ProductAttributeDto result =
        productService.ensureAttribute(
            "COLOR", "Colour", "VARIANT", "ALL", "Colour card reference", 100);

    // Assert
    assertThat(result.id()).isEqualTo(existing.getId());
    assertThat(result.attributeCode()).isEqualTo("COLOR");
    verify(productAttributeRepository, never()).save(any());
  }

  @Test
  void ensureAttribute_whenMissing_createsTenantScopedAttribute() {
    // Arrange
    UUID tenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);

    when(productAttributeRepository.findFirstByTenantIdAndAttributeCode(tenantId, "COLOR"))
        .thenReturn(Optional.empty());
    when(productAttributeRepository.save(any(ProductAttribute.class)))
        .thenAnswer(
            invocation -> {
              ProductAttribute attribute = invocation.getArgument(0);
              attribute.setId(UUID.randomUUID());
              return attribute;
            });

    // Act
    ProductAttributeDto result =
        productService.ensureAttribute(
            "COLOR", "Colour", "VARIANT", "ALL", "Colour card reference", 100);

    // Assert
    ArgumentCaptor<ProductAttribute> captor = ArgumentCaptor.forClass(ProductAttribute.class);
    verify(productAttributeRepository).save(captor.capture());
    ProductAttribute saved = captor.getValue();
    assertThat(saved.getTenantId()).isEqualTo(tenantId);
    assertThat(saved.getAttributeCode()).isEqualTo("COLOR");
    assertThat(saved.getAttributeGroup()).isEqualTo("VARIANT");
    assertThat(saved.getProductScope()).isEqualTo("ALL");
    assertThat(result.id()).isNotNull();
    assertThat(result.displayOrder()).isEqualTo(100);
  }
}
