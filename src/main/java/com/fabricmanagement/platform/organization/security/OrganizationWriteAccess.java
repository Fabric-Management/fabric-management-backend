package com.fabricmanagement.platform.organization.security;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContextResolver;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.PlatformAuthority;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tradingpartner.app.PartnerOrganizationQueryService;
import com.fabricmanagement.platform.tradingpartner.security.PartnerContactDetailsAccess;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.security.PermissionIdentityReader;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrganizationWriteAccess {

  private final OrganizationRepository organizations;
  private final AuthenticatedUserContextResolver contexts;
  private final PermissionIdentityReader identities;
  private final PermissionEvaluator permissions;
  private final PartnerOrganizationQueryService partners;
  private final PartnerContactDetailsAccess partnerAccess;

  public void requireConfigure() {
    var tenantId = TenantContext.requireTenantId();
    var context =
        contexts
            .resolve(SecurityContextHolder.getContext().getAuthentication())
            .filter(actor -> tenantId.equals(actor.tenantId()))
            .orElseThrow(OrganizationWriteAccess::denied);
    var identity =
        identities.find(tenantId, context.userId()).orElseThrow(OrganizationWriteAccess::denied);
    var scope =
        permissions
            .evaluateFresh(
                tenantId,
                PlatformAuthority.effectiveRoleCode(
                    identity.roleCode(),
                    tenantId,
                    context.isPlayground(),
                    context.demoImpersonation()),
                identity.departmentCodes(),
                context.userId())
            .scopeOf("organization", "configure");
    if (scope == null || scope.compareTo(DataScope.ORGANIZATION) < 0) {
      throw denied();
    }
  }

  public void requireInternalType(OrganizationType type) {
    if (type == OrganizationType.EXTERNAL_PARTNER) {
      throw new PlatformDomainException(
          "Use the trading partner routes to manage this organisation",
          "ORG_PARTNER_REQUIRES_TRADING_PARTNER_ROUTE",
          403);
    }
  }

  public void requireContactDetails(UUID organizationId) {
    UUID tenantId = TenantContext.requireTenantId();
    Organization organisation =
        organizations
            .findByTenantIdAndId(tenantId, organizationId)
            .filter(
                found -> Boolean.TRUE.equals(found.getIsActive()) && found.getDeletedAt() == null)
            .orElseThrow(
                () -> new PlatformDomainException("Organisation not found", "ORG_NOT_FOUND", 404));
    if (organisation.getOrganizationType() != OrganizationType.EXTERNAL_PARTNER) {
      requireConfigure();
      return;
    }
    var capability = partnerContactCapability(organizationId);
    if (!capability.allowed()) {
      throw new PlatformDomainException(
          "Authority to edit this partner's contact details is required", capability.reason(), 403);
    }
  }

  public PartnerContactDetailsAccess.Capability partnerContactCapability(UUID organizationId) {
    UUID tenantId = TenantContext.requireTenantId();
    if (organizationId == null
        || organizations
            .findByTenantIdAndId(tenantId, organizationId)
            .filter(org -> Boolean.TRUE.equals(org.getIsActive()) && org.getDeletedAt() == null)
            .filter(org -> org.getOrganizationType() == OrganizationType.EXTERNAL_PARTNER)
            .isEmpty()) {
      return partnerAccess.capability(null);
    }
    return partnerAccess.capability(
        partners.activePartnerType(tenantId, organizationId).orElse(null));
  }

  private static PlatformDomainException denied() {
    return new PlatformDomainException(
        "Organisation-wide company configuration authority is required",
        "ORGANIZATION_CONFIGURE_REQUIRED",
        403);
  }
}
