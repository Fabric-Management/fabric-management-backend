package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
class TenantResetServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantResetService tenantResetService;

  @MockBean private NotificationService notificationService;
  @MockBean private EmailTemplateRenderer emailTemplateRenderer;

  @Test
  @DisplayName("Reset demo restores registered demo seed while preserving tenant, owner, and clock")
  void resetRegisteredDemo_restoresSeedAndKeepsDemoMode() throws Exception {
    doNothing()
        .when(notificationService)
        .sendNotificationSync(anyString(), anyString(), anyString());
    when(emailTemplateRenderer.renderSetupPassword(
            anyString(), anyString(), anyString(), anyString()))
        .thenReturn("Setup password email body");
    when(emailTemplateRenderer.renderWelcome(anyString(), anyString(), anyString(), anyString()))
        .thenReturn("Welcome email body");

    long suffix = System.currentTimeMillis();
    String ownerEmail = "reset-owner-" + suffix + "@example.com";
    String ownerAliasPattern = "reset-owner-" + suffix + "+%@example.com";
    String organizationName = "Reset Demo Company " + suffix;
    String body =
        """
        {
          "organizationName": "%s",
          "taxId": "RESET%s",
          "organizationType": "VERTICAL_MILL",
          "firstName": "Reset",
          "lastName": "Owner",
          "email": "%s",
          "acceptedTerms": true
        }
        """
            .formatted(organizationName, suffix % 100000000, ownerEmail);

    mockMvc
        .perform(
            post("/api/v1/public/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    UUID tenantId =
        jdbc.queryForObject(
            "SELECT id FROM common_tenant.common_tenant WHERE name = ?",
            UUID.class,
            organizationName);
    UUID ownerId = findUserIdByContact(ownerEmail);
    UUID realUserId = insertRealUser(tenantId);
    Timestamp trialStartedAt = tenantTimestamp(tenantId, "trial_started_at");
    Timestamp trialEndsAt = tenantTimestamp(tenantId, "trial_ends_at");
    UUID mutatedSeedUserId =
        jdbc.queryForObject(
            "SELECT id FROM common_user.common_user WHERE tenant_id = ? AND demo_seed = true LIMIT 1",
            UUID.class,
            tenantId);
    jdbc.update(
        "UPDATE common_user.common_user SET first_name = 'MutatedSeed' WHERE id = ?",
        mutatedSeedUserId);

    tenantResetService.reset(
        new AuthenticatedUserContext(ownerId, "ADMIN", List.of(), null, tenantId));

    assertThat(booleanTenantValue(tenantId, "demo_mode")).isTrue();
    assertThat(tenantTimestamp(tenantId, "trial_started_at")).isEqualTo(trialStartedAt);
    assertThat(tenantTimestamp(tenantId, "trial_ends_at")).isEqualTo(trialEndsAt);
    assertThat(userExists(ownerId)).isTrue();
    assertThat(userExists(realUserId)).isTrue();
    assertThat(countSeedUsers(tenantId)).isGreaterThan(0);
    assertThat(countUsersByFirstName(tenantId, "MutatedSeed")).isZero();
    assertThat(countAliasUsers(ownerAliasPattern, true)).isGreaterThan(0);
    assertThat(countAliasUsers(ownerAliasPattern, false)).isZero();
    assertThat(countRows("common_company.common_trading_partner", tenantId)).isGreaterThan(0);
  }

  private UUID findUserIdByContact(String contactValue) {
    return jdbc.queryForObject(
        """
        SELECT u.id
        FROM common_user.common_user u
        JOIN common_user.common_user_contact uc ON uc.user_id = u.id
        JOIN common_communication.common_contact c ON c.id = uc.contact_id
        WHERE lower(c.contact_value) = lower(?)
        """,
        UUID.class,
        contactValue);
  }

  private UUID insertRealUser(UUID tenantId) {
    UUID organizationId =
        jdbc.queryForObject(
            "SELECT id FROM common_company.common_organization WHERE tenant_id = ? LIMIT 1",
            UUID.class,
            tenantId);
    UUID roleId =
        jdbc.queryForObject(
            "SELECT id FROM common_user.common_role WHERE tenant_id = ? AND role_code = 'WORKER' LIMIT 1",
            UUID.class,
            tenantId);
    UUID userId = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO common_user.common_user
          (id, tenant_id, uid, organization_id, role_id, first_name, last_name, user_type,
           is_active, demo_seed, created_at, updated_at, version)
        VALUES (?, ?, ?, ?, ?, 'Real', 'User', 'INTERNAL', true, false, now(), now(), 0)
        """,
        userId,
        tenantId,
        "REAL-" + UUID.randomUUID(),
        organizationId,
        roleId);
    return userId;
  }

  private Timestamp tenantTimestamp(UUID tenantId, String column) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM common_tenant.common_tenant WHERE id = ?",
        Timestamp.class,
        tenantId);
  }

  private Boolean booleanTenantValue(UUID tenantId, String column) {
    return jdbc.queryForObject(
        "SELECT " + column + " FROM common_tenant.common_tenant WHERE id = ?",
        Boolean.class,
        tenantId);
  }

  private boolean userExists(UUID userId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM common_user.common_user WHERE id = ?", Integer.class, userId);
    return count != null && count == 1;
  }

  private Integer countSeedUsers(UUID tenantId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM common_user.common_user WHERE tenant_id = ? AND demo_seed = true",
        Integer.class,
        tenantId);
  }

  private Integer countUsersByFirstName(UUID tenantId, String firstName) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM common_user.common_user WHERE tenant_id = ? AND first_name = ?",
        Integer.class,
        tenantId,
        firstName);
  }

  private Integer countAliasUsers(String aliasPattern, boolean demoSeed) {
    return jdbc.queryForObject(
        """
        SELECT count(*)
        FROM common_user.common_user u
        JOIN common_user.common_user_contact uc ON uc.user_id = u.id
        JOIN common_communication.common_contact c ON c.id = uc.contact_id
        WHERE lower(c.contact_value) LIKE lower(?)
          AND u.demo_seed = ?
        """,
        Integer.class,
        aliasPattern,
        demoSeed);
  }

  private Integer countRows(String table, UUID tenantId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE tenant_id = ?", Integer.class, tenantId);
  }
}
