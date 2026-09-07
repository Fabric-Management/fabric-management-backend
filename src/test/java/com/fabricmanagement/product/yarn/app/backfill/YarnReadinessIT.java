package com.fabricmanagement.product.yarn.app.backfill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.product.yarn.app.port.YarnUsageDiscovery;
import com.fabricmanagement.product.yarn.app.port.YarnUsageSignal;
import com.fabricmanagement.product.yarn.app.port.YarnUsageSignalSource;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleStatus;
import com.fabricmanagement.product.yarn.domain.backfill.YarnBackfillQueueStatus;
import com.fabricmanagement.product.yarn.infra.repository.YarnArticleRepository;
import com.fabricmanagement.product.yarn.infra.repository.YarnBackfillReconciliationRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

@ExtendWith(MockitoExtension.class)
class YarnReadinessIT extends AbstractIntegrationTest {

  @Mock private ProductRepository productRepository;
  @Mock private YarnArticleRepository articleRepository;
  @Mock private YarnBackfillReconciliationRepository reconciliationRepository;
  @Autowired private YarnReadinessService actualReadinessService;
  @Autowired private SystemTransactionExecutor systemTransactions;
  @Autowired private EntityManagerFactory entityManagerFactory;

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void unionsSignalsTypesInBulkReportsUnlinkedDocumentsAndCapsOnlyTheDetails() {
    UUID tenantId = UUID.randomUUID();
    UUID lowerId = UUID.fromString("00000000-0000-4000-8000-000000000001");
    UUID higherId = UUID.fromString("00000000-0000-4000-8000-000000000002");
    UUID nonYarnId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
    Product lower = product(lowerId, "SAME-UID");
    Product higher = product(higherId, "SAME-UID");
    YarnUsageSignalSource purchaseOrders =
        ignored ->
            new YarnUsageDiscovery(
                Map.of(YarnUsageSignal.OPEN_PURCHASE_ORDER, Set.of(lowerId, nonYarnId)),
                Map.of(YarnUsageSignal.OPEN_PURCHASE_ORDER, 2L));
    YarnUsageSignalSource production =
        ignored ->
            new YarnUsageDiscovery(
                Map.of(
                    YarnUsageSignal.OPEN_WORK_ORDER,
                    Set.of(higherId),
                    YarnUsageSignal.RECENT_BATCH_MOVEMENT,
                    Set.of(lowerId)),
                Map.of(YarnUsageSignal.OPEN_WORK_ORDER, 3L));
    when(productRepository.findByTenantIdAndIdInAndProductType(
            eq(tenantId), any(), eq(ProductType.YARN)))
        .thenReturn(List.of(higher, lower));
    YarnArticle draft = mock(YarnArticle.class);
    when(draft.getProductId()).thenReturn(higherId);
    when(draft.getId()).thenReturn(UUID.randomUUID());
    when(draft.getStatus()).thenReturn(YarnArticleStatus.DRAFT);
    when(articleRepository.findByTenantIdAndProduct_IdIn(eq(tenantId), any()))
        .thenReturn(List.of(draft));
    when(productRepository.countByTenantIdAndProductTypeAndIsActiveTrue(tenantId, ProductType.YARN))
        .thenReturn(7L);
    when(articleRepository.countByTenantIdAndStatus(tenantId, YarnArticleStatus.ACTIVE))
        .thenReturn(5L);
    when(reconciliationRepository.countByTenantIdAndStatus(tenantId, YarnBackfillQueueStatus.OPEN))
        .thenReturn(4L);

    var report =
        new YarnReadinessService(
                List.of(purchaseOrders, production),
                productRepository,
                articleRepository,
                reconciliationRepository)
            .readiness(1);

    assertThat(report.ready()).isFalse();
    assertThat(report.activelyUsedCount()).isEqualTo(2);
    assertThat(report.blockerCount()).isEqualTo(2);
    assertThat(report.blockers()).hasSize(1);
    assertThat(report.blockers().getFirst().productId()).isEqualTo(lowerId);
    assertThat(report.blockers().getFirst().articleStatus()).isNull();
    assertThat(report.blockers().getFirst().signals())
        .containsExactly(
            YarnUsageSignal.OPEN_PURCHASE_ORDER, YarnUsageSignal.RECENT_BATCH_MOVEMENT);
    assertThat(report.unlinkedOpenYarnDocuments().openPurchaseOrders()).isEqualTo(2);
    assertThat(report.unlinkedOpenYarnDocuments().openWorkOrders()).isEqualTo(3);
    assertThat(report.openReconciliationCount()).isEqualTo(4);
    assertThat(report.activeYarnProductCount()).isEqualTo(7);
    assertThat(report.activeArticleCount()).isEqualTo(5);
    assertThat(report.movementWindowDays()).isEqualTo(90);
    verify(productRepository)
        .findByTenantIdAndIdInAndProductType(eq(tenantId), any(), eq(ProductType.YARN));
  }

