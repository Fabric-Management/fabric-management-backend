package com.fabricmanagement.common.platform.company.infra.repository;

import com.fabricmanagement.common.platform.company.domain.SubscriptionQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for SubscriptionQuota entity.
 *
 * <p>Manages quota tracking for subscriptions (users, API calls, storage, etc.)</p>
 */
@Repository
public interface SubscriptionQuotaRepository extends JpaRepository<SubscriptionQuota, UUID> {

    /**
     * Find quota by tenant ID and quota type.
     *
     * @param tenantId the tenant ID
     * @param quotaType the quota type (e.g., "api_calls")
     * @return the quota if found
     */
    Optional<SubscriptionQuota> findByTenantIdAndQuotaType(UUID tenantId, String quotaType);

    /**
     * Find all quotas for a tenant.
     *
     * @param tenantId the tenant ID
     * @return list of quotas
     */
    List<SubscriptionQuota> findByTenantId(UUID tenantId);

    /**
     * Find all quotas for a specific subscription.
     *
     * @param subscriptionId the subscription ID
     * @return list of quotas
     */
    List<SubscriptionQuota> findBySubscriptionId(UUID subscriptionId);

    /**
     * Find all quotas with a specific reset period.
     *
     * <p>Used by scheduled jobs to reset monthly/daily quotas.</p>
     *
     * @param resetPeriod the reset period (e.g., "MONTHLY")
     * @return list of quotas
     */
    List<SubscriptionQuota> findByResetPeriod(String resetPeriod);

    /**
     * Find all exceeded quotas for a tenant.
     *
     * @param tenantId the tenant ID
     * @return list of exceeded quotas
     */
    @Query("SELECT q FROM SubscriptionQuota q WHERE q.tenantId = :tenantId AND q.quotaUsed >= q.quotaLimit")
    List<SubscriptionQuota> findExceededQuotas(@Param("tenantId") UUID tenantId);

    /**
     * Check if tenant has exceeded a specific quota.
     *
     * @param tenantId the tenant ID
     * @param quotaType the quota type
     * @return true if quota is exceeded
     */
    @Query("SELECT CASE WHEN COUNT(q) > 0 THEN true ELSE false END " +
           "FROM SubscriptionQuota q " +
           "WHERE q.tenantId = :tenantId AND q.quotaType = :quotaType " +
           "AND q.quotaUsed >= q.quotaLimit")
    boolean isQuotaExceeded(@Param("tenantId") UUID tenantId, @Param("quotaType") String quotaType);

    /**
     * Get remaining quota for a specific type.
     *
     * @param tenantId the tenant ID
     * @param quotaType the quota type
     * @return remaining quota (0 if not found or exceeded)
     */
    @Query("SELECT CASE WHEN q.quotaLimit > q.quotaUsed " +
           "THEN (q.quotaLimit - q.quotaUsed) ELSE 0 END " +
           "FROM SubscriptionQuota q " +
           "WHERE q.tenantId = :tenantId AND q.quotaType = :quotaType")
    Optional<Long> getRemainingQuota(@Param("tenantId") UUID tenantId, @Param("quotaType") String quotaType);
}

