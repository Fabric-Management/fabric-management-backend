package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.production.masterdata.product.app.ProductService;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.dto.CreateProductRequest;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductMasterdataSeeder implements DataSeeder {

  private final TenantSystemService tenantService;
  private final ProductService productService;
  private final TransactionTemplate transactionTemplate;

  /**
   * Expected product counts per type. Used for granular isSeeded() verification. Products lack
   * unique codes, so we verify by type-count calibration.
   */
  private static final Map<ProductType, Long> EXPECTED_COUNTS =
      Map.of(
          ProductType.FIBER, 2L,
          ProductType.YARN, 2L,
          ProductType.FABRIC, 1L,
          ProductType.CHEMICAL, 1L);

  @Override
  public boolean isSeeded() {
    Optional<TenantDto> tenantOpt = tenantService.findBySlug(TenantSeeder.TENANT_SLUG);
    if (tenantOpt.isEmpty()) {
      return false;
    }

    return TenantContext.executeInTenantContext(
        tenantOpt.get().getId(),
        () -> {
          UUID tenantId = tenantOpt.get().getId();
          // Granular check: verify expected counts per product type
          return EXPECTED_COUNTS.entrySet().stream()
              .allMatch(
                  entry ->
                      productService.findByType(tenantId, entry.getKey()).size()
                          >= entry.getValue());
        });
  }

  @Override
  public void seed() {
    TenantDto tenant =
        tenantService
            .findBySlug(TenantSeeder.TENANT_SLUG)
            .orElseThrow(
                () -> new IllegalStateException("Tenant must be seeded before Masterdata"));

    TenantContext.executeInTenantContext(
        tenant.getId(),
        () -> {
          transactionTemplate.executeWithoutResult(
              status -> {
                UUID tenantId = tenant.getId();

                // Per-type count check to prevent duplicates on partial re-seed
                seedProductIfNeeded(tenantId, ProductType.FIBER, "kg", 2);
                seedProductIfNeeded(tenantId, ProductType.YARN, "kg", 2);
                seedProductIfNeeded(tenantId, ProductType.FABRIC, "m", 1);
                seedProductIfNeeded(tenantId, ProductType.CHEMICAL, "lt", 1);
              });
        });
  }

  /**
   * Creates products of the given type only if the current count is less than the expected count.
   * This prevents duplicates when a previous seed was partially completed.
   */
  private void seedProductIfNeeded(
      UUID tenantId, ProductType type, String unit, int expectedCount) {
    long currentCount = productService.findByType(tenantId, type).size();
    long toCreate = expectedCount - currentCount;

    if (toCreate <= 0) {
      log.debug("Product type {} already has {} records, skipping.", type, currentCount);
      return;
    }

    for (int i = 0; i < toCreate; i++) {
      CreateProductRequest req =
          CreateProductRequest.builder().productType(type).unit(unit).build();
      productService.createProduct(req);
      log.info("Created Product of type {} in unit {} ({}/{})", type, unit, i + 1, toCreate);
    }
  }

  @Override
  public int getOrder() {
    return 60;
  }
}
