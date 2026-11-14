package com.fabricmanagement.platform.organization.app;

import com.fabricmanagement.common.infrastructure.tenant.TenantNamePort;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationNamingService {

  private final OrganizationRepository organizations;
  private final TenantNamePort tenantNames;

  /** The caller holds the organisation write lock before this method can lock the tenant row. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void rename(Organization organization, String name) {
    organization.setName(name);
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
}
