package com.fabricmanagement.human.compliance.localization.app;

import com.fabricmanagement.human.compliance.localization.domain.HrInheritanceMode;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPack;
import com.fabricmanagement.human.compliance.localization.domain.HrRuleVersion;
import com.fabricmanagement.human.compliance.localization.dto.HrPolicyPackResponse;
import com.fabricmanagement.human.compliance.localization.dto.PolicyBindingDto;
import com.fabricmanagement.human.compliance.localization.dto.RuleVersionDto;

import com.fabricmanagement.human.compliance.localization.dto.HrInheritanceModeDto;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyBinding;

import java.util.Comparator;
import java.util.List;

public final class HrPolicyPackMapper {

    private HrPolicyPackMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static HrPolicyPackResponse toResponse(HrPolicyPack pack) {
        return new HrPolicyPackResponse(
            pack.getId(),
            pack.getPackCode(),
            pack.getCountryCode(),
            pack.getName(),
            pack.getDescription(),
            pack.getPackVersion(),
            pack.getStatus(),
            pack.getEffectiveFrom(),
            pack.getEffectiveTo(),
            pack.getChecksum(),
            pack.getParentPackCode(),
            pack.getRegionCode(),
            mapInheritanceMode(pack.getInheritanceMode()),
            pack.getUpdatedAt(),
            pack.getPolicyBindings().stream()
                .sorted(Comparator.comparing(HrPolicyBinding::getPolicyInterface))
                .map(binding -> new PolicyBindingDto(binding.getPolicyInterface(), binding.getStrategyBean(), binding.getConfigReference()))
                .toList(),
            pack.getRuleVersions().stream()
                .sorted(Comparator.comparing(HrRuleVersion::getRuleType))
                .map(version -> new RuleVersionDto(version.getRuleType(), version.getPayloadHash(), version.getPayload()))
                .toList()
        );
    }

    public static List<HrPolicyPackResponse> toResponseList(List<HrPolicyPack> packs) {
        return packs.stream()
            .map(HrPolicyPackMapper::toResponse)
            .toList();
    }

    private static HrInheritanceModeDto mapInheritanceMode(HrInheritanceMode mode) {
        if (mode == null) {
            return HrInheritanceModeDto.FULL;
        }
        return switch (mode) {
            case FULL -> HrInheritanceModeDto.FULL;
            case PARTIAL -> HrInheritanceModeDto.PARTIAL;
        };
    }
}

