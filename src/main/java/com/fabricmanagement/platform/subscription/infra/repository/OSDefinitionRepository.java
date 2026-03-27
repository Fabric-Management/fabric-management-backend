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

  boolean existsByOsCode(String osCode);
}
