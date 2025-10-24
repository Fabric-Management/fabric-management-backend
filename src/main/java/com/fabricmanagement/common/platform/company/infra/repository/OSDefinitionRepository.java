package com.fabricmanagement.common.platform.company.infra.repository;

import com.fabricmanagement.common.platform.company.domain.OSDefinition;
import com.fabricmanagement.common.platform.company.domain.OSType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for OSDefinition entity.
 *
 * <p>OS Definitions are system-wide (not tenant-specific).
 * They define available OS packages that tenants can subscribe to.</p>
 */
@Repository
public interface OSDefinitionRepository extends JpaRepository<OSDefinition, UUID> {

    Optional<OSDefinition> findByOsCode(String osCode);

    List<OSDefinition> findByOsType(OSType osType);

    List<OSDefinition> findByIsActiveTrue();

    boolean existsByOsCode(String osCode);
}

