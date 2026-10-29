package com.fabricmanagement.platform.subscription.app;

import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.subscription.domain.OSDefinition;
import com.fabricmanagement.platform.subscription.infra.repository.OSDefinitionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates accepted package codes. Saving an onboarding selection never activates billing. */
@Service
@RequiredArgsConstructor
public class EvaluationPackageQueryService {
  private final OSDefinitionRepository definitions;
  private final jakarta.persistence.EntityManager entities;

  public record Catalogue(String version, List<OSDefinition> definitions) {
    public List<String> codes() {
      return definitions.stream().map(OSDefinition::getOsCode).toList();
    }
  }

  @Transactional(readOnly = true)
  public Catalogue catalogue() {
    return describe(definitions.findByIsActiveTrue());
  }

  @Transactional
  public Catalogue lockedCatalogue() {
    // Row locks alone cannot exclude a concurrently inserted newly offered package.
    entities
        .createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock_shared(20261025, 130000)")
        .getSingleResult();
    return describe(definitions.findByIsActiveTrue());
  }

  private Catalogue describe(List<OSDefinition> rows) {
    var eligible =
        rows.stream()
            .filter(
                d ->
                    com.fabricmanagement.common.infrastructure.persistence.TenantContext
                        .SYSTEM_TENANT_ID
                        .equals(d.getTenantId()))
            .filter(
                d ->
                    Boolean.TRUE.equals(d.getIsActive())
                        && d.getDeletedAt() == null
                        && d.isEvaluationAvailable())
            .sorted(java.util.Comparator.comparing(OSDefinition::getOsCode))
            .toList();
    if (eligible.stream().noneMatch(d -> "FabricOS".equals(d.getOsCode()))
        || eligible.stream()
            .anyMatch(d -> d.getDefaultTier() == null || !d.hasTier(d.getDefaultTier()))) {
      throw new PlatformDomainException(
          "Evaluation catalogue is unavailable or has invalid default tiers",
          "ONBOARDING_CATALOGUE_INVALID",
          409);
    }
    try {
      String content =
          eligible.stream()
              .map(d -> d.getOsCode() + ":" + d.getVersion() + ":" + d.getDefaultTier())
              .collect(java.util.stream.Collectors.joining("|"));
      return new Catalogue(
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(content.getBytes(StandardCharsets.UTF_8))),
          eligible);
    } catch (NoSuchAlgorithmException failure) {
      throw new PlatformDomainException(
          "Unable to describe the evaluation catalogue",
          "ONBOARDING_CATALOGUE_ENCODING_FAILED",
          500);
    }
  }

  @Transactional(readOnly = true)
  public void requireActiveSelection(List<String> codes) {
    requireSelection(catalogue(), codes);
  }

  public void requireSelection(Catalogue catalogue, List<String> codes) {
    if (codes == null
        || !codes.contains("FabricOS")
        || new HashSet<>(codes).size() != codes.size()
        || !catalogue.codes().containsAll(codes)) {
      throw new PlatformDomainException(
          "Select active evaluation packages including FabricOS",
          "ONBOARDING_PACKAGES_INVALID",
          422);
    }
  }
}
