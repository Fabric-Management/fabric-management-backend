package com.fabricmanagement.common.platform.company.infra.repository;

import com.fabricmanagement.common.platform.company.domain.Subscription;
import com.fabricmanagement.common.platform.company.domain.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Subscription entity.
 *
 * <p>CRITICAL for Policy Engine Layer 1 - OS Subscription checks.</p>
 * <p>All queries are tenant-scoped for multi-tenant isolation.</p>
 */
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<Subscription> findByTenantIdAndOsCode(UUID tenantId, String osCode);

    List<Subscription> findByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);

    List<Subscription> findByTenantId(UUID tenantId);

    @Query("SELECT s FROM Subscription s WHERE s.tenantId = :tenantId AND s.status = 'ACTIVE' AND (s.expiryDate IS NULL OR s.expiryDate > :now)")
    List<Subscription> findActiveSubscriptions(@Param("tenantId") UUID tenantId, @Param("now") Instant now);

    @Query("SELECT s FROM Subscription s WHERE s.tenantId = :tenantId AND s.osCode = :osCode AND s.status = 'ACTIVE' AND (s.expiryDate IS NULL OR s.expiryDate > :now)")
    Optional<Subscription> findActiveSubscriptionByOsCode(
        @Param("tenantId") UUID tenantId,
        @Param("osCode") String osCode,
        @Param("now") Instant now);

    @Query("SELECT s FROM Subscription s WHERE s.status = 'TRIAL' AND s.trialEndsAt < :sevenDaysFromNow AND s.trialEndsAt > :now")
    List<Subscription> findTrialsEndingSoon(@Param("now") Instant now, @Param("sevenDaysFromNow") Instant sevenDaysFromNow);

    boolean existsByTenantIdAndOsCode(UUID tenantId, String osCode);
}

