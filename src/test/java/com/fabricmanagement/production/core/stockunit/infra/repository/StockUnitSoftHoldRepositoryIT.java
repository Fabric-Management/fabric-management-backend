package com.fabricmanagement.production.core.stockunit.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchSourceType;
import com.fabricmanagement.production.core.batch.domain.BatchStatus;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.stockunit.domain.PackageType;
import com.fabricmanagement.production.core.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.core.stockunit.domain.StockUnit;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitSoftHold;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitSoftHoldRepository.HeldPieceRow;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * STOCK-PREVIEW-1 A2 against a real database: the two-entity query behind piece holds returns the
 * projected stock unit, lot and quote line of active holds on active pieces of the requested lots,
 * and nothing of another tenant. The test database user bypasses row-level security, so tenant
 * isolation here is the query's own tenant predicates. Data is fictional.
 */
@Transactional
class StockUnitSoftHoldRepositoryIT extends AbstractIntegrationTest {

  @Autowired private StockUnitSoftHoldRepository repository;
  @Autowired private StockUnitRepository stockUnitRepository;
  @Autowired private BatchRepository batchRepository;
  @Autowired private ProductRepository productRepository;
  @Autowired private TenantRepository tenantRepository;

  private UUID tenantA;

  @BeforeEach
  void setUpTenant() {
    tenantA = tenant("a");
    useTenant(tenantA);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void returnsActiveHoldsOnActivePiecesOfTheRequestedLotsWithAllThreeIds() {
    Product product = product(tenantA);
    Batch lotA = batch(product, "LOT-A", tenantA);
    Batch lotB = batch(product, "LOT-B", tenantA);
    Batch notRequested = batch(product, "LOT-OTHER", tenantA);
    StockUnit a1 = piece(lotA, "ROLL-A1", tenantA, true);
    StockUnit a2 = piece(lotA, "ROLL-A2", tenantA, true);
    StockUnit inactivePiece = piece(lotA, "ROLL-A3", tenantA, false);
    StockUnit b1 = piece(lotB, "ROLL-B1", tenantA, true);
    StockUnit other = piece(notRequested, "ROLL-O1", tenantA, true);
    UUID lineX = UUID.randomUUID();
    UUID lineY = UUID.randomUUID();

    hold(tenantA, lineX, a1);
    hold(tenantA, lineY, a1); // one piece, two quote lines: both holds are returned
    hold(tenantA, lineX, b1);
    StockUnitSoftHold released = StockUnitSoftHold.place(tenantA, lineX, a2.getId());
    released.release(Instant.parse("2026-10-06T12:00:00Z"));
    repository.save(released);
    StockUnitSoftHold deactivated = StockUnitSoftHold.place(tenantA, lineY, a2.getId());
    deactivated.setIsActive(false);
    repository.save(deactivated);
    hold(tenantA, lineX, inactivePiece);
    hold(tenantA, lineX, other);
    repository.flush();

    List<HeldPieceRow> rows =
        repository.findActiveByBatchIds(tenantA, List.of(lotA.getId(), lotB.getId()));

    assertThat(rows)
        .extracting(
            HeldPieceRow::getStockUnitId, HeldPieceRow::getBatchId, HeldPieceRow::getQuoteLineId)
        .containsExactlyInAnyOrder(
            tuple(a1.getId(), lotA.getId(), lineX),
            tuple(a1.getId(), lotA.getId(), lineY),
            tuple(b1.getId(), lotB.getId(), lineX));
  }

  @Test
  void noLotsReturnNothing() {
    assertThat(repository.findActiveByBatchIds(tenantA, List.of())).isEmpty();
    assertThat(repository.findActiveByBatchIds(tenantA, null)).isEmpty();
  }

  @Test
  void anotherTenantsHoldsAreNeverReturned() {
    Product productA = product(tenantA);
    Batch lotA = batch(productA, "LOT-A", tenantA);
    StockUnit pieceA = piece(lotA, "ROLL-A1", tenantA, true);
    UUID lineA = UUID.randomUUID();
    hold(tenantA, lineA, pieceA);

    UUID tenantB = tenant("b");
    useTenant(tenantB);
    Product productB = product(tenantB);
    Batch lotB = batch(productB, "LOT-B", tenantB);
    StockUnit pieceB = piece(lotB, "ROLL-B1", tenantB, true);
    UUID lineB = UUID.randomUUID();
    hold(tenantB, lineB, pieceB);
    repository.flush();

    // Tenant B asking for tenant A's lot, and for both lots, sees only its own hold.
    assertThat(repository.findActiveByBatchIds(tenantB, List.of(lotA.getId()))).isEmpty();
    assertThat(repository.findActiveByBatchIds(tenantB, List.of(lotA.getId(), lotB.getId())))
        .extracting(
            HeldPieceRow::getStockUnitId, HeldPieceRow::getBatchId, HeldPieceRow::getQuoteLineId)
        .containsExactly(tuple(pieceB.getId(), lotB.getId(), lineB));

    useTenant(tenantA);
    assertThat(repository.findActiveByBatchIds(tenantA, List.of(lotB.getId()))).isEmpty();
    assertThat(repository.findActiveByBatchIds(tenantA, List.of(lotA.getId(), lotB.getId())))
        .extracting(
            HeldPieceRow::getStockUnitId, HeldPieceRow::getBatchId, HeldPieceRow::getQuoteLineId)
        .containsExactly(tuple(pieceA.getId(), lotA.getId(), lineA));
  }

  private void useTenant(UUID tenantId) {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(UUID.randomUUID());
  }

  private void hold(UUID tenantId, UUID quoteLineId, StockUnit piece) {
    repository.save(StockUnitSoftHold.place(tenantId, quoteLineId, piece.getId()));
  }

  private UUID tenant(String label) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Tenant tenant = Tenant.create("Soft Hold " + label + " " + suffix, "SH-" + suffix);
    tenant.activate("test");
    return tenantRepository.saveAndFlush(tenant).getId();
  }

  private Product product(UUID ownerTenantId) {
    Product product = Product.create(ProductType.FABRIC, "M");
    product.setTenantId(ownerTenantId);
    return productRepository.saveAndFlush(product);
  }

  private Batch batch(Product product, String code, UUID ownerTenantId) {
    Batch batch =
        Batch.builder()
            .productId(product.getId())
            .productType(product.getProductType())
            .batchCode(code + "-" + UUID.randomUUID().toString().substring(0, 8))
            .quantity(new BigDecimal("300"))
            .reservedQuantity(BigDecimal.ZERO)
            .consumedQuantity(BigDecimal.ZERO)
            .wasteQuantity(BigDecimal.ZERO)
            .unit(product.getUnit())
            .status(BatchStatus.AVAILABLE)
            .sourceType(BatchSourceType.INITIAL_STOCK)
            .build();
    batch.setTenantId(ownerTenantId);
    batch.setIsActive(true);
    return batchRepository.saveAndFlush(batch);
  }

  private StockUnit piece(Batch batch, String barcode, UUID ownerTenantId, boolean active) {
    StockUnit piece =
        StockUnit.create(
            ownerTenantId,
            batch.getId(),
            ProductType.FABRIC,
            barcode + "-" + UUID.randomUUID().toString().substring(0, 8),
            null,
            PackageType.ROLL,
            new BigDecimal("10"),
            null,
            "KG",
            null,
            StockUnitSourceType.PRODUCTION,
            UUID.randomUUID(),
            QualityDisposition.RELEASED);
    piece.recordLength(new BigDecimal("100"), "M");
    piece.setIsActive(active);
    return stockUnitRepository.saveAndFlush(piece);
  }
}