  @Test
  void eachUsageSignalAloneMakesAMissingArticleBlockReadiness() {
    for (YarnUsageSignal signal : YarnUsageSignal.values()) {
      UUID tenantId = UUID.randomUUID();
      UUID productId = UUID.randomUUID();
      ProductRepository products = mock(ProductRepository.class);
      YarnArticleRepository articles = mock(YarnArticleRepository.class);
      YarnBackfillReconciliationRepository reconciliations =
          mock(YarnBackfillReconciliationRepository.class);
      YarnUsageSignalSource source =
          ignored -> new YarnUsageDiscovery(Map.of(signal, Set.of(productId)), Map.of());
      Product referencedProduct = product(productId, "ONLY-" + signal.name());
      when(products.findByTenantIdAndIdInAndProductType(eq(tenantId), any(), eq(ProductType.YARN)))
          .thenReturn(List.of(referencedProduct));
      when(articles.findByTenantIdAndProduct_IdIn(eq(tenantId), any())).thenReturn(List.of());
      TenantContext.setCurrentTenantId(tenantId);

      var report =
          new YarnReadinessService(List.of(source), products, articles, reconciliations)
              .readiness(50);

      assertThat(report.ready()).as(signal.name()).isFalse();
      assertThat(report.activelyUsedCount()).as(signal.name()).isEqualTo(1);
      assertThat(report.blockerCount()).as(signal.name()).isEqualTo(1);
      assertThat(report.blockers().getFirst().articleStatus()).as(signal.name()).isNull();
      assertThat(report.blockers().getFirst().signals()).containsExactly(signal);
    }
  }

  @Test
  void realDatabaseLegsApplyEveryInclusionExclusionAndKeepStatementCountConstant() {
    UUID tenantId = UUID.randomUUID();
    UUID poProduct = UUID.randomUUID();
    UUID woProduct = UUID.randomUUID();
    UUID movementProduct = UUID.randomUUID();
    UUID excludedProduct = UUID.randomUUID();
    UUID nonYarnProduct = UUID.randomUUID();
    seedReadinessTenant(
        tenantId, poProduct, woProduct, movementProduct, excludedProduct, nonYarnProduct);
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentTenantUid("YARN-READINESS-IT");
    var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);

    statistics.clear();
    var report = actualReadinessService.readiness(50);
    long baseStatements = statistics.getPrepareStatementCount();
    insertAdditionalOpenWorkOrders(tenantId, woProduct, 20);
    statistics.clear();
    var repeated = actualReadinessService.readiness(50);
    long manyRowStatements = statistics.getPrepareStatementCount();

