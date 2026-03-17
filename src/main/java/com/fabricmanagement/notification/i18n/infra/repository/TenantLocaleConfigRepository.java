package com.fabricmanagement.notification.i18n.infra.repository;

import com.fabricmanagement.notification.i18n.domain.TenantLocaleConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TenantLocaleConfigRepository extends JpaRepository<TenantLocaleConfig, UUID> {

  Optional<TenantLocaleConfig> findByTenantId(UUID tenantId);
}
