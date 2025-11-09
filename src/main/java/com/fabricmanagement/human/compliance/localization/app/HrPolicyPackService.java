package com.fabricmanagement.human.compliance.localization.app;

import com.fabricmanagement.human.compliance.localization.domain.HrLocalizationCacheNames;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPack;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPackStatus;
import com.fabricmanagement.human.compliance.localization.infra.repository.HrPolicyPackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HrPolicyPackService {

    private final HrPolicyPackRepository repository;
    private final Clock clock;

    @Cacheable(
        cacheNames = HrLocalizationCacheNames.ACTIVE_POLICY_PACK,
        key = "T(java.lang.String).format('%s::%s', #tenantId, #countryCode)",
        unless = "#result == null || #result.isEmpty()"
    )
    public Optional<HrPolicyPack> findActivePack(UUID tenantId, String countryCode) {
        Instant now = clock.instant();
        return repository.findActivePack(tenantId, countryCode, HrPolicyPackStatus.ACTIVE, now);
    }

    public Optional<HrPolicyPack> findLatestPack(UUID tenantId, String countryCode) {
        return repository.findLatestPack(tenantId, countryCode, EnumSet.of(HrPolicyPackStatus.ACTIVE, HrPolicyPackStatus.DRAFT));
    }

    public Optional<HrPolicyPack> findLatestByPackCode(UUID tenantId, String packCode) {
        return repository.findFirstByTenantIdAndPackCodeOrderByPackVersionDesc(tenantId, packCode);
    }

    public Optional<HrPolicyPack> findActiveByPackCode(UUID tenantId, String packCode) {
        return repository.findByPackCodeAndStatus(tenantId, packCode, HrPolicyPackStatus.ACTIVE);
    }

    public Optional<HrPolicyPack> findDraftByPackCode(UUID tenantId, String packCode) {
        return repository.findFirstByTenantIdAndPackCodeAndStatusInOrderByPackVersionDesc(
            tenantId,
            packCode,
            EnumSet.of(HrPolicyPackStatus.DRAFT)
        );
    }

    public List<HrPolicyPack> listPacks(UUID tenantId, String countryCode, String regionCode, HrPolicyPackStatus status) {
        return repository.findAllByTenantAndFilters(tenantId, countryCode, regionCode, status);
    }

    public List<HrPolicyPack> getHistory(UUID tenantId, String packCode) {
        return repository.findByTenantIdAndPackCodeOrderByPackVersionDesc(tenantId, packCode);
    }

    public Optional<HrPolicyPack> findByPackCodeAndVersion(UUID tenantId, String packCode, Integer packVersion) {
        return repository.findByTenantIdAndPackCodeAndPackVersion(tenantId, packCode, packVersion);
    }
}

