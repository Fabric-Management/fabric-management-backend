package com.fabricmanagement.production.masterdata.product.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.production.masterdata.product.domain.Product;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
import com.fabricmanagement.production.masterdata.product.infra.repository.ProductAttributeRepository;
import com.fabricmanagement.production.masterdata.product.infra.repository.ProductRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

  @Mock private ProductRepository productRepository;
  @Mock private ProductAttributeRepository productAttributeRepository;
  @Mock private FiberRepository fiberRepository;
  @Mock private DomainEventPublisher eventPublisher;

  private ProductService productService;

  @BeforeEach
  void setUp() {
    productService =
        new ProductService(
            productRepository, productAttributeRepository, fiberRepository, eventPublisher);
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

    when(productRepository.findByTenantIdAndProductTypeAndIsActiveTrue(tenantId, ProductType.FIBER))
        .thenReturn(List.of(fiberProduct));

    Fiber fiber = new Fiber();
    ReflectionTestUtils.setField(fiber, "tenantId", tenantId);
    ReflectionTestUtils.setField(fiber, "fiberName", "Cotton Organic");
    ReflectionTestUtils.setField(fiber, "product", fiberProduct);

    when(fiberRepository.findByProductIdIn(List.of(productId))).thenReturn(List.of(fiber));

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

    when(productRepository.findByTenantIdAndProductTypeAndIsActiveTrue(tenantId, ProductType.YARN))
        .thenReturn(List.of(yarnProduct));

    // Act
    List<ProductDto> result = productService.findByType(tenantId, ProductType.YARN);

    // Assert
    assertThat(result).hasSize(1);
    ProductDto dto = result.get(0);
    assertThat(dto.getProductType()).isEqualTo(ProductType.YARN);
    assertThat(dto.getDisplayName()).isEqualTo("PRD-2000");
  }
}
