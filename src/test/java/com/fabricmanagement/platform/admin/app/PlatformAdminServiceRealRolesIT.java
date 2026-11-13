package com.fabricmanagement.platform.admin.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.testsupport.AbstractFabricAppIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * PLATFORM-BOUNDARY-1: platform staff read another tenant's users and companies under row-level
 * security (the real {@code fabric_app} role). Before the fix the read opened its transaction on
 * the caller's tenant and every list came back empty.
 */
class PlatformAdminServiceRealRolesIT extends AbstractFabricAppIntegrationTest {

  @Autowired private PlatformAdminService service;

  private final List<UUID> createdTenants = new ArrayList<>();

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    // The container is shared with other real-role ITs: leave nothing behind.
    JdbcTemplate owner = owner();
    for (UUID tenantId : createdTenants) {
      owner.update("DELETE FROM common_user.common_user WHERE tenant_id = ?", tenantId);
      owner.update("DELETE FROM common_user.common_role WHERE tenant_id = ?", tenantId);
      owner.update("DELETE FROM common_company.common_organization WHERE tenant_id = ?", tenantId);
      owner.update("DELETE FROM common_tenant.common_tenant WHERE id = ?", tenantId);
    }
    createdTenants.clear();
  }

  @Test
  @DisplayName("platform staff see the target tenant's users and companies, not their own")
  void readsTheTargetTenant() {
    UUID caller = insertTenant("Caller");
    UUID target = insertTenant("Target");
    UUID organisation = insertOrganisation(target, "Ashdown Mills");
    UUID user = insertUser(target, organisation);
    insertUser(caller, insertOrganisation(caller, "Caller Org"));

    // The request runs as the platform staff member's own tenant.
    TenantContext.setCurrentTenantId(caller);

    List<UserDto> users = service.getTenantUsers(target);
    List<OrganizationDto> organisations = service.getTenantOrganizations(target);

    assertThat(users).extracting(UserDto::getId).containsExactly(user);
    assertThat(organisations).extracting(OrganizationDto::getName).containsExactly("Ashdown Mills");
    assertThat(service.getTenantUser(target, user).getId()).isEqualTo(user);
    assertThat(service.getTenantOrganization(target, organisation).getName())
        .isEqualTo("Ashdown Mills");
    assertThat(service.getTenantStatistics(target).getUserCount()).isEqualTo(1L);
    // The caller's tenant is restored afterwards.
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isEqualTo(caller);
  }

  private static JdbcTemplate owner() {
    return new JdbcTemplate(
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
  }

  private UUID insertTenant(String name) {
    UUID id = UUID.randomUUID();
    String suffix = shortId();
    owner()
        .update(
            "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, billing_email,"
                + " status, settings, demo_mode, is_active, created_at, updated_at, version)"
                + " VALUES (?, ?, ?, ?, 'REGULAR', 'admin@example.com', 'ACTIVE', '{}', false,"
                + " true, now(), now(), 0)",
            id,
            "PA-" + suffix,
            "pa-" + suffix.toLowerCase(Locale.ROOT),
            name + " " + suffix);
    createdTenants.add(id);
    return id;
  }

  private static UUID insertOrganisation(UUID tenantId, String name) {
    UUID id = UUID.randomUUID();
    owner()
        .update(
            "INSERT INTO common_company.common_organization (id, tenant_id, uid, name, tax_id,"
                + " organization_type, is_active, created_at, updated_at, version) VALUES (?, ?,"
                + " ?, ?, ?, 'WEAVER', true, now(), now(), 0)",
            id,
            tenantId,
            UUID.randomUUID().toString(),
            name,
            "TAX-" + shortId());
    return id;
  }

  private static UUID insertUser(UUID tenantId, UUID organisationId) {
    UUID roleId = UUID.randomUUID();
    owner()
        .update(
            "INSERT INTO common_user.common_role (id, tenant_id, uid, role_name, role_code,"
                + " role_scope, is_system_role, is_active, created_at, updated_at, version) VALUES"
                + " (?, ?, ?, 'Worker', ?, 'INTERNAL', false, true, now(), now(), 0)",
            roleId,
            tenantId,
            UUID.randomUUID().toString(),
            "ROLE-" + shortId());
    UUID userId = UUID.randomUUID();
    owner()
        .update(
            "INSERT INTO common_user.common_user (id, tenant_id, uid, organization_id, role_id,"
                + " first_name, last_name, user_type, is_active, created_at, updated_at, version)"
                + " VALUES (?, ?, ?, ?, ?, 'Harriet', 'Lowe', 'INTERNAL', true, now(), now(), 0)",
            userId,
            tenantId,
            UUID.randomUUID().toString(),
            organisationId,
            roleId);
    return userId;
  }

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }
}
