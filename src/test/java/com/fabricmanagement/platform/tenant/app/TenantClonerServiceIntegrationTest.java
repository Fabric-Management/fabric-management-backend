package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.costing.integration.AbstractCostingIntegrationTest;
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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TenantClonerServiceIntegrationTest extends AbstractCostingIntegrationTest {

  @Autowired private TenantClonerService tenantClonerService;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private UserRepository userRepository;

  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private DepartmentRepository departmentRepository;
  @Autowired private RoleRepository roleRepository;

  @Test
  @DisplayName("Should successfully clone TEMPLATE tenant with all relations")
  void cloneTemplateToPlayground_clonesAllRelations() {
    // Arrange - verify TEMPLATE exists
    long templateCount = tenantRepository.findByType(TenantType.TEMPLATE).size();
    assertThat(templateCount).as("Precondition: TEMPLATE tenant must exist").isGreaterThan(0);

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
  }
}
