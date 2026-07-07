package com.fabricmanagement.sales.salesproduct.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesproduct.domain.SalesProduct;
import com.fabricmanagement.sales.salesproduct.dto.CreateSalesProductRequest;
import com.fabricmanagement.sales.salesproduct.dto.SalesProductDto;
import com.fabricmanagement.sales.salesproduct.infra.repository.SalesProductRepository;
import com.fabricmanagement.sales.salesproduct.mapper.SalesProductMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesProductServiceTest {

  @Mock private SalesProductRepository repository;

  private final SalesProductMapper mapper = Mappers.getMapper(SalesProductMapper.class);
  private final UUID tenantId = UUID.randomUUID();
  private final UUID productId = UUID.randomUUID();
  private SalesProductService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    service = new SalesProductService(repository, mapper);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createEntryPersistsProductNameSnapshot() {
    CreateSalesProductRequest request =
        new CreateSalesProductRequest(
            productId,
            "Combed cotton fabric",
            "FABRIC",
            new BigDecimal("12.50"),
            "GBP",
            null,
            null,
            null,
            null,
            null);
    when(repository.save(any(SalesProduct.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    SalesProductDto dto = service.createEntry(request);

    ArgumentCaptor<SalesProduct> captor = ArgumentCaptor.forClass(SalesProduct.class);
    verify(repository).save(captor.capture());
    assertThat(captor.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(captor.getValue().getProductName()).isEqualTo("Combed cotton fabric");
    assertThat(dto.getProductName()).isEqualTo("Combed cotton fabric");
  }

  @Test
  void getActiveCatalogForModuleReturnsProductNameSnapshot() {
    SalesProduct catalogItem = new SalesProduct();
    catalogItem.setTenantId(tenantId);
    catalogItem.setProductId(productId);
    catalogItem.setProductName("Combed cotton fabric");
    catalogItem.setModuleType("FABRIC");
    catalogItem.setListPrice(new BigDecimal("12.50"));
    catalogItem.setCurrency("GBP");
    catalogItem.setSpecs("{}");
    catalogItem.setPhotos("[]");

    when(repository.findAllByTenantIdAndModuleTypeAndIsActiveTrue(tenantId, "FABRIC"))
        .thenReturn(List.of(catalogItem));

    List<SalesProductDto> result = service.getActiveCatalogForModule("FABRIC");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getProductName()).isEqualTo("Combed cotton fabric");
  }
}
