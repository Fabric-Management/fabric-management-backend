package com.fabricmanagement.human.compliance.localization.api.facade;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.human.compliance.localization.app.HrPolicyPackMapper;
import com.fabricmanagement.human.compliance.localization.app.HrPolicyPackResolver;
import com.fabricmanagement.human.compliance.localization.app.HrPolicyPackService;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPackStatus;
import com.fabricmanagement.human.compliance.localization.dto.AssignCountryPackRequest;
import com.fabricmanagement.human.compliance.localization.dto.HrCountryPackMappingResponse;
import com.fabricmanagement.human.compliance.localization.dto.HrInheritanceModeDto;
import com.fabricmanagement.human.compliance.localization.dto.HrPolicyPackLineageResponse;
import com.fabricmanagement.human.compliance.localization.dto.HrPolicyPackResponse;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HrPolicyPackFacade {

  private final HrPolicyPackService policyPackService;
  private final HrPolicyPackResolver policyPackResolver;
  private final com.fabricmanagement.human.compliance.localization.app.HrCountryPackMappingService
      countryPackMappingService;

  public HrCountryPackMappingResponse assignCountryPack(AssignCountryPackRequest request) {
    var mapping = countryPackMappingService.assign(request.countryCode(), request.packCode());
    return new HrCountryPackMappingResponse(
        mapping.getId(), mapping.getCountryCode(), mapping.getPackCode());
  }

  public List<HrCountryPackMappingResponse> listMappings(UUID tenantId) {
    return countryPackMappingService.listMappings(tenantId).stream()
        .map(
            mapping ->
                new HrCountryPackMappingResponse(
                    mapping.getId(), mapping.getCountryCode(), mapping.getPackCode()))
        .toList();
  }

  public List<HrPolicyPackResponse> listPacks(
      UUID tenantId, String countryCode, String regionCode, HrPolicyPackStatus status) {
    return HrPolicyPackMapper.toResponseList(
        policyPackService.listPacks(tenantId, countryCode, regionCode, status));
  }

  public List<HrPolicyPackResponse> getHistory(UUID tenantId, String packCode) {
    return HrPolicyPackMapper.toResponseList(
        policyPackService.getHistory(tenantId, packCode.toUpperCase(Locale.ROOT)));
  }

  public HrPolicyPackLineageResponse getLineage(
      UUID tenantId, String packCode, Integer packVersion) {
    String normalizedCode = packCode.toUpperCase(Locale.ROOT);

    var pack =
        (packVersion != null
                ? policyPackService.findByPackCodeAndVersion(tenantId, normalizedCode, packVersion)
                : policyPackService
                    .findActiveByPackCode(tenantId, normalizedCode)
                    .or(() -> policyPackService.findLatestByPackCode(tenantId, normalizedCode)))
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "No policy pack found for code %s (version %s)"
                            .formatted(
                                normalizedCode, packVersion != null ? packVersion : "latest")));

    var resolved = policyPackResolver.resolve(tenantId, pack);
    return new HrPolicyPackLineageResponse(
        pack.getPackCode(),
        pack.getPackVersion(),
        pack.getCountryCode(),
        pack.getRegionCode(),
        pack.getParentPackCode(),
        pack.getInheritanceMode() != null
            ? HrInheritanceModeDto.valueOf(pack.getInheritanceMode().name())
            : null,
        resolved.lineageCodes(),
        resolved.resolvedPayload());
  }
}
