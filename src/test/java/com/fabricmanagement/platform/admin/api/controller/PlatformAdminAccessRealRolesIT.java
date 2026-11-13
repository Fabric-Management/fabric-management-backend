package com.fabricmanagement.platform.admin.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.app.JwtService;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserContact;
import com.fabricmanagement.platform.user.domain.UserDepartment;
import com.fabricmanagement.testsupport.AbstractFabricAppIntegrationTest;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PLATFORM-BOUNDARY-1: platform authority is the {@code PLATFORM_ADMIN} role held in the platform
 * tenant, never the role code alone. This drives the real endpoints with real tokens, so it covers
 * the whole path: token, filter chain, URL rule and method rule.
 *
 * <p>Before the fix a Playground persona carried the role code and was let in. The control test
 * (platform staff get through) is what keeps the refusal honest: if the shared set-up were wrong,
 * both would fail, so a 403 here can only be the authority rule.
 */
@AutoConfigureMockMvc
@DisplayName("Platform admin endpoints: who is let in")
class PlatformAdminAccessRealRolesIT extends AbstractFabricAppIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;

  private UUID customerTenant;

  @AfterEach
  void removeWhatTheTestWrote() {
    TenantContext.clear();
    if (customerTenant != null) {
      owner().update("DELETE FROM common_tenant.common_tenant WHERE id = ?", customerTenant);
      customerTenant = null;
    }
  }

  @Test
  @DisplayName("a customer tenant's user holding a role coded PLATFORM_ADMIN is refused")
  void customerTenantUserWithThePlatformRoleCodeIsRefused() throws Exception {
    customerTenant = insertTenant();
    String token = tokenFor(customerTenant, "PLATFORM_ADMIN");

    mockMvc
        .perform(get("/api/v1/admin/tenants").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/admin/tenants/" + customerTenant)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("control: the platform role held in the platform tenant is let in")
  void platformStaffAreLetIn() throws Exception {
    String token = tokenFor(TenantContext.SYSTEM_TENANT_ID, "PLATFORM_ADMIN");

    mockMvc
        .perform(get("/api/v1/admin/tenants").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("no token: refused as unauthenticated, not let through")
  void noTokenIsUnauthorised() throws Exception {
    mockMvc.perform(get("/api/v1/admin/tenants")).andExpect(status().isUnauthorized());
  }

  /** A real signed access token for a user of the tenant who holds the role. */
  private String tokenFor(UUID tenantId, String roleCode) {
    UUID organisationId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID roleId = UUID.randomUUID();

    Role role = Role.create(roleCode, roleCode, roleCode);
    role.setId(roleId);
    role.setTenantId(tenantId);

    Department department = Department.create(organisationId, "Production", "PRODUCTION", "x");
    department.setId(UUID.randomUUID());
    department.setTenantId(tenantId);

    Contact contact =
        Contact.builder()
            .contactType(ContactType.EMAIL)
            .contactValue("access-" + shortId().toLowerCase(Locale.ROOT) + "@example.com")
            .isVerified(true)
            .build();

    User user = User.create(roleCode, "User", organisationId);
    user.setId(userId);
    user.setTenantId(tenantId);
    user.setUid("USER-" + userId.toString().substring(0, 8));
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
    return jwtService.generateAccessToken(user);
  }

  private static JdbcTemplate owner() {
    return new JdbcTemplate(
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
  }

  private static UUID insertTenant() {
    UUID id = UUID.randomUUID();
    String suffix = shortId();
    owner()
        .update(
            "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, billing_email,"
                + " status, settings, demo_mode, is_active, created_at, updated_at, version)"
                + " VALUES (?, ?, ?, ?, 'REGULAR', 'admin@example.com', 'ACTIVE', '{}', false,"
                + " true, now(), now(), 0)",
            id,
            "PX-" + suffix,
            "px-" + suffix.toLowerCase(Locale.ROOT),
            "Customer " + suffix);
    return id;
  }

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }
}
