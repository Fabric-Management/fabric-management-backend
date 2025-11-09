package com.fabricmanagement.human.compliance.localization.infra.repository;

import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPack;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPackStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface HrPolicyPackRepository extends JpaRepository<HrPolicyPack, UUID> {

    @Query("""
        select p from HrPolicyPack p
        where p.tenantId = :tenantId
          and p.countryCode = :countryCode
          and p.status = :status
          and p.effectiveFrom <= :moment
          and (p.effectiveTo is null or p.effectiveTo > :moment)
        order by p.packVersion desc
        """)
    Optional<HrPolicyPack> findActivePack(@Param("tenantId") UUID tenantId,
                                          @Param("countryCode") String countryCode,
                                          @Param("status") HrPolicyPackStatus status,
                                          @Param("moment") Instant moment);

    @Query("""
        select p from HrPolicyPack p
        where p.tenantId = :tenantId
          and p.countryCode = :countryCode
          and p.status in :statuses
        order by p.effectiveFrom desc, p.packVersion desc
        """)
    Optional<HrPolicyPack> findLatestPack(@Param("tenantId") UUID tenantId,
                                          @Param("countryCode") String countryCode,
                                          @Param("statuses") Iterable<HrPolicyPackStatus> statuses);

    Optional<HrPolicyPack> findFirstByTenantIdAndPackCodeOrderByPackVersionDesc(UUID tenantId, String packCode);

    Optional<HrPolicyPack> findFirstByTenantIdAndPackCodeAndStatusInOrderByPackVersionDesc(
        UUID tenantId, String packCode, Iterable<HrPolicyPackStatus> statuses);

    @Query("""
        select p from HrPolicyPack p
        where p.tenantId = :tenantId
          and (:countryCode is null or p.countryCode = :countryCode)
          and (:regionCode is null or p.regionCode = :regionCode)
          and (:status is null or p.status = :status)
        order by p.packCode asc, p.packVersion desc
        """)
    java.util.List<HrPolicyPack> findAllByTenantAndFilters(@Param("tenantId") UUID tenantId,
                                                           @Param("countryCode") String countryCode,
                                                           @Param("regionCode") String regionCode,
                                                           @Param("status") HrPolicyPackStatus status);

    java.util.List<HrPolicyPack> findByTenantIdAndPackCodeOrderByPackVersionDesc(UUID tenantId, String packCode);

    @Query("""
        select p from HrPolicyPack p
        where p.tenantId = :tenantId
          and p.packCode = :packCode
          and p.status = :status
        order by p.packVersion desc
        """)
    Optional<HrPolicyPack> findByPackCodeAndStatus(@Param("tenantId") UUID tenantId,
                                                   @Param("packCode") String packCode,
                                                   @Param("status") HrPolicyPackStatus status);

    Optional<HrPolicyPack> findByTenantIdAndPackCodeAndPackVersion(UUID tenantId, String packCode, Integer packVersion);
}

