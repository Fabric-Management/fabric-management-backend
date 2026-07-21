package com.fabricmanagement.production.execution.batch.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
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
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.Product;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.infra.repository.ProductRepository;
import com.fabricmanagement.production.masterdata.qualitygrade.domain.QualityGrade;
import com.fabricmanagement.production.masterdata.qualitygrade.infra.repository.QualityGradeRepository;
import com.fabricmanagement.production.quality.decision.app.QualityDecisionCommand;
import com.fabricmanagement.production.quality.decision.app.QualityDecisionService;
import com.fabricmanagement.production.quality.decision.app.TrustedDecisionContext;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOutcome;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionScope;
import com.fabricmanagement.production.quality.decision.infra.repository.QualityDecisionUnitRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class StockAvailabilityQueryIT extends AbstractIntegrationTest {

  @Autowired private StockAvailabilityQueryService service;
  @Autowired private ProductRepository productRepository;
  @Autowired private BatchRepository batchRepository;
  @Autowired private StockUnitRepository stockUnitRepository;
  @Autowired private QualityGradeRepository qualityGradeRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private QualityDecisionService qualityDecisionService;
  @Autowired private QualityDecisionUnitRepository decisionUnitRepository;
  @Autowired private EntityManager entityManager;

  private UUID tenantId;

  @BeforeEach
  void setUpTenant() {
    tenantId = tenant("primary");
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void readsQualityAtPieceGrainAndExcludesQuarantineAndOtherTenants() {
    // Batch headers retain the legacy DB bookkeeping unit; canonical metres come from pieces.
    Product product = product(ProductType.FABRIC, "PIECE", tenantId);
    QualityGrade firstGrade = grade(product.getProductType(), "1ST", 1, true);
    Batch available = batch(product, "LOT-AVAILABLE", BatchStatus.AVAILABLE, tenantId);
    batch(product, "LOT-PENDING", BatchStatus.PENDING_QC, tenantId);

    StockUnit graded = piece(available, "ROLL-GRADED", "10", "100", tenantId);
    graded.changeGrade(firstGrade.getId());
    stockUnitRepository.save(graded);
    stockUnitRepository.save(piece(available, "ROLL-UNASSIGNED", "5", "50", tenantId));
    StockUnit quarantined = piece(available, "ROLL-QUARANTINE", "100", "1000", tenantId);
    quarantined.changeGrade(firstGrade.getId());
    quarantined.quarantine();
    stockUnitRepository.save(quarantined);

    Product chemicalProduct = product(ProductType.CHEMICAL, "PIECE", tenantId);
    batch(chemicalProduct, "LOT-CHEMICAL", BatchStatus.AVAILABLE, tenantId);

    UUID otherTenant = tenant("other");
    Product otherProduct = product(ProductType.FABRIC, "PIECE", otherTenant);
    batch(otherProduct, "LOT-OTHER-TENANT", BatchStatus.AVAILABLE, otherTenant);
    stockUnitRepository.flush();

    var lots = service.lots(null, null, product.getId(), null, null, null, PageRequest.of(0, 20));
    var lot = lots.getContent().getFirst();

    assertThat(lots.getTotalElements()).isEqualTo(1);
    assertThat(lot.lotNo()).isEqualTo("LOT-AVAILABLE");
    assertThat(lot.physical().pieceCount()).isEqualTo(2);
    assertThat(lot.physical().kg()).isEqualByComparingTo("15");
    assertThat(lot.physical().metres()).isEqualByComparingTo("150");
    assertThat(lot.qualityBreakdown()).hasSize(2);
    assertThat(lot.qualityBreakdown().getFirst().grade().id()).isEqualTo(firstGrade.getId());
    assertThat(lot.qualityBreakdown().getLast().grade()).isNull();

    var gradeFiltered =
        service.lots(
            null, null, product.getId(), null, firstGrade.getId(), null, PageRequest.of(0, 20));
    assertThat(gradeFiltered.getContent().getFirst().physical().pieceCount()).isEqualTo(1);
    assertThat(gradeFiltered.getContent().getFirst().physical().metres())
        .isEqualByComparingTo("100");

    var colourlessLots = service.lots(null, true, null, null, null, null, PageRequest.of(0, 20));
    assertThat(colourlessLots.getContent())
        .extracting(lotRow -> lotRow.lotNo())
        .containsExactly("LOT-AVAILABLE");
    assertThat(
            service.lots(
                null, null, chemicalProduct.getId(), null, null, null, PageRequest.of(0, 20)))
        .isEmpty();

    assertThat(
            service.lots(null, null, otherProduct.getId(), null, null, null, PageRequest.of(0, 20)))
        .isEmpty();
  }

  @Test
  void pendingReceiptStockBecomesVisibleOnlyAfterImmutableFullLotRelease() {
    Product product = product(ProductType.FABRIC, "M", tenantId);
    Batch pending = batch(product, "LOT-RECEIPT-QC", BatchStatus.PENDING_QC, tenantId);
    UUID actorId = UUID.randomUUID();
    StockUnit received =
        StockUnit.create(
            tenantId,
            pending.getId(),
            ProductType.FABRIC,
            "GR-ROLL-" + UUID.randomUUID().toString().substring(0, 8),
            null,
            PackageType.ROLL,
            new BigDecimal("10"),
            null,
            "KG",
            null,
            StockUnitSourceType.GOODS_RECEIPT,
            UUID.randomUUID(),
            QualityDisposition.PENDING_INSPECTION);
    received.recordLength(new BigDecimal("100"), "M");
    stockUnitRepository.saveAndFlush(received);

    assertThat(service.lots(null, null, product.getId(), null, null, null, PageRequest.of(0, 20)))
        .isEmpty();

    var decision =
        qualityDecisionService.recordDecision(
            TrustedDecisionContext.manual(actorId),
            new QualityDecisionCommand(
                pending.getId(),
                QualityDecisionScope.FULL_LOT,
                QualityDecisionOutcome.RELEASED,
                null,
                "Goods receipt inspection passed",
                java.util.Set.of(),
                null));
    entityManager.flush();
    entityManager.clear();

    StockUnit released = stockUnitRepository.findById(received.getId()).orElseThrow();
    Batch projected = batchRepository.findById(pending.getId()).orElseThrow();
    assertThat(released.getQualityDisposition()).isEqualTo(QualityDisposition.RELEASED);
    assertThat(projected.getStatus()).isEqualTo(BatchStatus.AVAILABLE);
    assertThat(decision.getActorId()).isEqualTo(actorId);
    assertThat(decisionUnitRepository.countByTenantIdAndDecisionId(tenantId, decision.getId()))
        .isEqualTo(1);

    var visible =
        service.lots(null, null, product.getId(), null, null, null, PageRequest.of(0, 20));
    assertThat(visible).hasSize(1);
    assertThat(visible.getContent().getFirst().lotNo()).isEqualTo("LOT-RECEIPT-QC");
  }

  private Product product(ProductType type, String unit, UUID ownerTenantId) {
    Product product = Product.create(type, unit);
    product.setTenantId(ownerTenantId);
    return productRepository.saveAndFlush(product);
  }

  private UUID tenant(String label) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Tenant tenant = Tenant.create("Stock Availability " + label + " " + suffix, "STK-" + suffix);
    tenant.activate("test");
    return tenantRepository.saveAndFlush(tenant).getId();
  }

  private QualityGrade grade(ProductType type, String code, int rank, boolean saleable) {
    return qualityGradeRepository.saveAndFlush(
        QualityGrade.create(
            tenantId,
            type,
            code,
            "Grade " + code,
            rank,
            BigDecimal.ONE,
            saleable,
            false,
            null,
            false));
  }

  private Batch batch(Product product, String code, BatchStatus status, UUID ownerTenantId) {
    Batch batch =
        Batch.builder()
            .productId(product.getId())
            .productType(product.getProductType())
            .batchCode(code)
            .quantity(new BigDecimal("200"))
            .reservedQuantity(BigDecimal.ZERO)
            .consumedQuantity(BigDecimal.ZERO)
            .wasteQuantity(BigDecimal.ZERO)
            .unit(product.getUnit())
            .status(status)
            .sourceType(BatchSourceType.INITIAL_STOCK)
            .build();
    batch.setTenantId(ownerTenantId);
    batch.setIsActive(true);
    return batchRepository.saveAndFlush(batch);
  }

  private StockUnit piece(
      Batch batch, String barcode, String kg, String metres, UUID ownerTenantId) {
    StockUnit piece =
        StockUnit.create(
            ownerTenantId,
            batch.getId(),
            ProductType.FABRIC,
            barcode + "-" + UUID.randomUUID().toString().substring(0, 8),
            null,
            PackageType.ROLL,
            new BigDecimal(kg),
            null,
            "KG",
            null,
            StockUnitSourceType.PRODUCTION,
            UUID.randomUUID(),
            QualityDisposition.RELEASED);
    piece.recordLength(new BigDecimal(metres), "M");
    return piece;
  }
}
