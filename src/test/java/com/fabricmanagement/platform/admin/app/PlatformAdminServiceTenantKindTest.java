package com.fabricmanagement.platform.admin.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.admin.dto.PlatformTenantGroup;
import com.fabricmanagement.platform.admin.dto.PlatformTenantKind;
import com.fabricmanagement.platform.admin.dto.PlatformTenantSummary;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.subscription.infra.repository.SubscriptionRepository;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import com.fabricmanagement.platform.tenant.domain.TenantType;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.user.domain.port.EmployeeProjectionPort;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/** PLATFORM-BOUNDARY-1: the backend says what each tenant is; the screen only shows it. */
class PlatformAdminServiceTenantKindTest {

  private final TenantSystemService tenants = mock(TenantSystemService.class);
  private final PlatformAdminService service =
      new PlatformAdminService(
          tenants,
          mock(OrganizationRepository.class),
          mock(UserRepository.class),
          mock(SubscriptionRepository.class),
          mock(EmployeeProjectionPort.class),
          mock(PlatformTransactionManager.class));

  @Test
  @DisplayName("each tenant gets its kind and screen section")
  void classifiesEveryKind() {
    assertThat(kind(TenantContext.SYSTEM_TENANT_ID, TenantType.REGULAR, "Longward"))
        .isEqualTo(PlatformTenantKind.PLATFORM_OWNER);
    assertThat(kind(TenantContext.TEMPLATE_TENANT_ID, TenantType.TEMPLATE, "Golden Template"))
        .isEqualTo(PlatformTenantKind.ONBOARDING_TEMPLATE);
    assertThat(kind(UUID.randomUUID(), TenantType.TEMPLATE, "Nexus Fabrics"))
        .isEqualTo(PlatformTenantKind.SAMPLE_COMPANY);
    assertThat(kind(UUID.randomUUID(), TenantType.PLAYGROUND, "Playground ab12"))
        .isEqualTo(PlatformTenantKind.PLAYGROUND_COPY);
    assertThat(kind(UUID.randomUUID(), TenantType.REGULAR, "Thornbury Weaving"))
        .isEqualTo(PlatformTenantKind.CUSTOMER);

    assertThat(PlatformTenantKind.PLATFORM_OWNER.group()).isEqualTo(PlatformTenantGroup.PLATFORM);
    assertThat(PlatformTenantKind.ONBOARDING_TEMPLATE.group())
        .isEqualTo(PlatformTenantGroup.PLATFORM);
    assertThat(PlatformTenantKind.SAMPLE_COMPANY.group()).isEqualTo(PlatformTenantGroup.PLATFORM);
    assertThat(PlatformTenantKind.PLAYGROUND_COPY.group())
        .isEqualTo(PlatformTenantGroup.PLAYGROUND);
    assertThat(PlatformTenantKind.CUSTOMER.group()).isEqualTo(PlatformTenantGroup.CUSTOMER);
  }

  @Test
  @DisplayName("the list puts customers first, then Playground copies, then platform tenants")
  void listsCustomersFirst() {
    when(tenants.getAllActive())
        .thenReturn(
            List.of(
                tenant(TenantContext.SYSTEM_TENANT_ID, TenantType.REGULAR, "Longward"),
                tenant(UUID.randomUUID(), TenantType.PLAYGROUND, "Playground ab12"),
                tenant(UUID.randomUUID(), TenantType.REGULAR, "thornbury Weaving"),
                tenant(UUID.randomUUID(), TenantType.REGULAR, "Ashdown Mills")));

    List<PlatformTenantSummary> listed = service.listTenants();

    assertThat(listed)
        .extracting(PlatformTenantSummary::name)
        .containsExactly("Ashdown Mills", "thornbury Weaving", "Playground ab12", "Longward");
    assertThat(listed.get(0).group()).isEqualTo(PlatformTenantGroup.CUSTOMER);
    assertThat(listed.get(3).kind()).isEqualTo(PlatformTenantKind.PLATFORM_OWNER);
  }

  private static PlatformTenantKind kind(UUID id, TenantType type, String name) {
    return PlatformAdminService.classify(tenant(id, type, name));
  }

  private static TenantDto tenant(UUID id, TenantType type, String name) {
    return TenantDto.builder()
        .id(id)
        .uid("T-" + name.length())
        .name(name)
        .type(type)
        .status(TenantStatus.ACTIVE)
        .isActive(true)
        .build();
  }
}
