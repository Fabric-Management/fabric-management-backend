package com.fabricmanagement.platform.auth.api.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.costing.integration.AbstractCostingIntegrationTest;
import com.jayway.jsonpath.JsonPath;
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
class PlaygroundAuthControllerIntegrationTest extends AbstractCostingIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;

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
}
