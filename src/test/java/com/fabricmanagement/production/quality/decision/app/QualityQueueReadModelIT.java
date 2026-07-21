package com.fabricmanagement.production.quality.decision.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.infra.repository.ColorRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.production.masterdata.product.domain.Product;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.infra.repository.ProductRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class QualityQueueReadModelIT extends AbstractIntegrationTest {

  @Autowired private QualityDecisionQueryService service;
  @Autowired private ProductRepository productRepository;
  @Autowired private FiberRepository fiberRepository;
  @Autowired private ColorRepository colorRepository;
  @Autowired private BatchRepository batchRepository;
  @Autowired private StockUnitRepository stockUnitRepository;

  private UUID tenantId;
  private UUID otherTenantId;
  private Product sharedProduct;
  private String sharedProductDisplayName;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    otherTenantId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(UUID.randomUUID());

    sharedProduct =
        productRepository
            .findByTenantIdAndProductTypeAndIsActiveTrue(
                TenantContext.TEMPLATE_TENANT_ID, ProductType.FIBER)
            .stream()
            .findFirst()
            .orElseThrow();
    sharedProductDisplayName =
        fiberRepository.findByProductId(sharedProduct.getId()).orElseThrow().getFiberName();
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void returnsSharedProductIdentityTenantColorColourlessRowsAndStablePagination() {
    Color navy =
        colorRepository.saveAndFlush(
            Color.create(tenantId, randomCode("NAVY"), "Inspection navy", "#1F2A44"));
    Batch oldest =
        saveBatch(
            tenantId,
            "LOT-QC-OLD-" + suffix(),
            "SUP-OLD",
            navy.getId(),
            Instant.parse("2026-07-20T08:00:00Z"));
    saveUnit(tenantId, oldest, QualityDisposition.PENDING_INSPECTION);
    saveUnit(tenantId, oldest, QualityDisposition.PENDING_INSPECTION);
    saveUnit(tenantId, oldest, QualityDisposition.RELEASED);

    Batch colourless =
        saveBatch(
            tenantId, "LOT-QC-NEW-" + suffix(), null, null, Instant.parse("2026-07-21T08:00:00Z"));
    saveUnit(tenantId, colourless, QualityDisposition.PENDING_INSPECTION);

    TenantContext.setCurrentTenantId(otherTenantId);
    Batch otherTenant =
        saveBatch(
            otherTenantId,
            "LOT-QC-OTHER-" + suffix(),
            null,
            null,
            Instant.parse("2026-07-19T08:00:00Z"));
    saveUnit(otherTenantId, otherTenant, QualityDisposition.PENDING_INSPECTION);

    TenantContext.setCurrentTenantId(tenantId);
    var firstPage = service.getQueue(PageRequest.of(0, 1));
    var secondPage = service.getQueue(PageRequest.of(1, 1));

    assertThat(firstPage.getTotalElements()).isEqualTo(2);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
    assertThat(firstPage)
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.batchId()).isEqualTo(oldest.getId());
              assertThat(item.productId()).isEqualTo(sharedProduct.getId());
              assertThat(item.productUid()).isEqualTo(sharedProduct.getUid());
              assertThat(item.productDisplayName()).isEqualTo(sharedProductDisplayName);
              assertThat(item.colorId()).isEqualTo(navy.getId());
              assertThat(item.colorName()).isEqualTo("Inspection navy");
              assertThat(item.supplierBatchCode()).isEqualTo("SUP-OLD");
              assertThat(item.pendingUnitCount()).isEqualTo(2);
              assertThat(item.batchCreatedAt()).isEqualTo(Instant.parse("2026-07-20T08:00:00Z"));
            });
    assertThat(secondPage)
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.batchId()).isEqualTo(colourless.getId());
              assertThat(item.colorId()).isNull();
              assertThat(item.colorName()).isNull();
            });
    assertThat(firstPage.getContent())
        .extracting(item -> item.batchId())
        .doesNotContain(otherTenant.getId());
  }

  private Batch saveBatch(
      UUID ownerTenantId,
      String batchCode,
      String supplierBatchCode,
      UUID colorId,
      Instant createdAt) {
    Batch batch =
        Batch.builder()
            .productId(sharedProduct.getId())
            .colorId(colorId)
            .productType(ProductType.FIBER)
            .batchCode(batchCode)
            .supplierBatchCode(supplierBatchCode)
            .quantity(new BigDecimal("100"))
            .reservedQuantity(BigDecimal.ZERO)
            .consumedQuantity(BigDecimal.ZERO)
            .wasteQuantity(BigDecimal.ZERO)
            .unit("KG")
            .status(BatchStatus.PENDING_QC)
            .sourceType(BatchSourceType.INITIAL_STOCK)
            .build();
    batch.setTenantId(ownerTenantId);
    batch.setCreatedAt(createdAt);
    return batchRepository.saveAndFlush(batch);
  }

  private void saveUnit(UUID ownerTenantId, Batch batch, QualityDisposition qualityDisposition) {
    stockUnitRepository.saveAndFlush(
        StockUnit.create(
            ownerTenantId,
            batch.getId(),
            ProductType.FIBER,
            "QC-" + suffix(),
            null,
            PackageType.BALE,
            BigDecimal.TEN,
            null,
            "KG",
            null,
            StockUnitSourceType.GOODS_RECEIPT,
            UUID.randomUUID(),
            qualityDisposition));
  }

  private static String randomCode(String prefix) {
    return prefix + "-" + suffix();
  }

  private static String suffix() {
    return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }
}
