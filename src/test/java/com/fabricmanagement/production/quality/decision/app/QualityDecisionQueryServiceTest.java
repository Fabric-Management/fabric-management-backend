package com.fabricmanagement.production.quality.decision.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.quality.decision.infra.repository.QualityDecisionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class QualityDecisionQueryServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");

  @Mock private QualityDecisionRepository decisionRepository;
  @Mock private StockUnitRepository stockUnitRepository;

  private QualityDecisionQueryService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    service = new QualityDecisionQueryService(decisionRepository, stockUnitRepository);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void mapsTheSelfContainedQueueProjectionWithOneRepositoryCall() {
    var pageable = PageRequest.of(0, 20);
    var batchId = UUID.randomUUID();
    var productId = UUID.randomUUID();
    var colorId = UUID.randomUUID();
    var createdAt = Instant.parse("2026-07-21T08:00:00Z");
    var row = mock(StockUnitRepository.QualityQueueRow.class);
    when(row.getBatchId()).thenReturn(batchId);
    when(row.getBatchCode()).thenReturn("LOT-QC-001");
    when(row.getProductId()).thenReturn(productId);
    when(row.getProductUid()).thenReturn("SYS-MAT-000001");
    when(row.getProductType()).thenReturn(ProductType.FIBER.name());
    when(row.getProductDisplayName()).thenReturn("Cotton (100%)");
    when(row.getColorId()).thenReturn(colorId);
    when(row.getColorName()).thenReturn("Navy");
    when(row.getSupplierBatchCode()).thenReturn("SUP-LOT-9");
    when(row.getPendingUnitCount()).thenReturn(3L);
    when(row.getBatchCreatedAt()).thenReturn(createdAt);
    when(stockUnitRepository.findQualityQueue(TENANT_ID, pageable))
        .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

    var result = service.getQueue(pageable);

    assertThat(result)
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.batchId()).isEqualTo(batchId);
              assertThat(item.batchCode()).isEqualTo("LOT-QC-001");
              assertThat(item.productId()).isEqualTo(productId);
              assertThat(item.productUid()).isEqualTo("SYS-MAT-000001");
              assertThat(item.productType()).isEqualTo(ProductType.FIBER);
              assertThat(item.productDisplayName()).isEqualTo("Cotton (100%)");
              assertThat(item.colorId()).isEqualTo(colorId);
              assertThat(item.colorName()).isEqualTo("Navy");
              assertThat(item.supplierBatchCode()).isEqualTo("SUP-LOT-9");
              assertThat(item.pendingUnitCount()).isEqualTo(3);
              assertThat(item.batchCreatedAt()).isEqualTo(createdAt);
            });
    verify(stockUnitRepository).findQualityQueue(TENANT_ID, pageable);
  }
}
