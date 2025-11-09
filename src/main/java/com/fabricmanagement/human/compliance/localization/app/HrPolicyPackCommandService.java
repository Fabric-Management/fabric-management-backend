package com.fabricmanagement.human.compliance.localization.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.human.compliance.localization.domain.HrInheritanceMode;
import com.fabricmanagement.human.compliance.localization.domain.HrLocalizationCacheNames;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyBinding;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPack;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPackAction;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPackStatus;
import com.fabricmanagement.human.compliance.localization.domain.HrRuleAuditLog;
import com.fabricmanagement.human.compliance.localization.domain.HrRuleVersion;
import com.fabricmanagement.human.compliance.localization.dto.CreateHrPolicyPackRequest;
import com.fabricmanagement.human.compliance.localization.dto.HrPolicyPackResponse;
import com.fabricmanagement.human.compliance.localization.dto.HrInheritanceModeDto;
import com.fabricmanagement.human.compliance.localization.dto.PolicyBindingRequest;
import com.fabricmanagement.human.compliance.localization.dto.PublishHrPolicyPackRequest;
import com.fabricmanagement.human.compliance.localization.dto.RetireHrPolicyPackRequest;
import com.fabricmanagement.human.compliance.localization.dto.RuleVersionRequest;
import com.fabricmanagement.human.compliance.localization.dto.UpdateHrPolicyPackRequest;
import com.fabricmanagement.human.compliance.localization.dto.ValidateHrPolicyPackResponse;
import com.fabricmanagement.human.compliance.localization.infra.repository.HrPolicyPackRepository;
import com.fabricmanagement.human.compliance.localization.infra.repository.HrRuleAuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class HrPolicyPackCommandService {

    private final HrPolicyPackRepository policyPackRepository;
    private final HrRuleAuditLogRepository auditLogRepository;
    private final HrPolicyPackService policyPackService;
    private final HrCountryPackMappingService countryPackMappingService;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public HrPolicyPackResponse createDraft(CreateHrPolicyPackRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        String packCode = request.packCode().toUpperCase();

        Optional<HrPolicyPack> existingDraft = policyPackService.findDraftByPackCode(tenantId, packCode);
        if (existingDraft.isPresent()) {
            throw new IllegalStateException("Draft already exists for packCode=" + packCode);
        }

        int nextVersion = policyPackService.findLatestByPackCode(tenantId, packCode)
            .map(HrPolicyPack::getPackVersion)
            .map(v -> v + 1)
            .orElse(1);

        assertJsonPayload(request.payload());
        String checksum = computeChecksum(request.payload());

        UUID parentPackId = resolveParentPackId(tenantId, request.parentPackCode());
        assertNoInheritanceCycle(tenantId, packCode, request.parentPackCode());

        HrPolicyPack pack = HrPolicyPack.builder()
            .packCode(packCode)
            .countryCode(request.countryCode().toUpperCase())
            .name(request.name())
            .description(request.description())
            .packVersion(nextVersion)
            .status(HrPolicyPackStatus.DRAFT)
            .payload(request.payload())
            .checksum(checksum)
            .parentPackId(parentPackId)
            .parentPackCode(normalizeCode(request.parentPackCode()))
            .regionCode(normalizeRegion(request.regionCode()))
            .inheritanceMode(mapInheritanceMode(request.inheritanceMode()))
            .build();

        pack.setTenantId(tenantId);
        pack.replaceBindings(mapBindings(request.bindings()));
        pack.replaceRuleVersions(mapRuleVersions(request.ruleVersions(), checksum));

        HrPolicyPack saved = policyPackRepository.save(pack);
        return HrPolicyPackMapper.toResponse(saved);
    }

    @Transactional
    public HrPolicyPackResponse updateDraft(String packCode, UpdateHrPolicyPackRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        HrPolicyPack draft = policyPackService.findDraftByPackCode(tenantId, packCode.toUpperCase())
            .orElseThrow(() -> new IllegalStateException("Draft not found for packCode=" + packCode));

        assertJsonPayload(request.payload());
        String checksum = computeChecksum(request.payload());

        draft.updateDraft(request.name(), request.description(), request.payload());
        draft.setChecksum(checksum);

        draft.replaceBindings(mapBindings(request.bindings()));
        draft.replaceRuleVersions(mapRuleVersions(request.ruleVersions(), checksum));
        UUID parentPackId = resolveParentPackId(tenantId, request.parentPackCode());
        assertNoInheritanceCycle(tenantId, draft.getPackCode(), request.parentPackCode());
        draft.updateHierarchy(parentPackId, normalizeCode(request.parentPackCode()), normalizeRegion(request.regionCode()),
            mapInheritanceMode(request.inheritanceMode()));

        HrPolicyPack saved = policyPackRepository.save(draft);
        return HrPolicyPackMapper.toResponse(saved);
    }

    @Transactional
    public ValidateHrPolicyPackResponse validatePayload(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node == null || node.isNull()) {
                return new ValidateHrPolicyPackResponse(false, null, "Payload is empty");
            }
            String checksum = computeChecksum(payload);
            return new ValidateHrPolicyPackResponse(true, checksum, "Payload is valid JSON");
        } catch (Exception ex) {
            log.warn("Invalid policy pack payload: {}", ex.getMessage());
            return new ValidateHrPolicyPackResponse(false, null, "Invalid JSON payload: " + ex.getMessage());
        }
    }

    @Transactional
    public HrPolicyPackResponse publish(String packCode, PublishHrPolicyPackRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        HrPolicyPack draft = policyPackService.findDraftByPackCode(tenantId, packCode.toUpperCase())
            .orElseThrow(() -> new IllegalStateException("Draft not found for packCode=" + packCode));

        Instant effectiveFrom = request.effectiveFrom();
        Instant effectiveTo = request.effectiveTo();
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo must be greater than effectiveFrom");
        }

        // Close existing active pack for same tenant + country
        policyPackService.findActivePack(tenantId, draft.getCountryCode())
            .filter(existing -> !existing.getId().equals(draft.getId()))
            .ifPresent(existing -> {
                existing.markRetired(effectiveFrom);
                policyPackRepository.save(existing);
                recordAudit(existing, HrPolicyPackAction.RETIRE, request.diffSnapshot());
                evictCache(tenantId, existing.getCountryCode());
            });

        draft.markPublished(effectiveFrom, effectiveTo);
        HrPolicyPack saved = policyPackRepository.save(draft);

        recordAudit(saved, HrPolicyPackAction.PUBLISH, request.diffSnapshot());
        evictCache(tenantId, saved.getCountryCode());
        var affectedMappings = countryPackMappingService.updateMappingsForPack(tenantId, saved.getPackCode(), saved.getId());
        affectedMappings.forEach(mapping -> evictCache(tenantId, mapping.getCountryCode()));

        return HrPolicyPackMapper.toResponse(saved);
    }

    @Transactional
    public HrPolicyPackResponse retire(String packCode, RetireHrPolicyPackRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        HrPolicyPack pack = policyPackService.findLatestByPackCode(tenantId, packCode.toUpperCase())
            .filter(existing -> existing.getStatus() == HrPolicyPackStatus.ACTIVE)
            .orElseThrow(() -> new IllegalStateException("Active pack not found for packCode=" + packCode));

        Instant effectiveTo = Optional.ofNullable(request.effectiveTo()).orElse(clock.instant());
        pack.markRetired(effectiveTo);
        HrPolicyPack saved = policyPackRepository.save(pack);

        recordAudit(saved, HrPolicyPackAction.RETIRE, request.diffSnapshot());
        evictCache(tenantId, saved.getCountryCode());

        return HrPolicyPackMapper.toResponse(saved);
    }

    private void recordAudit(HrPolicyPack pack, HrPolicyPackAction action, String diffSnapshot) {
        HrRuleAuditLog logEntry = new HrRuleAuditLog();
        logEntry.setPolicyPackId(pack.getId());
        logEntry.setPackCode(pack.getPackCode());
        logEntry.setCountryCode(pack.getCountryCode());
        logEntry.setPackVersion(pack.getPackVersion());
        logEntry.setAction(action);
        logEntry.setActorId(TenantContext.getCurrentUserId());
        logEntry.setPayloadChecksum(pack.getChecksum());
        logEntry.setDiffSnapshot(buildAuditPayload(pack, diffSnapshot));
        logEntry.setOccurredAt(clock.instant());
        logEntry.setTenantId(pack.getTenantId());
        auditLogRepository.save(logEntry);
    }

    private void evictCache(UUID tenantId, String countryCode) {
        Cache cacheActive = cacheManager.getCache(HrLocalizationCacheNames.ACTIVE_POLICY_PACK);
        if (cacheActive != null) {
            cacheActive.evict(formatCacheKey(tenantId, countryCode));
        }
        Cache cacheResolved = cacheManager.getCache(HrLocalizationCacheNames.RESOLVED_POLICY_PACK);
        if (cacheResolved != null) {
            cacheResolved.evict(formatCacheKey(tenantId, countryCode));
        }
    }

    private List<HrPolicyBinding> mapBindings(List<PolicyBindingRequest> requests) {
        if (CollectionUtils.isEmpty(requests)) {
            return List.of();
        }
        List<HrPolicyBinding> bindings = new ArrayList<>(requests.size());
        for (PolicyBindingRequest request : requests) {
            HrPolicyBinding binding = new HrPolicyBinding();
            binding.setPolicyInterface(request.policyInterface());
            binding.setStrategyBean(request.strategyBean());
            binding.setConfigReference(request.configReference());
            binding.setTenantId(TenantContext.getCurrentTenantId());
            bindings.add(binding);
        }
        return bindings;
    }

    private List<HrRuleVersion> mapRuleVersions(List<RuleVersionRequest> requests, String packChecksum) {
        if (CollectionUtils.isEmpty(requests)) {
            return List.of();
        }
        List<HrRuleVersion> versions = new ArrayList<>(requests.size());
        for (RuleVersionRequest request : requests) {
        assertJsonPayload(request.payload());
            assertJsonPayload(request.payload());
            HrRuleVersion version = new HrRuleVersion();
            version.setRuleType(request.ruleType());
            version.setPayload(request.payload());
            version.setPayloadHash(computeChecksum(request.payload()));
            version.setTenantId(TenantContext.getCurrentTenantId());
            versions.add(version);
        }
        if (versions.stream().noneMatch(version -> version.getPayloadHash().equals(packChecksum))) {
            log.debug("Policy pack checksum does not match any rule version payload hash");
        }
        return versions;
    }

    private void assertJsonPayload(String payload) {
        try {
            objectMapper.readTree(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JSON payload: " + ex.getMessage(), ex);
        }
    }

    private String computeChecksum(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String formatCacheKey(UUID tenantId, String countryCode) {
        return String.format("%s::%s", tenantId, countryCode);
    }

    private UUID resolveParentPackId(UUID tenantId, String parentPackCode) {
        if (parentPackCode == null || parentPackCode.isBlank()) {
            return null;
        }
        return policyPackRepository.findFirstByTenantIdAndPackCodeOrderByPackVersionDesc(tenantId, parentPackCode.toUpperCase())
            .map(HrPolicyPack::getId)
            .orElseThrow(() -> new IllegalArgumentException("Parent pack not found: " + parentPackCode));
    }

    private void assertNoInheritanceCycle(UUID tenantId, String childPackCode, String parentPackCode) {
        if (parentPackCode == null || parentPackCode.isBlank()) {
            return;
        }
        String normalizedChild = childPackCode.toUpperCase();
        String currentParent = parentPackCode.toUpperCase();
        int guard = 0;
        while (currentParent != null) {
            if (normalizedChild.equals(currentParent)) {
                throw new IllegalArgumentException("Inheritance cycle detected for packCode=" + childPackCode);
            }
            String lookupCode = currentParent;
            HrPolicyPack parentPack = policyPackRepository.findFirstByTenantIdAndPackCodeOrderByPackVersionDesc(tenantId, lookupCode)
                .orElseThrow(() -> new IllegalArgumentException("Parent pack not found: " + lookupCode));
            currentParent = normalizeCode(parentPack.getParentPackCode());
            guard++;
            if (guard > 20) {
                throw new IllegalArgumentException("Inheritance chain too deep or cyclical for packCode=" + childPackCode);
            }
        }
    }

    private String normalizeCode(String code) {
        return code != null && !code.isBlank() ? code.toUpperCase() : null;
    }

    private String normalizeRegion(String region) {
        return region != null && !region.isBlank() ? region.toUpperCase() : null;
    }

    private HrInheritanceMode mapInheritanceMode(HrInheritanceModeDto modeDto) {
        if (modeDto == null) {
            return HrInheritanceMode.FULL;
        }
        return switch (modeDto) {
            case FULL -> HrInheritanceMode.FULL;
            case PARTIAL -> HrInheritanceMode.PARTIAL;
        };
    }

    private String buildAuditPayload(HrPolicyPack pack, String diffSnapshot) {
        ObjectNode node = objectMapper.createObjectNode();
        if (diffSnapshot != null && !diffSnapshot.isBlank()) {
            try {
                node.set("diff", objectMapper.readTree(diffSnapshot));
            } catch (Exception ex) {
                node.put("diff_raw", diffSnapshot);
            }
        }
        node.put("parentPackCode", pack.getParentPackCode());
        node.put("inheritanceMode", pack.getInheritanceMode().name());
        node.put("regionCode", pack.getRegionCode());
        ArrayNode lineage = node.putArray("lineage");
        for (String code : computeLineage(pack)) {
            lineage.add(code);
        }
        return node.toString();
    }

    private List<String> computeLineage(HrPolicyPack pack) {
        List<String> lineage = new ArrayList<>();
        lineage.add(pack.getPackCode());
        String current = pack.getParentPackCode();
        UUID tenantId = pack.getTenantId();
        int guard = 0;
        while (current != null && guard < 20) {
            lineage.add(current);
            HrPolicyPack parent = policyPackRepository.findFirstByTenantIdAndPackCodeOrderByPackVersionDesc(tenantId, current)
                .orElse(null);
            current = parent != null ? normalizeCode(parent.getParentPackCode()) : null;
            guard++;
        }
        return lineage;
    }
}

