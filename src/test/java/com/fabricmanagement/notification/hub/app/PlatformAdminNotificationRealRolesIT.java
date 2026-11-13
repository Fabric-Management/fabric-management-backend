package com.fabricmanagement.notification.hub.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.notification.hub.domain.port.PlatformAdminRecipientPort;
import com.fabricmanagement.testsupport.AbstractFabricAppIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * NOTIF-UNIFY-1 B4 on the production roles: a platform-level notification (a fibre request) is
 * raised in a customer's tenant and must reach the platform administrators, who live in the
 * platform's own tenant.
 *
 * <p>The superuser base cannot tell whether the lookup saw them: it ignores row-level security. A
 * fibre request from a Playground tenant once reached nobody, because the administrator's role had
 * been moved out of the platform tenant and the policy hid it. The test is the automated stand-in
 * for sending a fibre request in the running application and looking at the queue.
 *
 * <p>The container is shared with the other tests on this base, so everything written here is
 * removed afterwards.
 */
@DisplayName("Platform-admin notifications on the runtime roles")
class PlatformAdminNotificationRealRolesIT extends AbstractFabricAppIntegrationTest {

  private static final UUID PLATFORM = TenantContext.SYSTEM_TENANT_ID;
  private static final String EVENT = NotificationEventType.FIBER_REQUEST_SUBMITTED;

  @Autowired private TenantNotificationDispatcher dispatcher;
  @Autowired private PlatformAdminRecipientPort platformAdmins;

  private final List<UUID> createdTenants = new ArrayList<>();
  private final List<UUID> createdOrganisations = new ArrayList<>();
  private final List<UUID> createdUsers = new ArrayList<>();

  @AfterEach
  void removeWhatTheTestWrote() {
    TenantContext.clear();
    JdbcTemplate owner = owner();
    for (UUID user : createdUsers) {
      owner.update("DELETE FROM notification.notification_queue WHERE recipient_id = ?", user);
      owner.update("DELETE FROM common_user.common_user WHERE id = ?", user);
    }
    for (UUID organisation : createdOrganisations) {
      owner.update("DELETE FROM common_company.common_organization WHERE id = ?", organisation);
    }
    for (UUID tenantId : createdTenants) {
      owner.update("DELETE FROM common_user.common_role WHERE tenant_id = ?", tenantId);
      owner.update("DELETE FROM common_tenant.common_tenant WHERE id = ?", tenantId);
    }
    createdUsers.clear();
    createdOrganisations.clear();
    createdTenants.clear();
  }

  @Test
  @DisplayName("a request raised in a customer tenant is queued for the platform administrator")
  void reachesThePlatformAdministrator() {
    UUID customer = insertTenant();
    UUID administrator = insertPlatformAdministrator();

    raiseFibreRequestFrom(customer);

    assertThat(queuedChannels(administrator))
        .as("queue rows for the platform administrator, written in the platform tenant")
        .contains("IN_APP");
    assertThat(TenantContext.getCurrentTenantIdOrNull())
        .as("the customer's tenant is restored for the rest of the caller's work")
        .isEqualTo(customer);
  }

  @Test
  @DisplayName("the platform role code in a customer tenant is not a platform administrator")
  void aCustomerTenantsRoleOfTheSameNameIsNotNotified() {
    UUID customer = insertTenant();
    UUID customerOrganisation = insertOrganisation(customer);
    UUID impostor =
        insertUser(customer, customerOrganisation, insertRole(customer, "PLATFORM_ADMIN"));
    UUID administrator = insertPlatformAdministrator();

    assertThat(platformAdmins.findPlatformAdministrators())
        .contains(administrator)
        .doesNotContain(impostor);

    raiseFibreRequestFrom(customer);

    assertThat(queuedChannels(administrator)).isNotEmpty();
    assertThat(queuedChannels(impostor)).isEmpty();
  }

  private void raiseFibreRequestFrom(UUID customerTenant) {
    // The event is raised in the customer's tenant, so the listener thread is bound to it.
    TenantContext.setCurrentTenantId(customerTenant);
    dispatcher.notifyInTenant(
        PLATFORM,
        platformAdmins::findPlatformAdministrators,
        EVENT,
        Map.of("tenantName", "Ashdown Mills", "fiberName", "Modal", "isoCode", "CMD"),
        UUID.randomUUID(),
        "FIBER_REQUEST");
  }

  private List<String> queuedChannels(UUID recipient) {
    return owner()
        .queryForList(
            "SELECT channel FROM notification.notification_queue"
                + " WHERE tenant_id = ? AND recipient_id = ? AND event_type = ?",
            String.class,
            PLATFORM,
            recipient,
            EVENT);
  }

  private static JdbcTemplate owner() {
    return new JdbcTemplate(
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
  }

  /** The role the platform tenant is seeded with; absent means the platform has no staff role. */
  private static UUID platformAdminRoleId() {
    return owner()
        .queryForObject(
            "SELECT id FROM common_user.common_role WHERE tenant_id = ? AND role_code ="
                + " 'PLATFORM_ADMIN'",
            UUID.class,
            PLATFORM);
  }

  /** A user of the platform tenant holding its seeded {@code PLATFORM_ADMIN} role. */
  private UUID insertPlatformAdministrator() {
    return insertUser(PLATFORM, insertOrganisation(PLATFORM), platformAdminRoleId());
  }

  private UUID insertTenant() {
    UUID id = UUID.randomUUID();
    String suffix = shortId();
    owner()
        .update(
            "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, billing_email,"
                + " status, settings, demo_mode, is_active, created_at, updated_at, version)"
                + " VALUES (?, ?, ?, ?, 'REGULAR', 'admin@example.com', 'ACTIVE', '{}', false,"
                + " true, now(), now(), 0)",
            id,
            "PN-" + suffix,
            "pn-" + suffix.toLowerCase(Locale.ROOT),
            "Customer " + suffix);
    createdTenants.add(id);
    return id;
  }

  private UUID insertOrganisation(UUID tenantId) {
    UUID id = UUID.randomUUID();
    owner()
        .update(
            "INSERT INTO common_company.common_organization (id, tenant_id, uid, name, tax_id,"
                + " organization_type, is_active, created_at, updated_at, version) VALUES (?, ?,"
                + " ?, 'Test Org', ?, 'WEAVER', true, now(), now(), 0)",
            id,
            tenantId,
            UUID.randomUUID().toString(),
            "TAX-" + shortId());
    createdOrganisations.add(id);
    return id;
  }

  private UUID insertRole(UUID tenantId, String roleCode) {
    UUID id = UUID.randomUUID();
    owner()
        .update(
            "INSERT INTO common_user.common_role (id, tenant_id, uid, role_name, role_code,"
                + " role_scope, is_system_role, is_active, created_at, updated_at, version)"
                + " VALUES (?, ?, ?, ?, ?, 'INTERNAL', false, true, now(), now(), 0)",
            id,
            tenantId,
            UUID.randomUUID().toString(),
            roleCode,
            roleCode);
    return id;
  }

  private UUID insertUser(UUID tenantId, UUID organisationId, UUID roleId) {
    UUID id = UUID.randomUUID();
    owner()
        .update(
            "INSERT INTO common_user.common_user (id, tenant_id, uid, organization_id, role_id,"
                + " first_name, last_name, user_type, is_active, created_at, updated_at, version)"
                + " VALUES (?, ?, ?, ?, ?, 'Harriet', 'Lowe', 'INTERNAL', true, now(), now(), 0)",
            id,
            tenantId,
            UUID.randomUUID().toString(),
            organisationId,
            roleId);
    createdUsers.add(id);
    return id;
  }

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
  }
}
