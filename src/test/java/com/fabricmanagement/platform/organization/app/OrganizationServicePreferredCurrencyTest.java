package com.fabricmanagement.platform.organization.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.dto.CreateOrganizationRequest;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationServicePreferredCurrencyTest {

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID ORG_ID = UUID.randomUUID();

  @Mock private OrganizationRepository organizationRepository;
  @Mock private DomainEventPublisher eventPublisher;

  @InjectMocks private OrganizationService organizationService;

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void createOrganizationNormalizesPreferredCurrencyAndMapsItToDto() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    CreateOrganizationRequest request =
        CreateOrganizationRequest.builder()
            .name("Nexus Fabrics")
            .taxId("1234567890")
            .organizationType(OrganizationType.VERTICAL_MILL)
            .preferredCurrency("gbp")
            .build();
    when(organizationRepository.save(any(Organization.class)))
        .thenAnswer(
            invocation -> {
              Organization organization = invocation.getArgument(0);
              organization.setId(ORG_ID);
              organization.setTenantId(TENANT_ID);
              return organization;
            });

    OrganizationDto result = organizationService.createOrganization(request);

    assertThat(result.getPreferredCurrency()).isEqualTo("GBP");
  }

  @Test
  void updateOrganizationClearsPreferredCurrencyWhenBlank() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    Organization organization =
        Organization.builder()
            .name("Nexus Fabrics")
            .taxId("1234567890")
            .organizationType(OrganizationType.VERTICAL_MILL)
            .preferredCurrency("USD")
            .build();
    organization.setId(ORG_ID);
    organization.setTenantId(TENANT_ID);
    when(organizationRepository.findByTenantIdAndId(TENANT_ID, ORG_ID))
        .thenReturn(Optional.of(organization));
    when(organizationRepository.save(any(Organization.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    OrganizationDto result =
        organizationService.updateOrganization(ORG_ID, "Nexus Fabrics", "1234567890", null, " ");

    assertThat(result.getPreferredCurrency()).isNull();
    assertThat(organization.getPreferredCurrency()).isNull();
  }

  @Test
  void updateOrganizationRejectsUnknownPreferredCurrency() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    Organization organization =
        Organization.builder()
            .name("Nexus Fabrics")
            .taxId("1234567890")
            .organizationType(OrganizationType.VERTICAL_MILL)
            .build();
    organization.setId(ORG_ID);
    organization.setTenantId(TENANT_ID);
    when(organizationRepository.findByTenantIdAndId(TENANT_ID, ORG_ID))
        .thenReturn(Optional.of(organization));

    assertThatThrownBy(
            () ->
                organizationService.updateOrganization(
                    ORG_ID, "Nexus Fabrics", "1234567890", null, "ZZZ"))
        .isInstanceOf(PlatformDomainException.class)
        .satisfies(
            ex -> {
              PlatformDomainException platformEx = (PlatformDomainException) ex;
              assertThat(platformEx.getHttpStatus()).isEqualTo(422);
              assertThat(platformEx.getErrorCode()).isEqualTo("ORG_INVALID_PREFERRED_CURRENCY");
            });
  }
}
