package com.fabricmanagement.production.masterdata.product.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.ai.AIQueryNormalizer;
import com.fabricmanagement.production.masterdata.fiber.api.facade.FiberFacade;
import com.fabricmanagement.production.masterdata.fiber.domain.FiberStatus;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import com.fabricmanagement.production.masterdata.product.api.facade.ProductFacade;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.dto.CreateProductRequest;
import com.fabricmanagement.production.masterdata.product.dto.ProductDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductAIToolProviderTest {

  @Mock private ProductFacade productFacade;
  @Mock private FiberFacade fiberFacade;
  @Mock private AIQueryNormalizer queryNormalizer;

  @InjectMocks private ProductAIToolProvider productAIToolProvider;

  private UUID tenantId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
  }

  @Test
  @DisplayName("Should support all product tools")
  void shouldSupportProductTools() {
    assertThat(productAIToolProvider.getSupportedTools())
        .containsExactlyInAnyOrder(
            "check_product_stock", "create_product", "search_products", "get_production_status");
  }

  @Test
  @DisplayName("Should check product stock")
  void shouldCheckProductStock() {
    // Given
    String productName = "Cotton";
    ProductDto product =
        ProductDto.builder()
            .uid(productName)
            .productType(ProductType.FIBER)
            .unit("kg")
            .isActive(true)
            .build();
    when(productFacade.findByTenant(tenantId)).thenReturn(List.of(product));

    // When
    String result =
        productAIToolProvider.execute(
            tenantId, "check_product_stock", Map.of("productName", productName));

    // Then
    assertThat(result).contains("Cotton").contains("FIBER").contains("Active");
  }

  @Test
  @DisplayName("Should search products with fiber cross-reference")
  void shouldSearchProductsWithFiberCrossReference() {
    // Given
    String query = "Organic Cotton";
    UUID productId = UUID.randomUUID();
    ProductDto product =
        ProductDto.builder()
            .id(productId)
            .uid("MAT-001")
            .productType(ProductType.FIBER)
            .isActive(true)
            .build();
    FiberDto fiber =
        FiberDto.builder()
            .productId(productId)
            .fiberName(query)
            .uid("FIB-001")
            .status(FiberStatus.ACTIVE)
            .build();

    when(productFacade.findByTenant(tenantId)).thenReturn(List.of(product));
    when(queryNormalizer.normalizeFiberQuery(anyString())).thenReturn(query);
    when(fiberFacade.findByProductIds(any())).thenReturn(List.of(fiber));

    // When
    String result =
        productAIToolProvider.execute(tenantId, "search_products", Map.of("query", query));

    // Then
    assertThat(result).contains("Found 1 products").contains("MAT-001");
    verify(fiberFacade).findByProductIds(any());
  }

  @Test
  @DisplayName("Should provide production status summary")
  void shouldProvideProductionStatus() {
    // Given
    ProductDto mat1 = ProductDto.builder().productType(ProductType.FIBER).isActive(true).build();
    ProductDto mat2 = ProductDto.builder().productType(ProductType.YARN).isActive(true).build();
    when(productFacade.findByTenant(tenantId)).thenReturn(List.of(mat1, mat2));

    // When
    String result = productAIToolProvider.execute(tenantId, "get_production_status", Map.of());

    // Then
    assertThat(result).contains("Production Status Summary").contains("Active Products: 2");
    assertThat(result).contains("FIBER: 1").contains("YARN: 1");
  }

  @Test
  @DisplayName("Should create product successfully")
  void shouldCreateProduct() {
    // Given
    Map<String, Object> params = Map.of("productType", "FIBER", "unit", "kg");
    ProductDto created =
        ProductDto.builder()
            .id(UUID.randomUUID())
            .uid("MAT-001")
            .productType(ProductType.FIBER)
            .unit("kg")
            .build();
    when(productFacade.createProduct(any(CreateProductRequest.class))).thenReturn(created);

    // When
    String result = productAIToolProvider.execute(tenantId, "create_product", params);

    // Then
    assertThat(result).contains("Product created successfully!").contains("MAT-001");
  }
}
