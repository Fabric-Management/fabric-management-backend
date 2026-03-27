package com.fabricmanagement.platform.organization.infra.repository;

import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for Organization entity.
 *
 * <p>All queries are tenant-scoped for multi-tenant isolation.
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

  /**
   * Find organization by tenant and ID.
   *
   * @param tenantId Tenant UUID
   * @param id Organization UUID
   * @return Organization if found
   */
  Optional<Organization> findByTenantIdAndId(UUID tenantId, UUID id);

  /**
   * Find organization by tenant and UID.
   *
   * @param tenantId Tenant UUID
   * @param uid Human-readable UID
   * @return Organization if found
   */
  Optional<Organization> findByTenantIdAndUid(UUID tenantId, String uid);

  /**
   * Find organization by tax ID (global lookup).
   *
   * @param taxId Tax ID
   * @return Organization if found
   */
  Optional<Organization> findByTaxId(String taxId);

  /**
   * Find all active organizations for a tenant.
   *
   * @param tenantId Tenant UUID
   * @return List of active organizations
   */
  List<Organization> findByTenantIdAndIsActiveTrue(UUID tenantId);

  /**
   * Find organizations by type.
   *
   * @param tenantId Tenant UUID
   * @param organizationType Organization type
   * @return List of organizations
   */
  List<Organization> findByTenantIdAndOrganizationType(
      UUID tenantId, OrganizationType organizationType);

  /**
   * Find child organizations.
   *
   * @param tenantId Tenant UUID
   * @param parentOrganizationId Parent organization UUID
   * @return List of child organizations
   */
  List<Organization> findByTenantIdAndParentOrganizationId(
      UUID tenantId, UUID parentOrganizationId);

  /**
   * Check if organization exists.
   *
   * @param tenantId Tenant UUID
   * @param id Organization UUID
   * @return true if exists
   */
  boolean existsByTenantIdAndId(UUID tenantId, UUID id);

  /**
   * Check if tax ID exists globally.
   *
   * @param taxId Tax ID
   * @return true if exists
   */
  boolean existsByTaxId(String taxId);

  /**
   * Check if tax ID exists within tenant.
   *
   * @param tenantId Tenant UUID
   * @param taxId Tax ID
   * @return true if exists
   */
  boolean existsByTenantIdAndTaxId(UUID tenantId, String taxId);

  /**
   * Find organization by tenant and tax ID.
   *
   * @param tenantId Tenant UUID
   * @param taxId Tax ID
   * @return Organization if found
   */
  Optional<Organization> findByTenantIdAndTaxId(UUID tenantId, String taxId);

  /**
   * Search organizations by name.
   *
   * @param tenantId Tenant UUID
   * @param name Name pattern
   * @return List of matching organizations
   */
  @Query("SELECT o FROM Organization o WHERE o.tenantId = :tenantId AND o.name LIKE %:name%")
  List<Organization> searchByName(@Param("tenantId") UUID tenantId, @Param("name") String name);

  /**
   * Find organizations by multiple types.
   *
   * @param tenantId Tenant UUID
   * @param types List of types
   * @return List of organizations
   */
  @Query(
      "SELECT o FROM Organization o WHERE o.tenantId = :tenantId AND o.organizationType IN :types")
  List<Organization> findByTenantIdAndOrganizationTypeIn(
      @Param("tenantId") UUID tenantId, @Param("types") List<OrganizationType> types);

  /**
   * Find the root organization for a tenant.
   *
   * <p>The root organization is the internal tenant organization created during onboarding.
   * EXTERNAL_PARTNER organizations are explicitly excluded because they are also parentless (no
   * parent_organization_id) — without this exclusion, creating a TradingPartner causes a
   * NonUniqueResultException.
   *
   * @param tenantId Tenant UUID
   * @param excludeType Organization type to exclude (pass {@link
   *     OrganizationType#EXTERNAL_PARTNER})
   * @return Root organization if found
   */
  @Query(
      "SELECT o FROM Organization o WHERE o.tenantId = :tenantId"
          + " AND o.parentOrganizationId IS NULL"
          + " AND o.isActive = true"
          + " AND o.organizationType != :excludeType")
  Optional<Organization> findRootOrganization(
      @Param("tenantId") UUID tenantId, @Param("excludeType") OrganizationType excludeType);

  /**
   * Count active organizations for a tenant.
   *
   * @param tenantId Tenant UUID
   * @return Count
   */
  long countByTenantIdAndIsActiveTrue(UUID tenantId);

  /**
   * Count organizations by type.
   *
   * @param tenantId Tenant UUID
   * @param organizationType Organization type
   * @return Count
   */
  long countByTenantIdAndOrganizationType(UUID tenantId, OrganizationType organizationType);

  /**
   * Find organization by UID (global lookup).
   *
   * @param uid UID
   * @return Organization if found
   */
  Optional<Organization> findByUid(String uid);
}
