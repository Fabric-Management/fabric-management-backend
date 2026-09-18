package com.fabricmanagement.platform.user.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.flowboard.routing.domain.port.out.EffectivePermissionUserQueryPort;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionOverride;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserDepartment;
import com.fabricmanagement.platform.user.infra.repository.PermissionOverrideRepository;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserDepartmentRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.testsupport.PostgresImage;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
class EffectivePermissionUserQueryIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      PostgresImage.container()
          .withDatabaseName("effective_permission_users_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  @Autowired private EffectivePermissionUserQueryPort effectivePermissionUserQueryPort;
  @Autowired private PermissionEvaluator permissionEvaluator;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private DepartmentRepository departmentRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private UserDepartmentRepository userDepartmentRepository;
  @Autowired private PermissionTemplateRepository templateRepository;
  @Autowired private PermissionOverrideRepository overrideRepository;

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void reverseQueryUsesFreshEffectivePermissionsAndStrictTenantActiveBoundaries() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    TenantFixture tenantA = tenant("A" + suffix);
    Role lineageRole = role(tenantA, "LINEAGE-" + suffix);
    Role globalRole = role(tenantA, "GLOBAL-" + suffix);
    Role noGrantRole = role(tenantA, "NO-GRANT-" + suffix);
    Role adminRole = role(tenantA, "ADMIN");
    Role platformAdminRole = role(tenantA, "PLATFORM_ADMIN");

    // Codes are deliberately mixed case: the identity must carry the stored spelling, because
    // department-lineage resolution matches department_code exactly. Uppercasing it once in the
    // identity silently dropped every inherited department grant.
    Department parent = department(tenantA, "Parent-" + suffix, null);
    Department child = department(tenantA, "child-" + suffix, parent);
    grantTemplate(tenantA, lineageRole, parent.getDepartmentCode());
    grantTemplate(tenantA, globalRole, null);

    User lineageGrant = user(tenantA, lineageRole, "Lineage", child);
    User departmentless = user(tenantA, globalRole, "Departmentless", null);
    User grantingOverride = user(tenantA, noGrantRole, "GrantOverride", null);
    override(tenantA, grantingOverride, DataScope.OWN);
    User revokingOverride = user(tenantA, globalRole, "RevokeOverride", null);
    assertThat(
            permissionEvaluator
                .evaluate(
                    tenantA.id(), globalRole.getRoleCode(), List.of(), revokingOverride.getId())
                .can("sales", "write"))
        .isTrue();
    override(tenantA, revokingOverride, null);
    User admin = user(tenantA, adminRole, "Admin", null);
    User platformAdmin = user(tenantA, platformAdminRole, "PlatformAdmin", null);
    User inactive = user(tenantA, globalRole, "Inactive", null);
    inactive.delete();
    userRepository.saveAndFlush(inactive);

    TenantFixture tenantB = tenant("B" + suffix);
    Role tenantBRole = role(tenantB, "GLOBAL-" + suffix);
    grantTemplate(tenantB, tenantBRole, null);
    User tenantBHolder = user(tenantB, tenantBRole, "TenantB", null);

    TenantContext.setCurrentTenantId(tenantA.id());
    Set<UUID> result =
        effectivePermissionUserQueryPort.findUsersWithAction(
            tenantA.id(), PermissionKey.SALES_WRITE);

    assertThat(result)
        .containsExactlyInAnyOrder(
            lineageGrant.getId(),
            departmentless.getId(),
            grantingOverride.getId(),
            admin.getId(),
            platformAdmin.getId())
        .doesNotContain(revokingOverride.getId(), inactive.getId(), tenantBHolder.getId());
  }

  private TenantFixture tenant(String suffix) {
    Tenant tenant = tenantRepository.save(Tenant.create("Reverse " + suffix, "RV-" + suffix));
    TenantContext.setCurrentTenantId(tenant.getId());
    Organization organization =
        organizationRepository.save(
            Organization.create(
                "Reverse org " + suffix, "RVT-" + suffix, OrganizationType.SPINNER));
    return new TenantFixture(tenant.getId(), organization);
  }

  private Role role(TenantFixture fixture, String code) {
    TenantContext.setCurrentTenantId(fixture.id());
    return roleRepository.save(Role.create("Role " + code, code, "Reverse query test"));
  }

  private Department department(TenantFixture fixture, String code, Department parentDepartment) {
    TenantContext.setCurrentTenantId(fixture.id());
    Department department =
        Department.create(fixture.organization().getId(), code, code, "Reverse query test");
    department.setParentDepartment(parentDepartment);
    return departmentRepository.save(department);
  }

  private User user(TenantFixture fixture, Role role, String firstName, Department department) {
    TenantContext.setCurrentTenantId(fixture.id());
    User user = User.create(firstName, "User", fixture.organization().getId());
    user.setRole(role);
    user = userRepository.saveAndFlush(user);
    if (department != null) {
      userDepartmentRepository.saveAndFlush(
          UserDepartment.create(user, department, true, user.getId()));
    }
    return user;
  }

  private void grantTemplate(TenantFixture fixture, Role role, String departmentCode) {
    TenantContext.setCurrentTenantId(fixture.id());
    templateRepository.saveAndFlush(
        PermissionTemplate.builder()
            .roleCode(role.getRoleCode())
            .departmentCode(departmentCode)
            .resource("sales")
            .action("write")
            .dataScope(DataScope.GLOBAL)
            .build());
  }

  private void override(TenantFixture fixture, User user, DataScope scope) {
    TenantContext.setCurrentTenantId(fixture.id());
    overrideRepository.saveAndFlush(
        PermissionOverride.builder()
            .userId(user.getId())
            .resource("sales")
            .action("write")
            .dataScope(scope)
            .reason("Reverse query test")
            .grantedBy(user.getId())
            .build());
  }

  private record TenantFixture(UUID id, Organization organization) {}
}
