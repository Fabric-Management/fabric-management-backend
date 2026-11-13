package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import com.fabricmanagement.platform.auth.dto.PlaygroundImpersonateResponse;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserContact;
import com.fabricmanagement.platform.user.domain.UserDepartment;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PlaygroundService")
class PlaygroundServiceTest {

  private final JwtService jwtService = org.mockito.Mockito.mock(JwtService.class);
  private final UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
  private final OrganizationRepository organizationRepository =
      org.mockito.Mockito.mock(OrganizationRepository.class);
  private final TenantAccessPort tenantAccessPort =
      org.mockito.Mockito.mock(TenantAccessPort.class);

  private final PlaygroundService service =
      new PlaygroundService(jwtService, userRepository, organizationRepository, tenantAccessPort);

  @Test
  @DisplayName("a registered company's persona alias finds its job title")
  void jobTitleForAliasAddresses() {
    assertThat(PlaygroundService.jobTitleFor("owner+weav-mgr@thornbury.example"))
        .contains("Weaving Manager");
    assertThat(PlaygroundService.jobTitleFor("weav.mgr@nexusfabrics.com"))
        .contains("Weaving Manager");
    assertThat(PlaygroundService.jobTitleFor("jane+demo+weav-mgr@thornbury.example"))
        .contains("Weaving Manager");
    assertThat(PlaygroundService.jobTitleFor("owner@thornbury.example")).isEmpty();
    assertThat(PlaygroundService.jobTitleFor(null)).isEmpty();
  }

  @Test
  @DisplayName("impersonate issues a demo-persona token with a normal session lifetime")
  void impersonatePreservesRealSessionTokenModel() {
    UUID tenantId = UUID.randomUUID();
    User user = user(tenantId, "Worker", "One", "worker@example.com");
    when(tenantAccessPort.isDemoMode(tenantId)).thenReturn(true);
    when(userRepository.findByTenantIdAndId(tenantId, user.getId())).thenReturn(Optional.of(user));
    when(jwtService.generateDemoImpersonationAccessToken(user, null, "worker@example.com"))
        .thenReturn("demo-impersonation-token");

    PlaygroundImpersonateResponse response = service.impersonate(tenantId, user.getId(), null);

    assertThat(response.token()).isEqualTo("demo-impersonation-token");
    assertThat(response.userId()).isEqualTo(user.getId());
  }

  @Test
  @DisplayName("impersonate refuses an inactive user of the tenant")
  void impersonateRefusesInactiveUser() {
    UUID tenantId = UUID.randomUUID();
    User user = user(tenantId, "Worker", "One", "worker@example.com");
    user.setIsActive(false);
    when(tenantAccessPort.isDemoMode(tenantId)).thenReturn(true);
    when(userRepository.findByTenantIdAndId(tenantId, user.getId())).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> service.impersonate(tenantId, user.getId(), null))
        .isInstanceOf(PlatformDomainException.class)
        .extracting("errorCode", "httpStatus")
        .containsExactly("IMPERSONATION_TARGET_UNAVAILABLE", 404);

    verify(jwtService, never()).generateDemoImpersonationAccessToken(any(), any(), any());
  }

  @Test
  @DisplayName("impersonate refuses a user outside the tenant")
  void impersonateRefusesUserOutsideTenant() {
    UUID tenantId = UUID.randomUUID();
    when(tenantAccessPort.isDemoMode(tenantId)).thenReturn(true);

    assertThatThrownBy(() -> service.impersonate(tenantId, UUID.randomUUID(), null))
        .isInstanceOf(PlatformDomainException.class)
        .extracting("errorCode", "httpStatus")
        .containsExactly("IMPERSONATION_TARGET_UNAVAILABLE", 404);
  }

  @Test
  @DisplayName("impersonate refuses when tenant is not in demo mode")
  void impersonateRefusesOutsideDemoMode() {
    UUID tenantId = UUID.randomUUID();
    when(tenantAccessPort.isDemoMode(tenantId)).thenReturn(false);

    assertThatThrownBy(() -> service.impersonate(tenantId, UUID.randomUUID(), "guest-1"))
        .isInstanceOf(PlatformDomainException.class)
        .extracting("errorCode", "httpStatus")
        .containsExactly("DEMO_MODE_REQUIRED", 403);

    verify(userRepository, never()).findByTenantIdAndId(any(), any());
  }

  @Test
  @DisplayName("impersonate fails closed when demo mode cannot be resolved")
  void impersonateFailsClosedWhenDemoModeResolutionFails() {
    UUID tenantId = UUID.randomUUID();
    when(tenantAccessPort.isDemoMode(tenantId)).thenThrow(new IllegalStateException("boom"));

    assertThatThrownBy(() -> service.impersonate(tenantId, UUID.randomUUID(), "guest-1"))
        .isInstanceOf(PlatformDomainException.class)
        .extracting("errorCode", "httpStatus")
        .containsExactly("DEMO_MODE_REQUIRED", 403);
  }

  @Test
  @DisplayName("listPersonas succeeds when tenant is in demo mode")
  void listPersonasSucceedsInDemoMode() {
    UUID tenantId = UUID.randomUUID();
    User user = user(tenantId, "Worker", "One", "worker@example.com");
    Organization org = organization(tenantId, user.getOrganizationId());
    when(tenantAccessPort.isDemoMode(tenantId)).thenReturn(true);
    when(userRepository.findByTenantIdWithRelations(tenantId)).thenReturn(List.of(user));
    when(organizationRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(org));

    var personas = service.listPersonas(tenantId);

    assertThat(personas).hasSize(1);
    assertThat(personas.get(0).id()).isEqualTo(user.getId());
  }

  @Test
  @DisplayName("listPersonas refuses when tenant is not in demo mode")
  void listPersonasRefusesOutsideDemoMode() {
    UUID tenantId = UUID.randomUUID();
    when(tenantAccessPort.isDemoMode(tenantId)).thenReturn(false);

    assertThatThrownBy(() -> service.listPersonas(tenantId))
        .isInstanceOf(PlatformDomainException.class)
        .extracting("errorCode", "httpStatus")
        .containsExactly("DEMO_MODE_REQUIRED", 403);

    verify(userRepository, never()).findByTenantIdWithRelations(any());
  }

  private static User user(UUID tenantId, String firstName, String lastName, String email) {
    UUID organizationId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    Role role = Role.create("Worker", "WORKER", "Worker");
    role.setId(UUID.randomUUID());
    role.setTenantId(tenantId);

    Department department =
        Department.create(organizationId, "Production", "PRODUCTION", "Production");
    department.setId(UUID.randomUUID());
    department.setTenantId(tenantId);

    Contact contact =
        Contact.builder()
            .contactType(ContactType.EMAIL)
            .contactValue(email)
            .isVerified(true)
            .build();

    User user = User.create(firstName, lastName, organizationId);
    user.setId(userId);
    user.setTenantId(tenantId);
    user.setUid("TEST-001-USER-" + userId.toString().substring(0, 8));
    user.setRole(role);
    user.getUserContacts()
        .add(UserContact.builder().user(user).contact(contact).isDefault(true).build());
    user.getUserDepartments()
        .add(
            UserDepartment.builder()
                .user(user)
                .userId(userId)
                .department(department)
                .departmentId(department.getId())
                .tenantId(tenantId)
                .isPrimary(true)
                .build());
    return user;
  }

  private static Organization organization(UUID tenantId, UUID organizationId) {
    Organization org =
        Organization.create("Demo Org", "1234567890", OrganizationType.VERTICAL_MILL);
    org.setId(organizationId);
    org.setTenantId(tenantId);
    return org;
  }
}