    assertThat(report.activelyUsedCount()).isEqualTo(3);
    assertThat(report.blockerCount()).isEqualTo(2);
    assertThat(report.ready()).isFalse();
    assertThat(report.blockers()).extracting("productId").containsExactly(poProduct, woProduct);
    assertThat(report.blockers().get(0).signals())
        .containsExactly(YarnUsageSignal.OPEN_PURCHASE_ORDER);
    assertThat(report.blockers().get(1).signals()).containsExactly(YarnUsageSignal.OPEN_WORK_ORDER);
    assertThat(report.unlinkedOpenYarnDocuments().openPurchaseOrders()).isEqualTo(1);
    assertThat(report.unlinkedOpenYarnDocuments().openWorkOrders()).isEqualTo(1);
    assertThat(report.activeYarnProductCount()).isEqualTo(3);
    assertThat(report.activeArticleCount()).isEqualTo(1);
    assertThat(repeated.activelyUsedCount()).isEqualTo(3);
    assertThat(manyRowStatements).isEqualTo(baseStatements);
  }

  private void seedReadinessTenant(
      UUID tenantId,
      UUID poProduct,
      UUID woProduct,
      UUID movementProduct,
      UUID excludedProduct,
      UUID nonYarnProduct) {
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status) "
                  + "VALUES (?, ?, ?, ?, 'ACTIVE')",
              tenantId,
              "YRD-" + tenantId,
              "yarn-readiness-" + tenantId,
              "Yarn Readiness");
          insertProduct(jdbc, tenantId, poProduct, "YARN", true, "A-PO");
          insertProduct(jdbc, tenantId, woProduct, "YARN", false, "B-WO");
          insertProduct(jdbc, tenantId, movementProduct, "YARN", true, "C-MOVE");
          insertProduct(jdbc, tenantId, excludedProduct, "YARN", true, "D-EXCLUDED");
          insertProduct(jdbc, tenantId, nonYarnProduct, "FABRIC", true, "E-NON-YARN");
          insertArticle(jdbc, tenantId, woProduct, "DRAFT");
          insertArticle(jdbc, tenantId, movementProduct, "ACTIVE");

          insertWorkOrder(jdbc, tenantId, woProduct, "DRAFT", true, "SPINNING");
          insertWorkOrder(jdbc, tenantId, excludedProduct, "COMPLETED", true, "SPINNING");
          insertWorkOrder(jdbc, tenantId, excludedProduct, "CANCELLED", true, "SPINNING");
          insertWorkOrder(jdbc, tenantId, excludedProduct, "DRAFT", false, "SPINNING");
          insertWorkOrder(jdbc, tenantId, nonYarnProduct, "DRAFT", true, "SPINNING");
          insertWorkOrder(jdbc, tenantId, null, "DRAFT", true, "SPINNING");

          UUID openPo = insertPurchaseOrder(jdbc, tenantId, "DRAFT", true);
          insertPurchaseLine(jdbc, tenantId, openPo, poProduct, "GENERIC");
          insertPurchaseLine(jdbc, tenantId, openPo, null, "YARN");
          insertPurchaseLine(jdbc, tenantId, openPo, null, "YARN");
          UUID closedPo = insertPurchaseOrder(jdbc, tenantId, "CLOSED", true);
          insertPurchaseLine(jdbc, tenantId, closedPo, excludedProduct, "GENERIC");
          UUID cancelledPo = insertPurchaseOrder(jdbc, tenantId, "CANCELLED", true);
          insertPurchaseLine(jdbc, tenantId, cancelledPo, excludedProduct, "GENERIC");

          UUID movedBatch = insertBatch(jdbc, tenantId, movementProduct);
          jdbc.update(
              "UPDATE production.production_execution_batch "
                  + "SET created_at=NOW() - INTERVAL '1 year', updated_at=NOW() - INTERVAL '1 year' "
                  + "WHERE id=?",
              movedBatch);
          insertTransaction(
              jdbc, tenantId, movedBatch, "RECEIPT", java.time.Instant.now().toString());
          UUID staleBatch = insertBatch(jdbc, tenantId, excludedProduct);
          insertTransaction(
              jdbc,
              tenantId,
              staleBatch,
              "RECEIPT",
              java.time.Instant.now().minus(100, java.time.temporal.ChronoUnit.DAYS).toString());
          insertTransaction(
              jdbc, tenantId, staleBatch, "RESERVATION", java.time.Instant.now().toString());
          return null;
        });
  }

  private void insertAdditionalOpenWorkOrders(UUID tenantId, UUID productId, int count) {
    systemTransactions.executeInTransaction(
        jdbc -> {
          for (int index = 0; index < count; index++) {
            insertWorkOrder(jdbc, tenantId, productId, "DRAFT", true, "SPINNING");
          }
          return null;
        });
  }

  private void insertProduct(
      org.springframework.jdbc.core.JdbcTemplate jdbc,
      UUID tenantId,
      UUID productId,
      String type,
      boolean active,
      String uidPrefix) {
    jdbc.update(
        "INSERT INTO production.prod_product "
            + "(id, tenant_id, uid, product_type, unit, is_active) VALUES (?, ?, ?, ?, 'KG', ?)",
        productId,
        tenantId,
        uidPrefix + "-" + productId,
        type,
        active);
  }

  private void insertArticle(
      org.springframework.jdbc.core.JdbcTemplate jdbc,
      UUID tenantId,
      UUID productId,
      String status) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO production.prod_yarn_article "
            + "(id, tenant_id, uid, product_id, status, name, is_active) "
            + "VALUES (?, ?, ?, ?, ?, ?, TRUE)",
        id,
        tenantId,
        "YRD-ARTICLE-" + id,
        productId,
        status,
        "Article " + productId);
  }

  private void insertWorkOrder(
      org.springframework.jdbc.core.JdbcTemplate jdbc,
      UUID tenantId,
      UUID productId,
      String status,
      boolean active,
      String specType) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO production.prod_work_order "
            + "(id, tenant_id, uid, created_at, updated_at, work_order_number, output_product_id, "
            + "module_type, production_specs, fulfillment_type, planned_qty, unit, status, is_active) "
            + "VALUES (?, ?, ?, NOW(), NOW(), ?, ?, 'SPINNING', "
            + "jsonb_build_object('specType', ?), 'INTERNAL', 1, 'KG', ?, ?)",
        id,
        tenantId,
        "YRD-WO-" + id,
        "YRD-WO-NO-" + id,
        productId,
        specType,
        status,
        active);
  }

  private UUID insertPurchaseOrder(
      org.springframework.jdbc.core.JdbcTemplate jdbc,
      UUID tenantId,
      String status,
      boolean active) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO procurement.purchase_order "
            + "(id, tenant_id, uid, created_at, updated_at, po_number, work_order_id, "
            + "trading_partner_id, status, currency, total_amount, is_active) "
            + "VALUES (?, ?, ?, NOW(), NOW(), ?, ?, ?, ?, 'GBP', 1, ?)",
        id,
        tenantId,
        "YRD-PO-" + id,
        "YRD-PO-NO-" + id,
        UUID.randomUUID(),
        UUID.randomUUID(),
        status,
        active);
    return id;
  }

  private void insertPurchaseLine(
      org.springframework.jdbc.core.JdbcTemplate jdbc,
      UUID tenantId,
      UUID purchaseOrderId,
      UUID productId,
      String specType) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO procurement.purchase_order_line "
            + "(id, tenant_id, uid, created_at, updated_at, purchase_order_id, product_id, "
            + "product_desc, qty, unit, unit_price, currency, total_price, module_specs, is_active) "
            + "VALUES (?, ?, ?, NOW(), NOW(), ?, ?, 'Yarn line', 1, 'KG', 1, 'GBP', 1, "
            + "jsonb_build_object('specType', ?), TRUE)",
        id,
        tenantId,
        "YRD-POL-" + id,
        purchaseOrderId,
        productId,
        specType);
  }

  private UUID insertBatch(
      org.springframework.jdbc.core.JdbcTemplate jdbc, UUID tenantId, UUID productId) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO production.production_execution_batch "
            + "(id, tenant_id, uid, created_at, updated_at, product_id, product_type, batch_code, "
            + "quantity, reserved_quantity, consumed_quantity, waste_quantity, unit, status, is_active) "
            + "VALUES (?, ?, ?, NOW(), NOW(), ?, 'YARN', ?, 1, 0, 0, 0, 'KG', 'AVAILABLE', TRUE)",
        id,
        tenantId,
        "YRD-BATCH-" + id,
        productId,
        "YRD-BATCH-NO-" + id);
    return id;
  }

  private void insertTransaction(
      org.springframework.jdbc.core.JdbcTemplate jdbc,
      UUID tenantId,
      UUID batchId,
      String type,
      String date) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO production.production_execution_inventory_transaction "
            + "(id, tenant_id, uid, created_at, updated_at, batch_id, transaction_type, quantity, "
            + "unit, transaction_date, is_active) VALUES (?, ?, ?, NOW(), NOW(), ?, ?, 1, 'KG', "
            + "CAST(? AS timestamptz), TRUE)",
        id,
        tenantId,
        "YRD-TXN-" + id,
        batchId,
        type,
        date);
  }

  private Product product(UUID id, String uid) {
    Product product = mock(Product.class);
    when(product.getId()).thenReturn(id);
    when(product.getUid()).thenReturn(uid);
    return product;
  }
}
