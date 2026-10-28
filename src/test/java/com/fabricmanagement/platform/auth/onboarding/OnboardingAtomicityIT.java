package com.fabricmanagement.platform.auth.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import com.fabricmanagement.platform.auth.app.onboarding.OnboardingContext;
import com.fabricmanagement.platform.auth.app.onboarding.PublishSelfSignupCompletedStep;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import com.fabricmanagement.testsupport.AbstractFabricAppIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ONBOARD-ATOMIC-1: a signup is all-or-nothing, and the pieces it leaves behind are the ones the
 * production roles allow.
 *
 * <p>Runs on {@link AbstractFabricAppIntegrationTest}: the application connects as {@code
 * fabric_app}, so a tenant row or a template copy written on the wrong connection, or on a session
 * bound to the wrong tenant, is rejected by row-level security here as in production. A test on the
 * superuser base would pass either way.
 */
@AutoConfigureMockMvc(addFilters = false)
class OnboardingAtomicityIT extends AbstractFabricAppIntegrationTest {

  /** Tables that carry a {@code tenant_id} and receive a copy of the template at signup. */
  private static final List<String> TEMPLATE_COPY_TABLES =
      List.of(
          "common_user.common_role",
          "common_user.permission_template",
          "sales.ownership_policy",
          "production.prod_property_definition",
          "production.prod_yarn_spinning_system",
          "production.prod_yarn_end_use",
          "production.prod_yarn_test_method",
          "production.prod_fabric_construction",
          "production.prod_fabric_finishing_process",
          "production.prod_fabric_finishing_price_class");

  @Autowired private MockMvc mockMvc;

  /** Cross-tenant access (BYPASSRLS) for assertions; the application itself runs as fabric_app. */
  @Autowired
  @Qualifier("systemJdbcTemplate")
  private JdbcTemplate systemJdbc;

  @Autowired private TenantAccessPort tenantAccessPort;

  /** The last onboarding step (order 14): everything before it, every clone included, has run. */
  @MockitoSpyBean private PublishSelfSignupCompletedStep lastStep;

  @MockitoBean private NotificationService notificationService;
  @MockitoBean private EmailTemplateRenderer emailTemplateRenderer;

  @Test
  void aLateFailureLeavesNothingBehindAndTheRetryCreatesOneCompany() throws Exception {
    stubEmailRendering();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String name = "Willow Cloth Ltd " + suffix;
    String body = signupBody(name, suffix, "TRIAL");

    doThrow(new PlatformDomainException("Injected late failure", "ONBOARDING_LATE_FAILURE", 503))
        .when(lastStep)
        .execute(ArgumentMatchers.<OnboardingContext>any());
    mockMvc.perform(signup(body)).andExpect(status().isServiceUnavailable());

    // The signup got as far as its last step, so every clone and seed before it had run.
    verify(lastStep).execute(ArgumentMatchers.<OnboardingContext>any());
    assertThat(tenantCountByName(name)).isZero();
    for (String table : TEMPLATE_COPY_TABLES) {
      assertThat(orphanRows(table)).as("rows of %s without a tenant", table).isZero();
    }

    // The same company signs up again, and ends up once, under its own name and slug.
    Mockito.reset(lastStep);
    mockMvc.perform(signup(body)).andExpect(status().isOk());

    assertThat(tenantCountByName(name)).isEqualTo(1L);
    String slug =
        systemJdbc.queryForObject(
            "SELECT slug FROM common_tenant.common_tenant WHERE name = ?", String.class, name);
    assertThat(slug).isEqualTo(name.toLowerCase().replaceAll("\\s+", "-"));
    assertTemplateCopies(tenantIdByName(name));
  }

  @Test
  void aRegisterFirstPlaygroundSignupGetsTemplateCopiesFixturesAndSalesDemoOrders()
      throws Exception {
    stubEmailRendering();
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String name = "Alder Mills Ltd " + suffix;

    mockMvc.perform(signup(signupBody(name, suffix, "PLAYGROUND"))).andExpect(status().isOk());

    UUID tenantId = tenantIdByName(name);
    // The copies were written on the signup's own connection: role, permission, ownership policy,
    // property registry, yarn and fabric catalogue. A copy that read the template through the
    // signup's connection would find none of it (row-level security) and copy nothing, silently.
    assertTemplateCopies(tenantId);
    assertThat(count("common_user.common_role", tenantId, "AND is_active = true")).isPositive();
    assertThat(count("sales.ownership_policy", tenantId, "")).isEqualTo(1L);

    // Read back on the signup's connection while it was open, so the demo-mode decision is right.
    assertThat(tenantAccessPort.isDemoMode(tenantId)).isTrue();

    // The register-first gate saw the persisted tenant, so the playground fibre fixtures installed.
    assertThat(
            systemJdbc.queryForObject(
                "SELECT status FROM production.playground_fixture_run WHERE tenant_id = ?",
                String.class,
                tenantId))
        .isEqualTo("COMPLETED");

    // The sales demo orders run after the signup commits, when the demo customers are visible.
    assertThat(
            systemJdbc.queryForObject(
                "SELECT count(*) FROM sales_ord.sales_order"
                    + " WHERE tenant_id = ? AND customer_reference LIKE 'SO-DEMO-%'",
                Long.class, tenantId))
        .isEqualTo(3L);
  }

