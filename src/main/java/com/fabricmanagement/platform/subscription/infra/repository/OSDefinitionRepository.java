package com.fabricmanagement.platform.subscription.infra.repository;

import com.fabricmanagement.platform.subscription.domain.OSDefinition;
import com.fabricmanagement.platform.subscription.domain.OSType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for OSDefinition entity.
 *
 * <p>OS Definitions are system-wide (not tenant-specific). They define available OS packages that
 * tenants can subscribe to.
 */
@Repository
public interface OSDefinitionRepository extends JpaRepository<OSDefinition, UUID> {

  Optional<OSDefinition> findByOsCode(String osCode);

  List<OSDefinition> findByOsType(OSType osType);

  List<OSDefinition> findByIsActiveTrue();

  /**
   * Every definition owned by {@code tenantId}, whatever its lifecycle state (offered, deactivated
   * or soft-deleted). Package access decisions read the system catalogue through this, never
   * through {@link #findByIsActiveTrue()}: which resources a package owns must not change when the
   * package stops being offered.
   */
  List<OSDefinition> findByTenantId(UUID tenantId);

  boolean existsByOsCode(String osCode);
}
