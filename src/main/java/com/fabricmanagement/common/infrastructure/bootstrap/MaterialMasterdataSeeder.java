package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.app.TenantService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.production.masterdata.material.app.MaterialService;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.masterdata.material.dto.CreateMaterialRequest;
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
public class MaterialMasterdataSeeder implements DataSeeder {

  private final TenantService tenantService;
  private final MaterialService materialService;
  private final TransactionTemplate transactionTemplate;

  /**
   * Expected material counts per type. Used for granular isSeeded() verification. Materials lack
   * unique codes, so we verify by type-count calibration.
   */
  private static final Map<MaterialType, Long> EXPECTED_COUNTS =
      Map.of(
          MaterialType.FIBER, 2L,
          MaterialType.YARN, 2L,
          MaterialType.FABRIC, 1L,
          MaterialType.CHEMICAL, 1L);

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
          // Granular check: verify expected counts per material type
          return EXPECTED_COUNTS.entrySet().stream()
              .allMatch(
                  entry ->
                      materialService.findByType(tenantId, entry.getKey()).size()
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
                seedMaterialIfNeeded(tenantId, MaterialType.FIBER, "kg", 2);
                seedMaterialIfNeeded(tenantId, MaterialType.YARN, "kg", 2);
                seedMaterialIfNeeded(tenantId, MaterialType.FABRIC, "m", 1);
                seedMaterialIfNeeded(tenantId, MaterialType.CHEMICAL, "lt", 1);
              });
        });
  }

  /**
   * Creates materials of the given type only if the current count is less than the expected count.
   * This prevents duplicates when a previous seed was partially completed.
   */
  private void seedMaterialIfNeeded(
      UUID tenantId, MaterialType type, String unit, int expectedCount) {
    long currentCount = materialService.findByType(tenantId, type).size();
    long toCreate = expectedCount - currentCount;

    if (toCreate <= 0) {
      log.debug("Material type {} already has {} records, skipping.", type, currentCount);
      return;
    }

    for (int i = 0; i < toCreate; i++) {
      CreateMaterialRequest req =
          CreateMaterialRequest.builder().materialType(type).unit(unit).build();
      materialService.createMaterial(req);
      log.info("Created Material of type {} in unit {} ({}/{})", type, unit, i + 1, toCreate);
    }
  }

  @Override
  public int getOrder() {
    return 60;
  }
}
