package com.fabricmanagement.platform.organization.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantReportingCurrencyAdapterTest {

  @Mock private OrganizationRepository organizationRepo;

  @InjectMocks private TenantReportingCurrencyAdapter adapter;

  private final UUID tenantId = UUID.randomUUID();

  @Test
  void getReportingCurrency_OrgExistsAndCurrencySet_ShouldReturnCurrency() {
    Organization org = new Organization();
    org.setReportingCurrency("USD");

    when(organizationRepo.findRootOrganization(tenantId, OrganizationType.EXTERNAL_PARTNER))
        .thenReturn(Optional.of(org));

    String result = adapter.getReportingCurrency(tenantId);

    assertThat(result).isEqualTo("USD");
  }

  @Test
  void getReportingCurrency_OrgExistsButCurrencyNull_ShouldReturnFallbackTRY() {
    Organization org = new Organization();
    org.setReportingCurrency(null);

    when(organizationRepo.findRootOrganization(tenantId, OrganizationType.EXTERNAL_PARTNER))
        .thenReturn(Optional.of(org));

    String result = adapter.getReportingCurrency(tenantId);

    assertThat(result).isEqualTo("TRY");
  }

  @Test
  void getReportingCurrency_OrgNotFound_ShouldReturnFallbackTRY() {
    when(organizationRepo.findRootOrganization(tenantId, OrganizationType.EXTERNAL_PARTNER))
        .thenReturn(Optional.empty());

    String result = adapter.getReportingCurrency(tenantId);

    assertThat(result).isEqualTo("TRY");
  }

  // Note: Testing @Cacheable explicitly in a pure Mockito unit test is not feasible
  // without a running Spring context or AOP proxy setup. The annotation's logic
  // is verified in integration tests.
}
