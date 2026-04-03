package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.iwm.location.app.WarehouseLocationService;
import com.fabricmanagement.iwm.location.domain.WarehouseLocationType;
import com.fabricmanagement.iwm.location.dto.CreateWarehouseLocationRequest;
import com.fabricmanagement.iwm.location.infra.repository.WarehouseLocationRepository;
import com.fabricmanagement.platform.tenant.app.TenantService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class WarehouseSeeder implements DataSeeder {

  private final TenantService tenantService;
  private final WarehouseLocationService warehouseLocationService;
  private final WarehouseLocationRepository warehouseLocationRepository;
  private final TransactionTemplate transactionTemplate;

  /** All expected warehouse codes — used for granular isSeeded() verification. */
  private static final List<String> EXPECTED_CODES = List.of("RAW_MAT", "SHOP_FL", "FIN_MAT");

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
          // Granular check: verify ALL 3 expected warehouse codes exist
          return EXPECTED_CODES.stream()
              .allMatch(
                  code -> warehouseLocationRepository.existsByTenantIdAndCode(tenantId, code));
        });
  }

  @Override
  public void seed() {
    TenantDto tenant =
        tenantService
            .findBySlug(TenantSeeder.TENANT_SLUG)
            .orElseThrow(
                () -> new IllegalStateException("Tenant must be seeded before Warehouses"));

    TenantContext.executeInTenantContext(
        tenant.getId(),
        () -> {
          transactionTemplate.executeWithoutResult(
              status -> {
                seedWarehouse(
                    "RAW_MAT",
                    "Ana Hammadde Deposu",
                    "Tüm hammaddeler buraya iner",
                    tenant.getId());
                seedWarehouse(
                    "SHOP_FL",
                    "Üretim Sahası",
                    "İş emirlerinin tüketim yaptığı alan",
                    tenant.getId());
                seedWarehouse("FIN_MAT", "Mamül Deposu", "Biten mallar", tenant.getId());
              });
        });
  }

  private void seedWarehouse(String code, String name, String desc, UUID tenantId) {
    if (warehouseLocationRepository.existsByTenantIdAndCode(tenantId, code)) {
      log.debug("Warehouse already exists (code={}), skipping: {}", code, name);
      return;
    }

    CreateWarehouseLocationRequest req =
        CreateWarehouseLocationRequest.builder()
            .code(code)
            .name(name)
            .description(desc)
            .type(WarehouseLocationType.WAREHOUSE)
            .build();
    warehouseLocationService.create(req);
    log.info("Created Warehouse: {}", name);
  }

  @Override
  public int getOrder() {
    return 50;
  }
}
