package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.RequirementProfileVersion;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileInput;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import com.fabricmanagement.sales.salesorder.infra.repository.RequirementProfileVersionRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends immutable order-line requirement profile versions. The resolution itself is {@link
 * RequirementProfileResolver}'s and writes nothing; this service stores a resolved snapshot that
 * differs from the line's current one.
 */
@Service
@RequiredArgsConstructor
public class RequirementProfileService {

  private final RequirementProfileVersionRepository versions;
  private final RequirementProfileResolver resolver;

  @Transactional
  public RequirementProfileSnapshot apply(
      SalesOrderLine line, RequirementProfileInput input, Map<String, Object> residualModuleSpecs) {
    if (line.getId() == null) {
      throw new IllegalArgumentException("Sales order line must be persisted before its profile");
    }
    RequirementProfileSnapshot candidate =
        resolver.resolve(
            RequirementProfileResolver.LineContext.of(line), input, residualModuleSpecs);

    if (candidate.fingerprint().equals(line.getRequirementProfileFingerprint())) {
      return line.getRequirementProfileSnapshot();
    }
    return store(line, candidate);
  }

  /**
   * Stores a profile already resolved elsewhere as the line's next version (the safe edit,
   * CEDIT-03: the profile resolved for a save, or the one a conflict showed). Its content and
   * fingerprint are kept; only its identity and version become the line's. Nothing is written when
   * the line already carries that fingerprint. The basis must belong to the line's product.
   */
  @Transactional
  public RequirementProfileSnapshot applyResolved(
      SalesOrderLine line, RequirementProfileSnapshot resolved) {
    if (line.getId() == null) {
      throw new IllegalArgumentException("Sales order line must be persisted before its profile");
    }
    if (resolved.basis().productId() != null
        && !resolved.basis().productId().equals(line.getProductId())) {
      throw new OrderDomainException("Requirement profile basis product does not match the line");
    }
    if (resolved.fingerprint().equals(line.getRequirementProfileFingerprint())) {
      return line.getRequirementProfileSnapshot();
    }
    UUID profileId =
        line.getRequirementProfileId() == null ? UUID.randomUUID() : line.getRequirementProfileId();
    int nextVersion =
        line.getRequirementProfileVersion() == null
            ? 1
            : Math.addExact(line.getRequirementProfileVersion(), 1);
    return store(line, resolved.withIdentity(profileId, nextVersion));
  }

  private RequirementProfileSnapshot store(
      SalesOrderLine line, RequirementProfileSnapshot candidate) {
    versions.save(
        RequirementProfileVersion.builder()
            .profileId(candidate.profileId())
            .profileVersion(candidate.profileVersion())
            .salesOrderLineId(line.getId())
            .fingerprint(candidate.fingerprint())
            .snapshot(candidate)
            .build());
    line.attachRequirementProfile(candidate);
    return candidate;
  }

  @Transactional(readOnly = true)
  public RequirementProfileSnapshot historyVersion(UUID profileId, int profileVersion) {
    return versions
        .findByTenantIdAndProfileIdAndProfileVersion(
            TenantContext.requireTenantId(), profileId, profileVersion)
        .map(RequirementProfileVersion::getSnapshot)
        .orElseThrow(
            () ->
                new OrderDomainException(
                    "Requirement profile version not found: " + profileId + "/" + profileVersion,
                    404));
  }
}
