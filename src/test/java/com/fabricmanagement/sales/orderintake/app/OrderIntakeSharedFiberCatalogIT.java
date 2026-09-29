package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.common.exception.ForbiddenOperationException;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.product.core.app.ProductService;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeProductOption;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Exercises real seed data, repositories and product definitions, without a mocked product facade.
 */
class OrderIntakeSharedFiberCatalogIT extends AbstractIntegrationTest {

  @Autowired private OrderIntakeProductSearchService search;
  @Autowired private ProductSalesDefinitionQueryService definitions;
  @Autowired private ProductService products;
  @Autowired private SystemTransactionExecutor systemTransactions;

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  void bothTenantsCanSelectSharedFibersButCannotSeeEachOthersPrivateProductsOrEditSeeds() {
    UUID tenantA = insertTenant();
    UUID tenantB = insertTenant();
    UUID privateA = insertPrivateProduct(tenantA);
    UUID privateB = insertPrivateProduct(tenantB);
    List<UUID> seedIds =
        systemTransactions.executeQuery(
            "SELECT id FROM production.prod_product "
                + "WHERE tenant_id = ? AND product_type = 'FIBER' AND is_active = TRUE "
                + "AND uid LIKE 'SYS-MAT-%'",
            (rs, row) -> rs.getObject("id", UUID.class), TenantContext.TEMPLATE_TENANT_ID);
    assertThat(seedIds).hasSizeGreaterThan(50);

    assertCatalog(tenantA, privateA, privateB, seedIds);
    assertCatalog(tenantB, privateB, privateA, seedIds);
  }

  private void assertCatalog(
      UUID tenantId, UUID ownProductId, UUID otherProductId, List<UUID> seedIds) {
    TenantContext.setCurrentTenantId(tenantId);
    List<OrderIntakeProductOption> options = search.search(null, ProductType.FIBER, null);
    assertThat(options)
        .extracting(OrderIntakeProductOption::productId)
        .containsAll(seedIds)
        .contains(ownProductId)
        .doesNotContain(otherProductId);
    assertThat(options)
        .filteredOn(option -> seedIds.contains(option.productId()))
        .allSatisfy(
            option -> {
              assertThat(option.baseUnit()).isEqualTo("KG");
              assertThat(option.allowedUnits()).contains("KG");
              assertThat(option.displayName()).isNotBlank().isNotEqualTo(option.uid());
              assertThat(option.customerSpecific()).isFalse();
            });

    UUID sharedId = seedIds.getFirst();
    assertThat(definitions.find(tenantId, sharedId)).isPresent();
    assertThat(definitions.find(tenantId, otherProductId)).isEmpty();
    assertThatThrownBy(() -> products.deactivateProduct(sharedId))
        .isInstanceOf(ForbiddenOperationException.class);
    assertThat(definitions.find(tenantId, sharedId))
        .hasValueSatisfying(definition -> assertThat(definition.active()).isTrue());
  }

  private UUID insertTenant() {
    UUID id = UUID.randomUUID();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status) "
                  + "VALUES (?, ?, ?, ?, 'ACTIVE')",
              id,
              "SOIF-" + id,
              "soi-fiber-" + id,
              "Shared fiber catalogue test");
          return null;
        });
    return id;
  }

  private UUID insertPrivateProduct(UUID tenantId) {
    UUID id = UUID.randomUUID();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO production.prod_product "
                  + "(id, tenant_id, uid, product_type, unit, is_active) "
                  + "VALUES (?, ?, ?, 'FIBER', 'KG', TRUE)",
              id,
              tenantId,
              "SOIF-" + id);
          return null;
        });
    return id;
  }
}
