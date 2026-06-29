package com.fabricmanagement.platform.auth.api.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.AuthCookieSupport;
import com.fabricmanagement.platform.auth.app.JwtService;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserContact;
import com.fabricmanagement.platform.user.domain.UserDepartment;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class PlaygroundAuthControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JwtService jwtService;

  @BeforeEach
  void setUpTemplateTenant() {
    UUID templateTenantId;

    // Check if nexus-fabrics exists first
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM common_tenant.common_tenant WHERE slug = 'nexus-fabrics'",
            Long.class);
    if (count != null && count > 0) {
      templateTenantId =
          jdbc.queryForObject(
              "SELECT id FROM common_tenant.common_tenant WHERE slug = 'nexus-fabrics' LIMIT 1",
              UUID.class);

      // If Jane Smith already exists, we have already seeded for this test class
      Long janeCount =
          jdbc.queryForObject(
              "SELECT count(*) FROM common_user.common_user WHERE first_name = 'Jane' AND tenant_id = ?",
              Long.class,
              templateTenantId);
      if (janeCount != null && janeCount > 0) {
        return;
      }
    } else {
      templateTenantId = UUID.fromString("00000000-0000-0000-ffff-000000000002");
      jdbc.update(
          "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, billing_email, status, settings, is_active, created_at, updated_at, version) VALUES (?, ?, 'nexus-fabrics', 'Nexus Fabrics', 'TEMPLATE', 'test@example.com', 'ACTIVE', '{}', true, now(), now(), 0)",
          templateTenantId,
          UUID.randomUUID().toString());
    }

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

    UUID userId1 = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO common_user.common_user (id, tenant_id, uid, organization_id, role_id, first_name, last_name, user_type, is_active, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, 'John', 'Doe', 'INTERNAL', true, now(), now(), 0)",
        userId1,
        templateTenantId,
        UUID.randomUUID().toString(),
        orgId,
        roleId);

    UUID userId2 = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO common_user.common_user (id, tenant_id, uid, organization_id, role_id, first_name, last_name, user_type, is_active, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, 'Jane', 'Smith', 'INTERNAL', true, now(), now(), 0)",
        userId2,
        templateTenantId,
        UUID.randomUUID().toString(),
        orgId,
        roleId);
  }

  @Test
  @DisplayName("Full Playground flow: Init -> Impersonate")
  void playgroundFlowTest() throws Exception {
    // 1. Initialize playground
    MvcResult initResult =
        mockMvc
            .perform(
                post("/api/v1/playground/init")
                    .param("guestId", "integration-test-guest")
                    .with(
                        request -> {
                          request.setRemoteAddr("10.99.99.10");
                          return request;
                        }))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists())
            .andExpect(jsonPath("$.tenantId").exists())
            .andExpect(jsonPath("$.organizationType").exists())
            .andReturn();

    String responseStr = initResult.getResponse().getContentAsString();
    String token = JsonPath.read(responseStr, "$.token");

    // 2. Fetch Personas (using the token we just got)
    MvcResult personasResult =
        mockMvc
            .perform(get("/api/v1/playground/personas").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
            .andExpect(jsonPath("$[0].userType").exists())
            .andExpect(jsonPath("$[0].organizationName").exists())
            .andReturn();

    String personasStr = personasResult.getResponse().getContentAsString();
    String targetUserId = JsonPath.read(personasStr, "$[1].id");

    // 3. Impersonate another user
    mockMvc
        .perform(
            post("/api/v1/playground/impersonate/" + targetUserId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").exists())
        .andExpect(jsonPath("$.userId").value(targetUserId));
  }

  @Test
  @DisplayName("Should return 429 when initializing too frequently from same IP")
  void shouldReturn429OnRateLimit() throws Exception {
    mockMvc.perform(
        post("/api/v1/playground/init")
            .param("guestId", "guest-rate-1")
            .with(
                request -> {
                  request.setRemoteAddr("10.99.99.20");
                  return request;
                }));
    mockMvc
        .perform(
            post("/api/v1/playground/init")
                .param("guestId", "guest-rate-2")
                .with(
                    request -> {
                      request.setRemoteAddr("10.99.99.20");
                      return request;
                    }))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  @DisplayName("Should return 401 when impersonating with invalid token")
  void shouldReturn401ForInvalidToken() throws Exception {
    // Generate a regular (non-playground) token - For this integration test we can't easily
    // inject a fake token unless we use JwtService. We will just use an invalid token or missing
    // token.
    mockMvc
        .perform(
            post("/api/v1/playground/impersonate/" + UUID.randomUUID())
                .header("Authorization", "Bearer invalid-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Authenticated demo session can list personas and impersonate")
  void authenticatedDemoSessionCanUsePersonasAndImpersonate() throws Exception {
    DemoTenantFixture fixture = createDemoTenantFixture(true, "TRIAL");
    String token = jwtService.generateAccessToken(fixture.owner());

    mockMvc
        .perform(get("/api/v1/playground/personas").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));

    mockMvc
        .perform(
            post("/api/v1/playground/impersonate/" + fixture.worker().getId())
                .header("Authorization", "Bearer " + token)
                .cookie(new Cookie(AuthCookieSupport.REFRESH_TOKEN_COOKIE_NAME, "refresh-token")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").exists())
        .andExpect(jsonPath("$.userId").value(fixture.worker().getId().toString()))
        .andExpect(cookie().value(AuthCookieSupport.REFRESH_TOKEN_COOKIE_NAME, "refresh-token"));
  }

  @Test
  @DisplayName("Anonymous playground impersonation keeps access-only cookie behavior")
  void anonymousPlaygroundImpersonationKeepsAccessOnlyCookieBehavior() throws Exception {
    DemoTenantFixture fixture = createDemoTenantFixture(true, "TRIAL");
    String token =
        jwtService.generatePlaygroundAccessToken(fixture.owner(), "guest-1", "owner@example.com");

    mockMvc
        .perform(
            post("/api/v1/playground/impersonate/" + fixture.worker().getId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(cookie().exists("access_token"))
        .andExpect(cookie().doesNotExist(AuthCookieSupport.REFRESH_TOKEN_COOKIE_NAME));
  }

  @Test
  @DisplayName("Authenticated non-demo ACTIVE tenant is refused with DEMO_MODE_REQUIRED")
  void authenticatedActiveTenantCannotImpersonate() throws Exception {
    DemoTenantFixture fixture = createDemoTenantFixture(false, "ACTIVE");
    String token = jwtService.generateAccessToken(fixture.owner());

    mockMvc
        .perform(
            post("/api/v1/playground/impersonate/" + fixture.worker().getId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DEMO_MODE_REQUIRED"));

    mockMvc
        .perform(get("/api/v1/playground/personas").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DEMO_MODE_REQUIRED"));
  }

  @Test
  @DisplayName("ACTIVE and EXPIRED tenants cannot impersonate even with playground token claims")
  void activeAndExpiredTenantsCannotImpersonateEvenWithPlaygroundClaims() throws Exception {
    assertImpersonationRefusedForStatus("ACTIVE");
    assertImpersonationRefusedForStatus("EXPIRED");
  }

  @Test
  @DisplayName("Unauthenticated playground requests return 401")
  void unauthenticatedPlaygroundRequestsReturn401() throws Exception {
    mockMvc.perform(get("/api/v1/playground/personas")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(post("/api/v1/playground/impersonate/" + UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }

  private void assertImpersonationRefusedForStatus(String status) throws Exception {
    DemoTenantFixture fixture = createDemoTenantFixture(false, status);
    String token =
        jwtService.generatePlaygroundAccessToken(fixture.owner(), "guest-1", "owner@example.com");

    mockMvc
        .perform(
            post("/api/v1/playground/impersonate/" + fixture.worker().getId())
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("DEMO_MODE_REQUIRED"));
  }

  private DemoTenantFixture createDemoTenantFixture(boolean demoMode, String tenantStatus) {
    String unique = UUID.randomUUID().toString();
    UUID tenantId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    UUID ownerRoleId = UUID.randomUUID();
    UUID workerRoleId = UUID.randomUUID();
    UUID departmentId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID workerId = UUID.randomUUID();

    jdbc.update(
        "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, billing_email, status, settings, demo_mode, is_active, created_at, updated_at, version) VALUES (?, ?, ?, 'Demo Tenant', 'REGULAR', 'demo@example.com', ?, '{}', ?, true, now(), now(), 0)",
        tenantId,
        "TEN-" + unique.substring(0, 8),
        "demo-" + unique,
        tenantStatus,
        demoMode);
    jdbc.update(
        "INSERT INTO common_company.common_organization (id, tenant_id, uid, name, tax_id, organization_type, is_active, created_at, updated_at, version) VALUES (?, ?, ?, 'Demo Org', ?, 'VERTICAL_MILL', true, now(), now(), 0)",
        orgId,
        tenantId,
        "ORG-" + unique.substring(0, 8),
        "TAX-" + unique.substring(0, 8));
    jdbc.update(
        "INSERT INTO common_user.common_role (id, tenant_id, uid, role_name, role_code, role_scope, is_system_role, is_active, created_at, updated_at, version) VALUES (?, ?, ?, 'Owner', 'ADMIN', 'INTERNAL', false, true, now(), now(), 0)",
        ownerRoleId,
        tenantId,
        "ROLE-O-" + unique.substring(0, 8));
    jdbc.update(
        "INSERT INTO common_user.common_role (id, tenant_id, uid, role_name, role_code, role_scope, is_system_role, is_active, created_at, updated_at, version) VALUES (?, ?, ?, 'Worker', 'WORKER', 'INTERNAL', false, true, now(), now(), 0)",
        workerRoleId,
        tenantId,
        "ROLE-W-" + unique.substring(0, 8));
    jdbc.update(
        "INSERT INTO common_company.common_department (id, tenant_id, uid, organization_id, department_name, department_code, description, is_active, created_at, updated_at, version) VALUES (?, ?, ?, ?, 'Production', 'PRODUCTION', 'Production', true, now(), now(), 0)",
        departmentId,
        tenantId,
        "DEPT-" + unique.substring(0, 8),
        orgId);

    insertUserWithContact(
        tenantId, orgId, ownerRoleId, departmentId, ownerId, "Owner", "User", "owner-" + unique);
    insertUserWithContact(
        tenantId,
        orgId,
        workerRoleId,
        departmentId,
        workerId,
        "Worker",
        "User",
        "worker-" + unique);

    User owner = tokenUser(tenantId, orgId, ownerId, ownerRoleId, "ADMIN", "owner-" + unique);
    User worker = tokenUser(tenantId, orgId, workerId, workerRoleId, "WORKER", "worker-" + unique);
    return new DemoTenantFixture(owner, worker);
  }

  private void insertUserWithContact(
      UUID tenantId,
      UUID orgId,
      UUID roleId,
      UUID departmentId,
      UUID userId,
      String firstName,
      String lastName,
      String emailPrefix) {
    UUID contactId = UUID.randomUUID();
    String email = emailPrefix + "@example.com";

    jdbc.update(
        "INSERT INTO common_user.common_user (id, tenant_id, uid, organization_id, role_id, first_name, last_name, user_type, is_active, created_at, updated_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, 'INTERNAL', true, now(), now(), 0)",
        userId,
        tenantId,
        "USER-" + userId.toString().substring(0, 8),
        orgId,
        roleId,
        firstName,
        lastName);
    jdbc.update(
        "INSERT INTO common_communication.common_contact (id, tenant_id, uid, contact_value, contact_type, is_verified, is_active, created_at, updated_at, version) VALUES (?, ?, ?, ?, 'EMAIL', true, true, now(), now(), 0)",
        contactId,
        tenantId,
        "CONTACT-" + contactId.toString().substring(0, 8),
        email);
    jdbc.update(
        "INSERT INTO common_user.common_user_contact (user_id, contact_id, tenant_id, uid, is_default, is_active, created_at, updated_at, version) VALUES (?, ?, ?, ?, true, true, now(), now(), 0)",
        userId,
        contactId,
        tenantId,
        "UCONTACT-" + contactId.toString().substring(0, 8));
    jdbc.update(
        "INSERT INTO common_user.common_user_department (user_id, department_id, tenant_id, is_primary, is_active, assigned_at, created_at, updated_at) VALUES (?, ?, ?, true, true, now(), now(), now())",
        userId,
        departmentId,
        tenantId);
  }

  private User tokenUser(
      UUID tenantId, UUID orgId, UUID userId, UUID roleId, String roleCode, String emailPrefix) {
    Role role = Role.create(roleCode, roleCode, roleCode);
    role.setId(roleId);
    role.setTenantId(tenantId);

    Department department = Department.create(orgId, "Production", "PRODUCTION", "Production");
    department.setId(UUID.randomUUID());
    department.setTenantId(tenantId);

    Contact contact =
        Contact.builder()
            .contactType(ContactType.EMAIL)
            .contactValue(emailPrefix + "@example.com")
            .isVerified(true)
            .build();

    User user = User.create(roleCode, "User", orgId);
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
    return user;
  }

  private record DemoTenantFixture(User owner, User worker) {}
}
