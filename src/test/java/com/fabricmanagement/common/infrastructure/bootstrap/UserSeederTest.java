package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.bootstrap.UserSeeder.PersonaSubset;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.app.IdentityProvisioningService;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.communication.infra.repository.ContactRepository;
import com.fabricmanagement.platform.organization.app.OrganizationService;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.user.app.RoleService;
import com.fabricmanagement.platform.user.app.UserCreationService;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.dto.CreateInternalUserRequest;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class UserSeederTest {

  private final TenantSystemService tenantService = Mockito.mock(TenantSystemService.class);
  private final OrganizationService organizationService = Mockito.mock(OrganizationService.class);
  private final UserCreationService userCreationService = Mockito.mock(UserCreationService.class);
  private final UserRepository userRepository = Mockito.mock(UserRepository.class);
  private final RoleService roleService = Mockito.mock(RoleService.class);
  private final DepartmentRepository departmentRepository =
      Mockito.mock(DepartmentRepository.class);
  private final AuthUserRepository authUserRepository = Mockito.mock(AuthUserRepository.class);
  private final PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
  private final TransactionTemplate transactionTemplate = Mockito.mock(TransactionTemplate.class);
  private final ContactRepository contactRepository = Mockito.mock(ContactRepository.class);
  private final IdentityProvisioningService identityProvisioningService =
      Mockito.mock(IdentityProvisioningService.class);

  private final UserSeeder userSeeder =
      new UserSeeder(
          tenantService,
          organizationService,
          userCreationService,
          userRepository,
          roleService,
          departmentRepository,
          authUserRepository,
          passwordEncoder,
          transactionTemplate,
          contactRepository,
          identityProvisioningService);

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void shouldSeedRepresentativePersonasWithOwnerAliasEmails() {
    UUID tenantId = UUID.randomUUID();
    UUID organizationId = UUID.randomUUID();
    stubTransactionTemplate();
    // An integrated mill: the core personas plus every production area (14).
    when(organizationService.getRootOrganization())
        .thenReturn(
            Optional.of(
                OrganizationDto.builder()
                    .id(organizationId)
                    .organizationType(OrganizationType.VERTICAL_MILL)
                    .build()));
    when(userRepository.existsByTenantIdAndContactValue(Mockito.eq(tenantId), anyString()))
        .thenReturn(false);
    when(roleService.findByCode(anyString())).thenAnswer(invocation -> Optional.of(role()));
    when(departmentRepository.findByTenantIdAndOrganizationIdAndDepartmentCode(
            Mockito.eq(tenantId), Mockito.eq(organizationId), anyString()))
        .thenAnswer(invocation -> Optional.of(department(organizationId)));
    when(userCreationService.createInternalUser(any(CreateInternalUserRequest.class)))
        .thenAnswer(invocation -> UserDto.builder().id(UUID.randomUUID()).build());
    when(userRepository.findByTenantIdAndId(Mockito.eq(tenantId), ArgumentMatchers.any(UUID.class)))
        .thenAnswer(
            invocation -> {
              UUID userId = invocation.getArgument(1);
              User user = User.create("Seed", "Persona", organizationId);
              user.setId(userId);
              user.setTenantId(tenantId);
              return Optional.of(user);
            });
    when(passwordEncoder.encode(anyString())).thenReturn("encoded");
    when(contactRepository.findByTenantIdAndContactValue(Mockito.eq(tenantId), anyString()))
        .thenReturn(Optional.empty());

    int seeded =
        userSeeder.seedFor(tenantId, "Owner@AcmeTextiles.com", PersonaSubset.REPRESENTATIVE);

    ArgumentCaptor<CreateInternalUserRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateInternalUserRequest.class);
    verify(userCreationService, Mockito.times(14)).createInternalUser(requestCaptor.capture());
    verify(authUserRepository, Mockito.times(14)).save(any());
    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository, Mockito.times(14)).save(userCaptor.capture());
    List<String> contactValues =
        requestCaptor.getAllValues().stream()
            .map(CreateInternalUserRequest::getContactValue)
            .toList();
    assertThat(seeded).isEqualTo(14);
    assertThat(contactValues)
        .hasSize(14)
        .allSatisfy(
            email -> {
              assertThat(email).startsWith("owner+");
              assertThat(email).endsWith("@acmetextiles.com");
              assertThat(email).doesNotContain("nexusfabrics.com");
            })
        .contains("owner+spin-mgr@acmetextiles.com", "owner+sales-rep@acmetextiles.com");
    assertThat(requestCaptor.getAllValues())
        .allSatisfy(request -> assertThat(request.isInvitationEmailSuppressed()).isTrue());
    assertThat(userCaptor.getAllValues())
        .hasSize(14)
        .allSatisfy(user -> assertThat(user.isDemoSeed()).isTrue());
  }

  @Test
  void shouldSkipExistingRepresentativePersonas() {
    UUID tenantId = UUID.randomUUID();
    UUID organizationId = UUID.randomUUID();
    stubTransactionTemplate();
    when(organizationService.getRootOrganization())
        .thenReturn(Optional.of(OrganizationDto.builder().id(organizationId).build()));
    when(userRepository.existsByTenantIdAndContactValue(Mockito.eq(tenantId), anyString()))
        .thenReturn(true);

    int seeded = userSeeder.seedFor(tenantId, "owner@example.com", PersonaSubset.REPRESENTATIVE);

    assertThat(seeded).isZero();
    verify(userCreationService, never()).createInternalUser(any());
    verify(authUserRepository, never()).save(any());
  }

  @Test
  void aWeaverGetsTheCorePersonasAndItsOwnProductionArea() {
    // PLATFORM-BOUNDARY-1: personas follow the company's own work, nothing it does not do.
    List<String> weaver = UserSeeder.personaEmailsFor(OrganizationType.WEAVER);

    assertThat(weaver)
        .hasSize(8)
        .startsWith(UserSeeder.CORE_PERSONA_EMAILS.toArray(String[]::new))
        .contains("weav.mgr@nexusfabrics.com", "weaver@nexusfabrics.com")
        .doesNotContain(
            "spin.mgr@nexusfabrics.com", "knit.mgr@nexusfabrics.com", "dye.mgr@nexusfabrics.com");
  }

  @Test
  void anIntegratedMillGetsEveryProductionArea() {
    assertThat(UserSeeder.personaEmailsFor(OrganizationType.VERTICAL_MILL))
        .hasSize(14)
        .contains(
            "spin.op@nexusfabrics.com",
            "weaver@nexusfabrics.com",
            "knitter@nexusfabrics.com",
            "jet.op@nexusfabrics.com");
    assertThat(UserSeeder.personaEmailsFor(null)).hasSize(14);
  }

  @Test
  void aGarmentMakerGetsTheCorePersonasOnly() {
    assertThat(UserSeeder.personaEmailsFor(OrganizationType.GARMENT_MANUFACTURER))
        .containsExactlyElementsOf(UserSeeder.CORE_PERSONA_EMAILS);
  }

  @Test
  void theCoreKeepsThePersonasTheDemoDataIsRecordedAs() {
    // The sales demo data is Sandra Deal's work and the purchasing demo data Yolanda Bidwell's.
    assertThat(UserSeeder.CORE_PERSONA_EMAILS)
        .contains("sales.rep@nexusfabrics.com", "yarn.buyer@nexusfabrics.com");
    for (OrganizationType type : OrganizationType.values()) {
      assertThat(UserSeeder.personaEmailsFor(type))
          .as("%s", type)
          .doesNotHaveDuplicates()
          .containsAll(UserSeeder.CORE_PERSONA_EMAILS);
    }
  }

  @Test
  void seedsTheWeaverSetForAWeavingCompany() {
    UUID tenantId = UUID.randomUUID();
    UUID organizationId = UUID.randomUUID();
    stubTransactionTemplate();
    when(organizationService.getRootOrganization())
        .thenReturn(
            Optional.of(
                OrganizationDto.builder()
                    .id(organizationId)
                    .organizationType(OrganizationType.WEAVER)
                    .build()));
    when(userRepository.existsByTenantIdAndContactValue(Mockito.eq(tenantId), anyString()))
        .thenReturn(false);
    when(roleService.findByCode(anyString())).thenAnswer(invocation -> Optional.of(role()));
    when(departmentRepository.findByTenantIdAndOrganizationIdAndDepartmentCode(
            Mockito.eq(tenantId), Mockito.eq(organizationId), anyString()))
        .thenAnswer(invocation -> Optional.of(department(organizationId)));
    when(userCreationService.createInternalUser(any(CreateInternalUserRequest.class)))
        .thenAnswer(invocation -> UserDto.builder().id(UUID.randomUUID()).build());
    when(userRepository.findByTenantIdAndId(Mockito.eq(tenantId), ArgumentMatchers.any(UUID.class)))
        .thenAnswer(
            invocation -> {
              User user = User.create("Seed", "Persona", organizationId);
              user.setId(invocation.getArgument(1));
              user.setTenantId(tenantId);
              return Optional.of(user);
            });
    when(passwordEncoder.encode(anyString())).thenReturn("encoded");
    when(contactRepository.findByTenantIdAndContactValue(Mockito.eq(tenantId), anyString()))
        .thenReturn(Optional.empty());

    int seeded = userSeeder.seedFor(tenantId, "owner@example.com", PersonaSubset.REPRESENTATIVE);

    ArgumentCaptor<CreateInternalUserRequest> requests =
        ArgumentCaptor.forClass(CreateInternalUserRequest.class);
    verify(userCreationService, Mockito.times(8)).createInternalUser(requests.capture());
    assertThat(seeded).isEqualTo(8);
    assertThat(requests.getAllValues())
        .extracting(CreateInternalUserRequest::getContactValue)
        .contains("owner+weav-mgr@example.com", "owner+sales-rep@example.com")
        .doesNotContain("owner+spin-mgr@example.com");
  }

  private void stubTransactionTemplate() {
    when(transactionTemplate.execute(Mockito.<TransactionCallback<Integer>>any()))
        .thenAnswer(
            invocation -> {
              TransactionCallback<Integer> callback = invocation.getArgument(0);
              return callback.doInTransaction(null);
            });
  }

  private Role role() {
    Role role = Role.create("Manager", "MANAGER", "Manager");
    role.setId(UUID.randomUUID());
    return role;
  }

  private Department department(UUID organizationId) {
    Department department = Department.create(organizationId, "Department", "DEPT", "Department");
    department.setId(UUID.randomUUID());
    return department;
  }
}