  // ---------------------------------------------------------------------------------------------

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signup(
      String body) {
    return post("/api/v1/public/signup")
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private static String signupBody(String name, String suffix, String intent) {
    return """
        {"organizationName":"%s","taxId":"GB%s","organizationType":"WEAVER",
         "firstName":"Eleanor","lastName":"Hughes","email":"eleanor.%s@example.com",
         "selectedOS":["FabricOS"],"intent":"%s","acceptedTerms":true}
        """
        .formatted(name, suffix, suffix, intent);
  }

  private void stubEmailRendering() {
    doNothing()
        .when(notificationService)
        .sendNotificationSync(anyString(), anyString(), anyString());
    when(emailTemplateRenderer.renderSetupPassword(
            anyString(), anyString(), anyString(), anyString()))
        .thenReturn("Setup password email body");
    when(emailTemplateRenderer.renderWelcome(anyString(), anyString(), anyString(), anyString()))
        .thenReturn("Welcome email body");
  }

  private long tenantCountByName(String name) {
    return systemJdbc.queryForObject(
        "SELECT count(*) FROM common_tenant.common_tenant WHERE name = ?", Long.class, name);
  }

  private UUID tenantIdByName(String name) {
    return systemJdbc.queryForObject(
        "SELECT id FROM common_tenant.common_tenant WHERE name = ?", UUID.class, name);
  }

  private UUID templateTenantId() {
    return systemJdbc.queryForObject(
        "SELECT id FROM common_tenant.common_tenant WHERE slug = 'golden-template'", UUID.class);
  }

  private long orphanRows(String table) {
    return systemJdbc.queryForObject(
        "SELECT count(*) FROM "
            + table
            + " t WHERE NOT EXISTS"
            + " (SELECT 1 FROM common_tenant.common_tenant c WHERE c.id = t.tenant_id)",
        Long.class);
  }

  /** Rows of {@code table} for {@code tenantId}; {@code filter} is an {@code AND …} fragment. */
  private long count(String table, UUID tenantId, String filter, Object... filterArgs) {
    Object[] args = new Object[filterArgs.length + 1];
    args[0] = tenantId;
    System.arraycopy(filterArgs, 0, args, 1, filterArgs.length);
    return systemJdbc.queryForObject(
        "SELECT count(*) FROM " + table + " WHERE tenant_id = ? " + filter, Long.class, args);
  }

  /**
   * Asserts that the tenant holds as many rows of each copied table as the template tenant, counted
   * with the filter the copy applies (live system-owned rows; permission templates without the
   * explicit-authorisation keys), and that the template has some rows at all (so "equal" is not
   * "both empty").
   */
  private void assertTemplateCopies(UUID tenantId) {
    UUID template = templateTenantId();
    String systemOwned = "AND system_defined = TRUE AND deleted_at IS NULL";
    String permissionFilter =
        "AND is_active = true AND deleted_at IS NULL"
            + " AND NOT (resource || ':' || action = ANY(string_to_array(?, ',')))";
    String explicitKeys = PermissionKey.explicitAuthorisationKeysCsv();
    for (String table : TEMPLATE_COPY_TABLES) {
      String filter;
      Object[] filterArgs = new Object[0];
      if (table.equals("common_user.common_role")) {
        filter = "AND is_active = true";
      } else if (table.equals("common_user.permission_template")) {
        filter = permissionFilter;
        filterArgs = new Object[] {explicitKeys};
      } else if (table.equals("sales.ownership_policy")) {
        filter = "";
      } else {
        filter = systemOwned;
      }
      long templateRows = count(table, template, filter, filterArgs);
      long tenantRows = count(table, tenantId, filter, filterArgs);
      assertThat(templateRows).as("template rows of %s", table).isPositive();
      assertThat(tenantRows)
          .as("rows of %s copied to the new tenant", table)
          .isEqualTo(templateRows);
    }
  }
}
