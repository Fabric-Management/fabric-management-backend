package com.fabricmanagement.platform.tenant.infra.repository;

import com.fabricmanagement.platform.tenant.domain.BrandAssetKind;
import com.fabricmanagement.platform.tenant.domain.TenantBrandAsset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantBrandAssetRepository extends JpaRepository<TenantBrandAsset, UUID> {
  List<TenantBrandAsset> findByTenantIdAndIsActiveTrue(UUID tenantId);

  Optional<TenantBrandAsset> findByTenantIdAndKind(UUID tenantId, BrandAssetKind kind);

  Optional<TenantBrandAsset> findByTenantIdAndKindAndIsActiveTrue(
      UUID tenantId, BrandAssetKind kind);

  Optional<TenantBrandAsset> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);
}
