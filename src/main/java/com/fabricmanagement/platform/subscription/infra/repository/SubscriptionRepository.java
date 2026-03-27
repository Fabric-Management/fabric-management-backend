package com.fabricmanagement.platform.subscription.infra.repository;

import com.fabricmanagement.platform.subscription.domain.Subscription;
import com.fabricmanagement.platform.subscription.domain.SubscriptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for Subscription entity.
 *
 * <p>CRITICAL for Policy Engine Layer 1 - OS Subscription checks.
 *
 * <p>All queries are tenant-scoped for multi-tenant isolation.
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

  Optional<Subscription> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<Subscription> findByTenantIdAndOsCode(UUID tenantId, String osCode);

  List<Subscription> findByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);

  List<Subscription> findByTenantId(UUID tenantId);

  @Query(
      "SELECT s FROM Subscription s WHERE s.tenantId = :tenantId AND s.status = 'ACTIVE' AND (s.expiryDate IS NULL OR s.expiryDate > :now)")
  List<Subscription> findActiveSubscriptions(
      @Param("tenantId") UUID tenantId, @Param("now") Instant now);

  @Query(
      "SELECT s FROM Subscription s WHERE s.tenantId = :tenantId AND s.osCode = :osCode AND s.status = 'ACTIVE' AND (s.expiryDate IS NULL OR s.expiryDate > :now)")
  Optional<Subscription> findActiveSubscriptionByOsCode(
      @Param("tenantId") UUID tenantId, @Param("osCode") String osCode, @Param("now") Instant now);

  @Query(
      "SELECT s FROM Subscription s WHERE s.status = 'TRIAL' AND s.trialEndsAt < :sevenDaysFromNow AND s.trialEndsAt > :now")
  List<Subscription> findTrialsEndingSoon(
      @Param("now") Instant now, @Param("sevenDaysFromNow") Instant sevenDaysFromNow);

  /**
   * Find subscriptions that are expired but not yet marked as EXPIRED status. Used for batch
   * processing expiring subscriptions.
   *
   * @param now current timestamp
   * @param pageable pagination parameters
   * @return page of expired subscriptions
   */
  @Query(
      "SELECT s FROM Subscription s "
          + "WHERE s.status != 'EXPIRED' "
          + "AND (s.expiryDate IS NOT NULL AND s.expiryDate < :now "
          + "OR (s.trialEndsAt IS NOT NULL AND s.trialEndsAt < :now))")
  Page<Subscription> findExpiredButNotExpiredStatus(@Param("now") Instant now, Pageable pageable);

  /**
   * Count active subscriptions for a tenant.
   *
   * <p><b>Performance:</b> This query implements the same logic as {@link Subscription#isActive()}
   * at the database level to avoid loading all subscriptions into memory.
   *
   * <p>An active subscription is:
   *
   * <ul>
   *   <li>Status = ACTIVE AND (expiryDate IS NULL OR expiryDate > now)
   *   <li>OR Status = TRIAL AND trialEndsAt IS NOT NULL AND trialEndsAt > now
   * </ul>
   *
   * @param tenantId the tenant ID
   * @param now current timestamp
   * @return count of active subscriptions
   */
  @Query(
      "SELECT COUNT(s) FROM Subscription s "
          + "WHERE s.tenantId = :tenantId "
          + "AND ("
          + "  (s.status = 'ACTIVE' AND (s.expiryDate IS NULL OR s.expiryDate > :now)) "
          + "  OR (s.status = 'TRIAL' AND s.trialEndsAt IS NOT NULL AND s.trialEndsAt > :now)"
          + ")")
  long countActiveSubscriptionsByTenantId(
      @Param("tenantId") UUID tenantId, @Param("now") Instant now);

  boolean existsByTenantIdAndOsCode(UUID tenantId, String osCode);
}
