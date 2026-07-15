package com.fabricmanagement.production.masterdata.color.infra.repository;

import com.fabricmanagement.production.masterdata.color.domain.Color;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ColorRepository
    extends JpaRepository<Color, UUID>, JpaSpecificationExecutor<Color> {

  List<Color> findByTenantIdAndIsActiveTrueOrderByCode(UUID tenantId);

  List<Color> findByTenantIdOrderByCode(UUID tenantId);

  Optional<Color> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<Color> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);

  Optional<Color> findByTenantIdAndCode(UUID tenantId, String code);

  boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
