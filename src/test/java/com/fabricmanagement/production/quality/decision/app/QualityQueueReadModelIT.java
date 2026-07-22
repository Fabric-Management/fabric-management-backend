package com.fabricmanagement.production.quality.decision.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.color.domain.Color;
import com.fabricmanagement.production.masterdata.color.infra.repository.ColorRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.production.masterdata.product.domain.Product;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.infra.repository.ProductRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

class QualityQueueReadModelIT extends AbstractIntegrationTest {

  @Autowired private QualityDecisionQueryService service;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private FiberRepository fiberRepository;
  @Autowired private ColorRepository colorRepository;
  @Autowired private BatchRepository batchRepository;
  @Autowired private StockUnitRepository stockUnitRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID tenantId;
  private UUID otherTenantId;
  private Product sharedProduct;
  private String sharedProductDisplayName;

  @BeforeEach
  void setUp() {
    tenantId = saveTenant("primary");
    otherTenantId = saveTenant("other");
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

  @Test
  void returnsTenantSafeSummaryWithTheCompleteActiveDispositionBreakdown() {
    Color navy =
        colorRepository.saveAndFlush(
            Color.create(tenantId, randomCode("DETAIL"), "QC detail navy", "#1F2A44"));
    Batch batch =
        saveBatch(
            tenantId,
            "LOT-QC-DETAIL-" + suffix(),
            null,
            navy.getId(),
            Instant.parse("2026-07-21T09:00:00Z"));
    saveUnit(tenantId, batch, QualityDisposition.PENDING_INSPECTION);
    saveUnit(tenantId, batch, QualityDisposition.PENDING_INSPECTION);
    saveUnit(tenantId, batch, QualityDisposition.RELEASED);
    saveUnit(tenantId, batch, QualityDisposition.QUARANTINED);
    saveUnit(tenantId, batch, QualityDisposition.NONCONFORMING);
    StockUnit inactive = saveUnit(tenantId, batch, QualityDisposition.NONCONFORMING);
    inactive.delete();
    stockUnitRepository.saveAndFlush(inactive);

    var summary = service.getSummary(batch.getId());

    assertThat(summary.batchCode()).isEqualTo(batch.getBatchCode());
    assertThat(summary.productId()).isEqualTo(sharedProduct.getId());
    assertThat(summary.productUid()).isEqualTo(sharedProduct.getUid());
    assertThat(summary.productDisplayName()).isEqualTo(sharedProductDisplayName);
    assertThat(summary.colorId()).isEqualTo(navy.getId());
    assertThat(summary.colorName()).isEqualTo("QC detail navy");
    assertThat(summary.pendingInspectionCount()).isEqualTo(2);
    assertThat(summary.releasedCount()).isEqualTo(1);
    assertThat(summary.quarantinedCount()).isEqualTo(1);
    assertThat(summary.nonconformingCount()).isEqualTo(1);
    assertThat(summary.totalCount()).isEqualTo(5);

    Batch colourless =
        saveBatch(
            tenantId,
            "LOT-QC-COLOURLESS-" + suffix(),
            null,
            null,
            Instant.parse("2026-07-21T09:30:00Z"));
    var colourlessSummary = service.getSummary(colourless.getId());
    assertThat(colourlessSummary.colorId()).isNull();
    assertThat(colourlessSummary.colorName()).isNull();
    assertThat(colourlessSummary.totalCount()).isZero();

    TenantContext.setCurrentTenantId(otherTenantId);
    assertThatThrownBy(() -> service.getSummary(batch.getId()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void activeUnitsReadIncludesOperationallyIneligibleStatusesAndExcludesInactiveUnits() {
    Batch batch =
        saveBatch(
            tenantId,
            "LOT-QC-UNITS-" + suffix(),
            null,
            null,
            Instant.parse("2026-07-21T10:00:00Z"));
    for (StockUnitStatus status : StockUnitStatus.values()) {
      saveUnitWithStatus(tenantId, batch, status);
    }
    StockUnit inactive = saveUnitWithStatus(tenantId, batch, StockUnitStatus.AVAILABLE);
    String inactiveBarcode = inactive.getBarcode();
    inactive.delete();
    stockUnitRepository.saveAndFlush(inactive);

    var units =
        service.getActiveUnits(
            batch.getId(), PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "barcode")));

    assertThat(units).hasSize(StockUnitStatus.values().length);
    assertThat(units.getContent())
        .extracting(StockUnit::getStatus)
        .containsExactlyInAnyOrder(StockUnitStatus.values());
    assertThat(units.getContent())
        .extracting(StockUnit::getBarcode)
        .doesNotContain(inactiveBarcode);
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
    Batch saved = batchRepository.saveAndFlush(batch);
    jdbcTemplate.update(
        "UPDATE production.production_execution_batch SET created_at = ? WHERE id = ?",
        Timestamp.from(createdAt),
        saved.getId());
    return saved;
  }

  private StockUnit saveUnit(
      UUID ownerTenantId, Batch batch, QualityDisposition qualityDisposition) {
    return stockUnitRepository.saveAndFlush(newUnit(ownerTenantId, batch, qualityDisposition));
  }

  private StockUnit saveUnitWithStatus(UUID ownerTenantId, Batch batch, StockUnitStatus status) {
    StockUnit unit = newUnit(ownerTenantId, batch, QualityDisposition.RELEASED);
    switch (status) {
      case AVAILABLE -> {
        // Factory default.
      }
      case PARTIAL -> unit.consume(BigDecimal.ONE);
      case RESERVED -> unit.reserve();
      case IN_TRANSIT -> unit.startTransfer(UUID.randomUUID());
      case ON_HOLD -> unit.hold();
      case QUARANTINE -> unit.quarantine();
      case DEPLETED -> unit.consume(BigDecimal.TEN);
      case DISPOSED -> {
        unit.hold();
        unit.dispose();
      }
    }
    return stockUnitRepository.saveAndFlush(unit);
  }

  private StockUnit newUnit(
      UUID ownerTenantId, Batch batch, QualityDisposition qualityDisposition) {
    return StockUnit.create(
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
        qualityDisposition);
  }

  private UUID saveTenant(String label) {
    String suffix = suffix();
    Tenant tenant = Tenant.create("Quality read model " + label + " " + suffix, "QRM-" + suffix);
    tenant.activate("test");
    return tenantRepository.saveAndFlush(tenant).getId();
  }

  private static String randomCode(String prefix) {
    return prefix + "-" + suffix();
  }

  private static String suffix() {
    return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  }
}
