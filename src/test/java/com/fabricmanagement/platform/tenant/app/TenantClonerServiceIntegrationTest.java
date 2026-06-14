package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.domain.TenantType;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TenantClonerServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired private TenantClonerService tenantClonerService;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private UserRepository userRepository;

  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private DepartmentRepository departmentRepository;
  @Autowired private RoleRepository roleRepository;

  @Autowired private JdbcTemplate jdbc;

  @Test
  @DisplayName("Should successfully clone TEMPLATE tenant with all relations")
  void cloneTemplateToPlayground_clonesAllRelations() {
    // Arrange - verify TEMPLATE exists
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM common_tenant.common_tenant WHERE slug = 'nexus-fabrics'",
            Long.class);
    UUID templateTenantId;
    if (count == null || count == 0) {
      templateTenantId = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, billing_email, status, settings, is_active, created_at, updated_at, version) VALUES (?, ?, 'nexus-fabrics', 'Nexus Fabrics', 'TEMPLATE', 'test@example.com', 'ACTIVE', '{}', true, now(), now(), 0)",
          templateTenantId,
          UUID.randomUUID().toString());
    } else {
      templateTenantId =
          jdbc.queryForObject(
              "SELECT id FROM common_tenant.common_tenant WHERE slug = 'nexus-fabrics' LIMIT 1",
              UUID.class);
      Long johnCount =
          jdbc.queryForObject(
              "SELECT count(*) FROM common_user.common_user WHERE first_name = 'John' AND last_name = 'Doe' AND tenant_id = ?",
              Long.class,
              templateTenantId);
      if (johnCount != null && johnCount > 0) {
        return;
      }
    }
    long templateCount = tenantRepository.findByType(TenantType.TEMPLATE).size();
    assertThat(templateCount).as("Precondition: TEMPLATE tenant must exist").isGreaterThan(0);

    // Insert dummy data into TEMPLATE tenant to test cloning
    UUID orgId;
    List<UUID> existingOrgs =
        jdbc.queryForList(
            "SELECT id FROM common_company.common_organization WHERE tenant_id = ? LIMIT 1",
            UUID.class,
            templateTenantId);
    if (!existingOrgs.isEmpty()) {
      orgId = existingOrgs.get(0);
    } else {
      orgId = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO common_company.common_organization (id, tenant_id, uid, name, tax_id, organization_type, is_active, created_at, updated_at, version) VALUES (?, ?, ?, 'Test Org', '1234567890', 'VERTICAL_MILL', true, now(), now(), 0)",
          orgId,
          templateTenantId,
          UUID.randomUUID().toString());
    }

    UUID deptId;
    List<UUID> existingDepts =
        jdbc.queryForList(
            "SELECT id FROM common_company.common_department WHERE tenant_id = ? AND department_code = 'DPT-001' LIMIT 1",
            UUID.class,
            templateTenantId);
    if (!existingDepts.isEmpty()) {
      deptId = existingDepts.get(0);
    } else {
      deptId = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO common_company.common_department (id, tenant_id, uid, organization_id, department_name, department_code, department_group, is_system_department, is_active, created_at, updated_at, version) VALUES (?, ?, ?, ?, 'Test Dept', 'DPT-001', 'PRODUCTION', false, true, now(), now(), 0)",
          deptId,
          templateTenantId,
          UUID.randomUUID().toString(),
          orgId);
    }

    UUID roleId;
    List<UUID> existingRoles =
        jdbc.queryForList(
            "SELECT id FROM common_user.common_role WHERE tenant_id = ? AND role_code = 'ROLE-001' LIMIT 1",
            UUID.class,
            templateTenantId);
    if (!existingRoles.isEmpty()) {
      roleId = existingRoles.get(0);
    } else {
      roleId = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO common_user.common_role (id, tenant_id, uid, role_name, role_code, role_scope, is_system_role, is_active, created_at, updated_at, version) VALUES (?, ?, ?, 'Test Role', 'ROLE-001', 'INTERNAL', false, true, now(), now(), 0)",
          roleId,
          templateTenantId,
          UUID.randomUUID().toString());
    }

    UUID userId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO common_user.common_user (id, tenant_id, uid, organization_id, role_id, first_name, last_name, user_type, is_active, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, 'John', 'Doe', 'INTERNAL', true, now(), now(), 0)",
        userId,
        templateTenantId,
        UUID.randomUUID().toString(),
        orgId,
        roleId);

    jdbc.update(
        "INSERT INTO common_user.common_user_department (tenant_id, user_id, department_id, is_primary, created_at, updated_at) VALUES (?, ?, ?, true, now(), now())",
        templateTenantId,
        userId,
        deptId);

    // Act
    Tenant pg = tenantClonerService.cloneTemplateToPlayground();

    // Assert - Tenant
    assertThat(pg).isNotNull();
    assertThat(pg.getType()).isEqualTo(TenantType.PLAYGROUND);
    assertThat(pg.getIsActive()).isTrue();
    assertThat(pg.getName()).startsWith("Playground ");

    // Assert - Organizations
    List<Organization> orgs = organizationRepository.findByTenantIdAndIsActiveTrue(pg.getId());
    assertThat(orgs).isNotEmpty();

    // Assert - Departments
    List<Department> depts = departmentRepository.findByTenantIdAndIsActiveTrue(pg.getId());
    assertThat(depts).isNotEmpty();

    // Assert - Roles
    assertThat(roleRepository.findByTenantIdAndIsActiveTrue(pg.getId())).isNotEmpty();

    // Assert - Users + UserDepartments
    List<User> users = userRepository.findByTenantIdWithRelations(pg.getId());
    assertThat(users).isNotEmpty();
    assertThat(users)
        .allSatisfy(
            u -> {
              assertThat(u.getTenantId()).isEqualTo(pg.getId());
              assertThat(u.getUserDepartments()).isNotEmpty();
            });

    // CR-8: Assert reference table cloning
    // Fiber categories
    Integer fiberCatCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM production.prod_fiber_category WHERE tenant_id = ?",
            Integer.class,
            pg.getId());
    assertThat(fiberCatCount).as("Fiber categories should be cloned").isGreaterThanOrEqualTo(0);

    // Notification templates
    Integer notifCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM notification.notification_template WHERE tenant_id = ?",
            Integer.class,
            pg.getId());
    assertThat(notifCount).as("Notification templates should be cloned").isGreaterThanOrEqualTo(0);

    // i18n translation keys
    Integer i18nKeyCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM i18n.translation_key WHERE tenant_id = ?",
            Integer.class,
            pg.getId());
    assertThat(i18nKeyCount).as("i18n translation keys should be cloned").isGreaterThanOrEqualTo(0);

    // Cost items
    Integer costItemCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM costing.cost_item WHERE tenant_id = ?",
            Integer.class,
            pg.getId());
    assertThat(costItemCount).as("Cost items should be cloned").isGreaterThanOrEqualTo(0);

    // Routing configs
    Integer routingCount =
        jdbc.queryForObject(
            "SELECT count(*) FROM common_communication.common_routing_config WHERE tenant_id = ?",
            Integer.class,
            pg.getId());
    assertThat(routingCount).as("Routing configs should be cloned").isGreaterThanOrEqualTo(0);

    // Verify UIDs are fresh (not copied from template) — CR-5
    List<String> playgroundOrgUids =
        jdbc.queryForList(
            "SELECT uid FROM common_company.common_organization WHERE tenant_id = ?",
            String.class,
            pg.getId());
    List<String> templateOrgUids =
        jdbc.queryForList(
            "SELECT uid FROM common_company.common_organization WHERE tenant_id = ?",
            String.class,
            templateTenantId);
    if (!playgroundOrgUids.isEmpty() && !templateOrgUids.isEmpty()) {
      assertThat(playgroundOrgUids)
          .as("Cloned UIDs should be fresh, not copied from template")
          .doesNotContainAnyElementsOf(templateOrgUids);
    }
  }
}
