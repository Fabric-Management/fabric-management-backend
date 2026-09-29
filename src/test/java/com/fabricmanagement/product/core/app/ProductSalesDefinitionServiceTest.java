package com.fabricmanagement.product.core.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.product.core.domain.ProductFinishedWidth;
import com.fabricmanagement.product.core.domain.ProductSalesUnit;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.dto.ProductSalesDefinitionDto;
import com.fabricmanagement.product.core.dto.UpdateProductSalesDefinitionRequest;
import com.fabricmanagement.product.core.infra.repository.ProductFinishedWidthRepository;
import com.fabricmanagement.product.core.infra.repository.ProductSalesUnitRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSalesDefinitionServiceTest {

  @Mock private ProductSalesDefinitionQueryService queries;
  @Mock private ProductFinishedWidthRepository widthRepository;
  @Mock private ProductSalesUnitRepository unitRepository;
  @InjectMocks private ProductSalesDefinitionService service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void replaceKeepsMatchingOptionsRetiresRemovedOnesAndIgnoresTheBaseUnit() {
    ProductFinishedWidth w150 = ProductFinishedWidth.of(productId, new BigDecimal("150"), "CM");
    ProductFinishedWidth w160 = ProductFinishedWidth.of(productId, new BigDecimal("160"), "CM");
    ProductSalesUnit yard = ProductSalesUnit.of(productId, "YD");
    when(queries.find(tenantId, productId)).thenReturn(Optional.of(definition()));
    when(widthRepository.findByTenantIdAndProductIdAndIsActiveTrueOrderByWidthUnitAscWidthValueAsc(
            tenantId, productId))
        .thenReturn(List.of(w150, w160));
    when(unitRepository.findByTenantIdAndProductIdAndIsActiveTrueOrderByUnitAsc(
            tenantId, productId))
        .thenReturn(List.of(yard));

    service.replace(
        productId,
        new UpdateProductSalesDefinitionRequest(
            List.of("m", "yd"),
            List.of(
                new UpdateProductSalesDefinitionRequest.FinishedWidthInput(
                    new BigDecimal("160.0"), "cm"),
                new UpdateProductSalesDefinitionRequest.FinishedWidthInput(
                    new BigDecimal("180"), "CM"))));

    assertThat(w150.getIsActive()).isFalse();
    assertThat(w160.getIsActive()).isTrue();
    assertThat(yard.getIsActive()).isTrue();
    ArgumentCaptor<ProductFinishedWidth> saved =
        ArgumentCaptor.forClass(ProductFinishedWidth.class);
    verify(widthRepository, atLeastOnce()).save(saved.capture());
    List<ProductFinishedWidth> added = new ArrayList<>(saved.getAllValues());
    added.remove(w150);
    assertThat(added)
        .singleElement()
        .satisfies(
            width -> {
              assertThat(width.getWidthValue()).isEqualByComparingTo("180");
              assertThat(width.getWidthUnit()).isEqualTo("CM");
            });
    verify(unitRepository, never()).save(any(ProductSalesUnit.class));
  }

  @Test
  void unknownProductIsNotFound() {
    when(queries.find(tenantId, productId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.replace(
                    productId, new UpdateProductSalesDefinitionRequest(List.of(), List.of())))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void widthsWithMoreThanTwoDecimalsAreRejected() {
    assertThatThrownBy(() -> ProductFinishedWidth.of(productId, new BigDecimal("160.005"), "CM"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ProductFinishedWidth.of(productId, new BigDecimal("160"), "MM"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ProductSalesDefinitionDto definition() {
    return new ProductSalesDefinitionDto(
        productId, "FAB-1", "Satin", ProductType.FABRIC, "M", true, List.of("YD"), List.of());
  }
}
