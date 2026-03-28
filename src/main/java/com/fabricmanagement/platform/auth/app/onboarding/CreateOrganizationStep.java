package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 2: Create root Organization for the tenant.
 *
 * <p>Creates the internal organizational structure:
 *
 * <ul>
 *   <li>Root organization with tax ID
 *   <li>Organization type for OS recommendations
 *   <li>Basis for departments and hierarchy
 * </ul>
 *
 * <p>Requires tenantId from CreateTenantStep (Order 1).
 */
@Order(2)
@Component
@RequiredArgsConstructor
@Slf4j
public class CreateOrganizationStep implements OnboardingStep {

  private final OrganizationFacade organizationFacade;

  @Override
  public void execute(OnboardingContext context) {
    log.debug("CreateOrganizationStep: Creating organization for tenant {}", context.getTenantId());

    if (context.getTenantId() == null) {
      throw new PlatformDomainException(
          "Tenant must be created before organization", "AUTH_ONBOARDING_INVALID_STATE", 400);
    }

    // Context already holds OrganizationType directly
    OrganizationType organizationType =
        context.getOrganizationType() != null
            ? context.getOrganizationType()
            : OrganizationType.VERTICAL_MILL;

    OrganizationDto organization =
        organizationFacade.createRootOrganization(
            context.getTenantId(),
            context.getOrganizationName(),
            context.getTaxId(),
            organizationType);

    context.setOrganizationId(organization.getId());
    context.setOrganizationUid(organization.getUid());

    log.debug(
        "CreateOrganizationStep: organizationId={}, organizationUid={}",
        organization.getId(),
        organization.getUid());
  }
}
