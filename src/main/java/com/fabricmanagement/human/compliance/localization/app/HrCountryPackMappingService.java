package com.fabricmanagement.human.compliance.localization.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.human.compliance.localization.domain.HrCountryPackMapping;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPack;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPackStatus;
import com.fabricmanagement.human.compliance.localization.infra.repository.HrCountryPackMappingRepository;
import com.fabricmanagement.human.compliance.localization.infra.repository.HrPolicyPackRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HrCountryPackMappingService {

  private final HrCountryPackMappingRepository mappingRepository;
  private final HrPolicyPackRepository policyPackRepository;

  @Transactional
  public HrCountryPackMapping assign(String countryCode, String packCode) {
    UUID tenantId = TenantContext.requireTenantId();
    String normalizedCountry = normalize(countryCode);
    String normalizedPackCode = packCode.toUpperCase(Locale.ROOT);
    HrPolicyPack pack = resolvePackForMapping(tenantId, normalizedPackCode);

    HrCountryPackMapping mapping =
        mappingRepository
            .findByCountryCode(tenantId, normalizedCountry)
            .orElseGet(
                () -> {
                  HrCountryPackMapping newMapping =
                      HrCountryPackMapping.builder()
                          .countryCode(normalizedCountry)
                          .packCode(normalizedPackCode)
                          .packId(pack.getId())
                          .build();
                  newMapping.setTenantId(tenantId);
                  return newMapping;
                });

    mapping.assignPack(normalizedPackCode, pack.getId());
    return mappingRepository.save(mapping);
  }

  @Transactional
  public List<HrCountryPackMapping> updateMappingsForPack(
      UUID tenantId, String packCode, UUID packId) {
    List<HrCountryPackMapping> mappings = mappingRepository.findByPackCode(tenantId, packCode);
    for (HrCountryPackMapping mapping : mappings) {
      mapping.assignPack(packCode, packId);
      mappingRepository.save(mapping);
    }
    return mappings;
  }

  public Optional<HrCountryPackMapping> findMapping(UUID tenantId, String countryCode) {
    return mappingRepository.findByCountryCode(tenantId, normalize(countryCode));
  }

  public List<HrCountryPackMapping> listMappings(UUID tenantId) {
    return mappingRepository.findAllByTenant(tenantId);
  }

  private HrPolicyPack resolvePackForMapping(UUID tenantId, String packCode) {
    return policyPackRepository
        .findFirstByTenantIdAndPackCodeAndStatusInOrderByPackVersionDesc(
            tenantId,
            packCode,
            java.util.EnumSet.of(HrPolicyPackStatus.ACTIVE, HrPolicyPackStatus.DRAFT))
        .orElseThrow(
            () -> new IllegalArgumentException("Policy pack not found for mapping: " + packCode));
  }

  private String normalize(String countryCode) {
    return countryCode != null ? countryCode.toUpperCase(Locale.ROOT) : null;
  }
}
