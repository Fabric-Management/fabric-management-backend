package com.fabricmanagement.platform.organization.app;

import com.fabricmanagement.common.infrastructure.tenant.TenantNamePort;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationNamingService {

  private static final int MAX_NAME_LENGTH = 255;
  private static final Pattern EDGE_WHITESPACE =
      Pattern.compile("\\A\\s+|(?<!\\s)\\s+\\z", Pattern.UNICODE_CHARACTER_CLASS);

  private final OrganizationRepository organizations;
  private final TenantNamePort tenantNames;

  /** The caller holds the organisation write lock before this method can lock the tenant row. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void rename(Organization organization, String name) {
    organization.setName(normalizeDisplayName(name));
    synchronizeRootName(organization);
  }

  /** A partial profile save can re-sync a populated root without renaming it. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void synchronizeExistingRootName(Organization organization) {
    if (!trimName(organization.getName()).isEmpty()) {
      synchronizeRootName(organization);
    }
  }

  private void synchronizeRootName(Organization organization) {
    if (!organization.isCompanyRoot()) {
      return;
    }
    var tenantId = organization.getTenantId();
    if (organizations.countCompanyRoots(tenantId) == 1) {
      tenantNames.rename(tenantId, organization.getName());
    } else {
      log.warn("Cannot synchronise display name without a single root: tenantId={}", tenantId);
    }
  }

  /** Canonical display name for both profile writes and accepted onboarding evidence. */
  public static String normalizeDisplayName(String name) {
    String displayName = trimName(name);
    if (displayName.isEmpty()
        || displayName.codePointCount(0, displayName.length()) > MAX_NAME_LENGTH) {
      throw new PlatformDomainException(
          "Organisation name must contain 1 to 255 characters after trimming",
          "ORG_INVALID_NAME",
          400);
    }
    if (displayName.indexOf(0) >= 0) {
      throw new PlatformDomainException(
          "Organisation name must not contain a null character", "ORG_INVALID_NAME", 400);
    }
    return displayName;
  }

  private static String trimName(String name) {
    return name == null ? "" : EDGE_WHITESPACE.matcher(name).replaceAll("");
  }
}
