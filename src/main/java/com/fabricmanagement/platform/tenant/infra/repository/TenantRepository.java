package com.fabricmanagement.platform.tenant.infra.repository;

import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import com.fabricmanagement.platform.tenant.domain.TenantType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for Tenant entity.
 *
 * <p><b>IMPORTANT:</b> This is a PLATFORM-LEVEL repository. Unlike other repositories that filter
 * by tenant_id, this repository operates across all tenants.
 *
 * <p>Access should be restricted to:
 *
 * <ul>
 *   <li>Platform admin endpoints
 *   <li>Onboarding flow
 *   <li>Billing/subscription management
 *   <li>TenantContext initialization (JWT interceptor)
 * </ul>
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

  // ========================================
  // LOOKUP QUERIES
  // ========================================

  /**
   * Find tenant by UID.
   *
   * @param uid Human-readable UID (e.g., "ACME-001")
   * @return tenant if found
   */
  Optional<Tenant> findByUid(String uid);

  /**
   * Find tenant by slug.
   *
   * @param slug URL-friendly identifier (e.g., "acme-corp")
   * @return tenant if found
   */
  Optional<Tenant> findBySlug(String slug);

  /**
   * Find active tenant by ID.
   *
   * @param id Tenant UUID
   * @return tenant if found and active
   */
  @Query("SELECT t FROM Tenant t WHERE t.id = :id AND t.isActive = true")
  Optional<Tenant> findActiveById(@Param("id") UUID id);

  /**
   * Check if UID is already in use.
   *
   * @param uid UID to check
   * @return true if UID exists
   */
  boolean existsByUid(String uid);

  /**
   * Check if slug is already in use.
   *
   * @param slug Slug to check
   * @return true if slug exists
   */
  boolean existsBySlug(String slug);

  // ========================================
  // STATUS QUERIES
  // ========================================

  /**
   * Find all tenants by status.
   *
   * @param status Tenant status
   * @return list of tenants with given status
   */
  List<Tenant> findByStatus(TenantStatus status);

  /** Find all tenants by type. */
  List<Tenant> findByType(TenantType type);

  /** Find active playgrounds older than a specific date for TTL reaping. */
  @Query(
      "SELECT t FROM Tenant t WHERE t.type = :type AND t.isActive = true AND t.createdAt < :threshold")
  List<Tenant> findExpiredPlaygrounds(
      @Param("type") TenantType type, @Param("threshold") Instant threshold);

  /**
   * Find all active tenants.
   *
   * @return list of active tenants
   */
  @Query("SELECT t FROM Tenant t WHERE t.isActive = true ORDER BY t.createdAt DESC")
  List<Tenant> findAllActive();

  /**
   * Find all active tenants with pagination.
   *
   * @param pageable page and sort parameters
   * @return page of active tenants
   */
  @Query("SELECT t FROM Tenant t WHERE t.isActive = true")
  Page<Tenant> findAllActive(Pageable pageable);

  /**
   * Find tenants with expired trials.
   *
   * @param now Current timestamp
   * @return list of tenants with expired trials that haven't been suspended yet
   */
  @Query(
      """
      SELECT t FROM Tenant t
      WHERE t.status = 'TRIAL'
        AND t.trialEndsAt IS NOT NULL
        AND t.trialEndsAt < :now
        AND t.isActive = true
      """)
  List<Tenant> findExpiredTrials(@Param("now") Instant now);

  /**
   * Find tenants needing trial expiry warning.
   *
   * @param warningThreshold Date threshold (e.g., 7 days from now)
   * @param now Current timestamp
   * @return list of tenants whose trial expires soon
   */
  @Query(
      """
      SELECT t FROM Tenant t
      WHERE t.status = 'TRIAL'
        AND t.trialEndsAt IS NOT NULL
        AND t.trialEndsAt BETWEEN :now AND :warningThreshold
        AND t.isActive = true
      """)
  List<Tenant> findTrialsExpiringSoon(
      @Param("now") Instant now, @Param("warningThreshold") Instant warningThreshold);

  // ========================================
  // STATISTICS QUERIES
  // ========================================

  /**
   * Count tenants by status.
   *
   * @param status Tenant status
   * @return count of tenants
   */
  long countByStatus(TenantStatus status);

  /**
   * Count all active tenants.
   *
   * @return count of active tenants
   */
  @Query("SELECT COUNT(t) FROM Tenant t WHERE t.isActive = true")
  long countActive();

  // ========================================
  // BULK OPERATIONS
  // ========================================

  /**
   * Bulk suspend expired trial tenants.
   *
   * @param now Current timestamp
   * @return number of tenants suspended
   */
  @Modifying
  @Query(
      """
      UPDATE Tenant t
      SET t.status = 'SUSPENDED', t.updatedAt = :now
      WHERE t.status = 'TRIAL'
        AND t.trialEndsAt IS NOT NULL
        AND t.trialEndsAt < :now
        AND t.isActive = true
      """)
  int suspendExpiredTrials(@Param("now") Instant now);
}
