package com.fabricmanagement.human.compliance.localization.app;

import com.fabricmanagement.human.compliance.localization.domain.HrInheritanceMode;
import com.fabricmanagement.human.compliance.localization.domain.HrLocalizationCacheNames;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPack;
import com.fabricmanagement.human.compliance.localization.domain.HrPolicyPackStatus;
import com.fabricmanagement.human.compliance.localization.infra.repository.HrPolicyPackRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class HrPolicyPackResolver {

  private static final String GLOBAL_BASE_PACK = "GLOBAL-BASE";

  private final HrPolicyPackService policyPackService;
  private final HrPolicyPackRepository policyPackRepository;
  private final HrCountryPackMappingService countryPackMappingService;
  private final ObjectMapper objectMapper;

  @Cacheable(
      cacheNames = HrLocalizationCacheNames.RESOLVED_POLICY_PACK,
      key = "T(java.lang.String).format('%s::%s', #tenantId, #countryCode)")
  public Optional<ResolvedPolicyPack> resolve(UUID tenantId, String countryCode) {
    HrPolicyPack pack =
        resolveBasePack(tenantId, countryCode)
            .orElseGet(() -> resolveBasePack(tenantId, GLOBAL_BASE_PACK).orElse(null));
    if (pack == null) {
      return Optional.empty();
    }
    try {
      ResolvedPayload resolvedPayload = resolvePayload(tenantId, pack);
      return Optional.of(
          new ResolvedPolicyPack(
              pack.getPackCode(),
              pack.getPackVersion(),
              resolvedPayload.payload(),
              resolvedPayload.lineageCodes()));
    } catch (Exception ex) {
      log.error(
          "Failed to resolve policy pack hierarchy: tenantId={}, countryCode={}, packCode={}",
          tenantId,
          countryCode,
          pack.getPackCode(),
          ex);
      throw new IllegalStateException(
          "Failed to resolve policy pack hierarchy: " + ex.getMessage(), ex);
    }
  }

  @Cacheable(
      cacheNames = HrLocalizationCacheNames.RESOLVED_POLICY_PACK,
      key =
          "T(java.lang.String).format('%s::PACK::%s::%s', #tenantId, #pack.getPackCode(), #pack.getPackVersion())")
  public ResolvedPolicyPack resolve(UUID tenantId, HrPolicyPack pack) {
    try {
      ResolvedPayload resolved = resolvePayload(tenantId, pack);
      return new ResolvedPolicyPack(
          pack.getPackCode(), pack.getPackVersion(), resolved.payload(), resolved.lineageCodes());
    } catch (Exception ex) {
      log.error("Failed to resolve policy pack hierarchy for packCode={}", pack.getPackCode(), ex);
      throw new IllegalStateException(
          "Failed to resolve policy pack hierarchy: " + ex.getMessage(), ex);
    }
  }

  private Optional<HrPolicyPack> resolveBasePack(UUID tenantId, String countryCode) {
    if (countryCode == null || countryCode.isBlank()) {
      return Optional.empty();
    }
    String normalizedCountry = countryCode.toUpperCase(Locale.ROOT);
    Optional<HrPolicyPack> directPack =
        policyPackService.findActivePack(tenantId, normalizedCountry);
    if (directPack.isPresent()) {
      return directPack;
    }

    Optional<String> mappedPackCode =
        countryPackMappingService
            .findMapping(tenantId, normalizedCountry)
            .map(mapping -> mapping.getPackCode().toUpperCase(Locale.ROOT));

    if (mappedPackCode.isPresent()) {
      return policyPackRepository.findByPackCodeAndStatus(
          tenantId, mappedPackCode.get(), HrPolicyPackStatus.ACTIVE);
    }

    return Optional.empty();
  }

  private ResolvedPayload resolvePayload(UUID tenantId, HrPolicyPack basePack) throws Exception {
    List<HrPolicyPack> lineage = new ArrayList<>();
    HrPolicyPack current = basePack;
    int guard = 0;
    while (current != null && guard < 20) {
      lineage.add(0, current); // prepend to maintain parent-first order
      if (current.getParentPackCode() == null) {
        break;
      }
      current =
          policyPackRepository
              .findFirstByTenantIdAndPackCodeOrderByPackVersionDesc(
                  tenantId, current.getParentPackCode())
              .orElse(null);
      guard++;
    }

    JsonNode resolvedNode = null;
    List<String> lineageCodes = new ArrayList<>();
    for (HrPolicyPack pack : lineage) {
      lineageCodes.add(pack.getPackCode());
      JsonNode packNode =
          pack.getPayload() != null && !pack.getPayload().isBlank()
              ? objectMapper.readTree(pack.getPayload())
              : null;

      if (resolvedNode == null) {
        resolvedNode = packNode;
        continue;
      }

      if (pack.getInheritanceMode() == HrInheritanceMode.FULL) {
        resolvedNode = packNode != null ? packNode : resolvedNode;
      } else {
        if (packNode != null && resolvedNode != null) {
          resolvedNode = deepMerge((ObjectNode) resolvedNode.deepCopy(), packNode);
        }
      }
    }

    String payload = resolvedNode != null ? objectMapper.writeValueAsString(resolvedNode) : "{}";
    return new ResolvedPayload(payload, lineageCodes);
  }

  private ObjectNode deepMerge(ObjectNode parent, JsonNode child) {
    if (child == null) {
      return parent;
    }
    child
        .fields()
        .forEachRemaining(
            entry -> {
              String fieldName = entry.getKey();
              JsonNode childValue = entry.getValue();
              if (parent.has(fieldName)
                  && parent.get(fieldName).isObject()
                  && childValue.isObject()) {
                deepMerge((ObjectNode) parent.get(fieldName), childValue);
              } else {
                parent.set(fieldName, childValue);
              }
            });
    return parent;
  }

  private record ResolvedPayload(String payload, List<String> lineageCodes) {}
}
