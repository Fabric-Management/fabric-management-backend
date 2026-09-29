package com.fabricmanagement.production.playground.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.bootstrap.DemoTransactionSeeder;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.generator.app.catalogue.TaskTemplateCatalogueBackfillRunner;
import com.fabricmanagement.platform.auth.app.TenantOnboardingService;
import com.fabricmanagement.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.platform.auth.dto.SignupIntent;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import com.fabricmanagement.platform.tenant.app.TenantTransactionalPurgeService;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.domain.port.PlaygroundFixtureProvisioningPort;
import com.fabricmanagement.platform.tenant.domain.port.PlaygroundFixtureProvisioningPort.RegisterFirstPlaygroundSignup;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * FIBER-CATALOG-1 §9 through the real provisioning orchestration: register-first PLAYGROUND signups
 * and legacy PLAYGROUND tenants receive their own private fixtures on the same shared catalogue;
 * TRIAL signups, unrelated demo tenants, TEMPLATE tenants and anything after go-real receive
 * nothing. Scenarios A03, A17, A18.
 *
 * <p>The generic demo transactions are stubbed (they depend on FX data irrelevant here); the fibre
 * fixtures under test are installed by the real contributor.
 */
class FiberPlaygroundSeedIT extends AbstractIntegrationTest {

  private static final List<String> FIBRE_KEYS =
      List.of(
          PlaygroundFiberFixtureService.CO_UNDECLARED,
          PlaygroundFiberFixtureService.CO_VIRGIN,
          PlaygroundFiberFixtureService.PES_VIRGIN,
          PlaygroundFiberFixtureService.PES_RECYCLED,
          PlaygroundFiberFixtureService.CO60_PES40,
          PlaygroundFiberFixtureService.PES_SOURCE50,
          PlaygroundFiberFixtureService.CO625_PES375);

  @Autowired private TenantOnboardingService onboardingService;
  @Autowired private TenantClonerService tenantClonerService;
  @Autowired private TaskTemplateCatalogueBackfillRunner catalogueRunner;
  @Autowired private PlaygroundFixtureProvisioningPort port;
  @Autowired private TenantTransactionalPurgeService purgeService;
  @Autowired private JdbcTemplate jdbc;

  @MockitoBean private DemoTransactionSeeder demoTransactionSeeder;
  @MockitoBean private NotificationService notificationService;
  @MockitoBean private EmailTemplateRenderer emailTemplateRenderer;

  @BeforeEach
  void stubMail() {
    when(emailTemplateRenderer.renderWelcome(anyString(), anyString(), anyString(), anyString()))
        .thenReturn("welcome");
    when(emailTemplateRenderer.renderSetupPassword(
            anyString(), anyString(), anyString(), anyString()))
        .thenReturn("setup");
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void registerFirstAndLegacyPlaygroundsGetPrivateFixturesOnTheSameSharedIdsAndTrialGetsNone() {
    UUID registerFirst = signup(SignupIntent.PLAYGROUND);
    UUID trial = signup(SignupIntent.TRIAL);
    playgroundSourceWithCatalogue();
    Tenant legacy = tenantClonerService.cloneTemplateToPlayground();

    for (UUID tenant : List.of(registerFirst, legacy.getId())) {
      assertThat(runStatus(tenant)).isEqualTo("COMPLETED");
      assertThat(keys(tenant)).containsAll(FIBRE_KEYS);
      assertThat(entity(tenant, PlaygroundFiberFixtureService.CO_UNDECLARED))
          .as("shared CO keeps its one shared id")
          .isEqualTo(sharedFiber("CO"));
    }
    assertThat(entity(registerFirst, PlaygroundFiberFixtureService.CO60_PES40))
        .as("private rows differ between playground experiences")
        .isNotEqualTo(entity(legacy.getId(), PlaygroundFiberFixtureService.CO60_PES40));

    assertThat(
            count(
                "SELECT count(*) FROM production.playground_fixture_run WHERE tenant_id = ?",
                trial))
        .isZero();
    assertThat(count("SELECT count(*) FROM production.prod_fiber WHERE tenant_id = ?", trial))
        .isZero();
    assertThat(
            count(
                "SELECT count(*) FROM production.prod_fiber WHERE tenant_id = ?"
                    + " AND id IN (SELECT entity_id FROM production.playground_fixture_item"
                    + " WHERE tenant_id = ?)",
                trial,
                registerFirst))
        .as("the TRIAL tenant cannot own or reach another tenant's fixtures")
        .isZero();
  }

  @Test
  void fixturesAreExactHonestAndEvaluatedThroughTheNormalQcFlow() {
    UUID tenant = signup(SignupIntent.PLAYGROUND);

    UUID blend = entity(tenant, PlaygroundFiberFixtureService.CO60_PES40);
    assertThat(
            jdbc.queryForObject(
                "SELECT fiber_iso_code_id FROM production.prod_fiber WHERE id = ?",
                UUID.class,
                blend))
        .isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT composition ->> ? FROM production.prod_fiber WHERE id = ?",
                String.class,
                sharedFiber("CO").toString(),
                entity(tenant, PlaygroundFiberFixtureService.CO625_PES375)))
        .isEqualTo("62.5");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(DISTINCT material_source) FROM production.prod_fiber"
                    + " WHERE id IN (SELECT (jsonb_each_text(composition)).key::uuid"
                    + " FROM production.prod_fiber WHERE id = ?)",
                Long.class,
                entity(tenant, PlaygroundFiberFixtureService.PES_SOURCE50)))
        .isEqualTo(2L);

    Map<String, String> qc = qcStatusByBatchCode(tenant);
    assertThat(qc)
        .containsEntry("PG-FIB-CO-001", "APPROVED")
        .containsEntry("PG-FIB-CO-002", "CONDITIONAL_ACCEPT")
        .containsEntry("PG-FIB-CO-003", "REJECTED")
        .containsEntry("PG-FIB-BL6040-001", "APPROVED")
        .containsEntry("PG-FIB-BL6040-002", "CONDITIONAL_ACCEPT")
        .containsEntry("PG-FIB-BL6040-003", "REJECTED")
        .containsEntry("PG-FIB-BL625-001", "PENDING");
    assertThat(
            jdbc.queryForList(
                "SELECT standard_name FROM production.prod_fiber_quality_standard"
                    + " WHERE tenant_id = ?",
                String.class,
                tenant))
        .hasSize(2)
        .allSatisfy(name -> assertThat(name).startsWith("DEMO —"));

    List<Map<String, Object>> certificates =
        jdbc.queryForList(
            "SELECT c.cert_number, c.scope, c.certificate_kind, c.document_url,"
                + " c.certifying_body_ref, s.certification_code, s.tenant_id AS scheme_owner"
                + " FROM production.production_execution_batch_certification c"
                + " JOIN production.prod_fiber_certification s ON s.id = c.certification_id"
                + " WHERE c.tenant_id = ? ORDER BY c.cert_number",
            tenant);
    assertThat(certificates).hasSize(2);
    assertThat(certificates)
        .allSatisfy(
            row -> {
              assertThat((String) row.get("cert_number")).startsWith("DEMO-");
              assertThat(row.get("scope")).isEqualTo("SUPPLIER");
              assertThat(row.get("certificate_kind")).isEqualTo("SCOPE");
              assertThat(row.get("document_url")).isNull();
              assertThat((String) row.get("certifying_body_ref")).contains("not a real");
              assertThat(row.get("scheme_owner")).isEqualTo(FiberCatalog.OWNER_ID);
            });
    assertThat(certificates)
        .extracting(row -> row.get("certification_code"))
        .containsExactly("GOTS", "OEKO_TEX_100");
    assertThat(
            count(
                "SELECT count(*) FROM production.production_execution_batch b"
                    + " WHERE b.tenant_id = ? AND b.batch_code = 'PG-FIB-COV-003'"
                    + " AND NOT EXISTS (SELECT 1 FROM"
                    + " production.production_execution_batch_certification c"
                    + " WHERE c.batch_id = b.id)",
                tenant))
        .as("an otherwise equivalent uncertified batch exists")
        .isEqualTo(1L);
  }

  @Test
  void replayAndPartialRepairCreateNoDuplicatesAndIneligibleOriginsInstallNothing() {
    playgroundSourceWithCatalogue();
    Tenant legacy = tenantClonerService.cloneTemplateToPlayground();
    UUID tenant = legacy.getId();
    long fibres = count("SELECT count(*) FROM production.prod_fiber WHERE tenant_id = ?", tenant);
    long batches =
        count(
            "SELECT count(*) FROM production.production_execution_batch WHERE tenant_id = ?",
            tenant);
    long results =
        count(
            "SELECT count(*) FROM production.production_quality_fiber_test_result"
                + " WHERE tenant_id = ?",
            tenant);

    clearInvocations(notificationService, emailTemplateRenderer);
    port.provisionLegacyPlayground(tenant);
    jdbc.update(
        "UPDATE production.playground_fixture_run SET status = 'PENDING', completed_at = NULL"
            + " WHERE tenant_id = ?",
        tenant);
    jdbc.update(
        "DELETE FROM production.playground_fixture_item WHERE tenant_id = ?"
            + " AND fixture_key IN ('PG-CERT-COV-OEKO', 'PG-BATCH-CO-PASS-QC')",
        tenant);
    port.provisionLegacyPlayground(tenant);

    assertThat(runStatus(tenant)).isEqualTo("COMPLETED");
    assertThat(count("SELECT count(*) FROM production.prod_fiber WHERE tenant_id = ?", tenant))
        .isEqualTo(fibres);
    assertThat(
            count(
                "SELECT count(*) FROM production.production_execution_batch WHERE tenant_id = ?",
                tenant))
        .isEqualTo(batches);
    assertThat(
            count(
                "SELECT count(*) FROM production.production_quality_fiber_test_result"
                    + " WHERE tenant_id = ?",
                tenant))
        .isEqualTo(results);
    assertThat(keys(tenant)).contains("PG-CERT-COV-OEKO", "PG-BATCH-CO-PASS-QC");
    // A18: installing or repairing fixtures never sends an email or renders one.
    verify(notificationService, never())
        .sendNotificationSync(anyString(), anyString(), anyString());
    verify(notificationService, never())
        .sendNotificationSync(any(UUID.class), anyString(), anyString(), anyString());
    verifyNoInteractions(emailTemplateRenderer);

    UUID regular = insertTenant("REGULAR", false);
    UUID demoRegular = insertTenant("REGULAR", true);
    UUID template = insertTenant("TEMPLATE", true);
    UUID unmarkedPlayground = insertTenant("PLAYGROUND", true);
    registerFirst(regular, "PLAYGROUND");
    // demoRegular is a REGULAR + demo_mode tenant: only a complete PLAYGROUND register-first
    // signup would qualify, so each call below lacks exactly one required fact.
    registerFirst(demoRegular, "TRIAL");
    registerFirst(demoRegular, "PLAYGROUND", false);
    TenantContext.executeInTenantContext(
        demoRegular,
        () -> {
          port.provisionRegisterFirstPlayground(
              new RegisterFirstPlaygroundSignup(demoRegular, "PLAYGROUND", true, true, false));
          port.provisionRegisterFirstPlayground(
              new RegisterFirstPlaygroundSignup(demoRegular, "PLAYGROUND", true, false, true));
        });
    registerFirst(template, "PLAYGROUND");
    port.provisionLegacyPlayground(regular);
    port.provisionLegacyPlayground(template);
    port.provisionLegacyPlayground(unmarkedPlayground);
    port.provisionLegacyPlayground(FiberCatalog.OWNER_ID);

    for (UUID ineligible : List.of(regular, demoRegular, template, unmarkedPlayground)) {
      assertThat(
              count("SELECT count(*) FROM production.prod_fiber WHERE tenant_id = ?", ineligible))
          .isZero();
      assertThat(
              count(
                  "SELECT count(*) FROM production.playground_fixture_run WHERE tenant_id = ?",
                  ineligible))
          .isZero();
    }
  }

  @Test
  void goRealRemovesPrivateFixturesKeepsTheSharedCatalogueAndNothingReinstallsThem() {
    UUID tenant = signup(SignupIntent.PLAYGROUND);
    long sharedFibres =
        count(
            "SELECT count(*) FROM production.prod_fiber WHERE tenant_id = ?",
            FiberCatalog.OWNER_ID);

    purgeService.goReal(tenant);
    registerFirst(tenant, "PLAYGROUND");

    assertThat(count("SELECT count(*) FROM production.prod_fiber WHERE tenant_id = ?", tenant))
        .isZero();
    assertThat(
            count(
                "SELECT count(*) FROM production.playground_fixture_item WHERE tenant_id = ?",
                tenant))
        .isZero();
    assertThat(
            count(
                "SELECT count(*) FROM production.prod_fiber WHERE tenant_id = ?",
                FiberCatalog.OWNER_ID))
        .isEqualTo(sharedFibres);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private UUID signup(SignupIntent intent) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    return onboardingService
        .createSelfServiceTenant(
            SelfSignupRequest.builder()
                .organizationName("Fibre Playground " + suffix)
                .taxId("FPG" + suffix)
                .organizationType(OrganizationType.SPINNER)
                .firstName("Demo")
                .lastName("Prospect")
                .email("fibre-playground-" + suffix + "@example.com")
                .intent(intent)
                .acceptedTerms(true)
                .build())
        .getTenantId();
  }

  private void registerFirst(UUID tenant, String intent) {
    registerFirst(tenant, intent, true);
  }

  private void registerFirst(UUID tenant, String intent, boolean demoMode) {
    TenantContext.executeInTenantContext(
        tenant,
        () ->
            port.provisionRegisterFirstPlayground(
                new RegisterFirstPlaygroundSignup(tenant, intent, demoMode, false, false)));
  }

  private UUID insertTenant(String type, boolean demoMode) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, status, settings,"
            + " demo_mode, is_active, created_at, updated_at, version)"
            + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', '{}', ?, true, now(), now(), 0)",
        id,
        "FPG-" + id.toString().substring(0, 8),
        "fibre-playground-" + id,
        "Fibre playground " + id,
        type,
        demoMode);
    return id;
  }

  private String runStatus(UUID tenant) {
    return jdbc.queryForObject(
        "SELECT status FROM production.playground_fixture_run WHERE tenant_id = ?",
        String.class,
        tenant);
  }

  private List<String> keys(UUID tenant) {
    return jdbc.queryForList(
        "SELECT fixture_key FROM production.playground_fixture_item WHERE tenant_id = ?",
        String.class,
        tenant);
  }

  private UUID entity(UUID tenant, String key) {
    return jdbc.queryForObject(
        "SELECT entity_id FROM production.playground_fixture_item"
            + " WHERE tenant_id = ? AND fixture_key = ?",
        UUID.class,
        tenant,
        key);
  }

  private UUID sharedFiber(String isoCode) {
    return jdbc.queryForObject(
        "SELECT f.id FROM production.prod_fiber f JOIN production.prod_fiber_iso_code i"
            + " ON i.id = f.fiber_iso_code_id WHERE f.tenant_id = ? AND i.iso_code = ?",
        UUID.class,
        FiberCatalog.OWNER_ID,
        isoCode);
  }

  private Map<String, String> qcStatusByBatchCode(UUID tenant) {
    Map<String, String> result = new java.util.HashMap<>();
    jdbc.query(
        "SELECT b.batch_code, r.approval_status FROM production.production_execution_batch b"
            + " JOIN production.production_quality_fiber_test_result r ON r.batch_id = b.id"
            + " WHERE b.tenant_id = ?",
        rs -> {
          result.put(rs.getString(1), rs.getString(2));
        },
        tenant);
    return result;
  }

  private long count(String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0 : value;
  }

  /** The playground source with its task-template catalogue (as PlaygroundCatalogueCloneIT). */
  private void playgroundSourceWithCatalogue() {
    List<UUID> existing =
        jdbc.queryForList(
            "SELECT id FROM common_tenant.common_tenant WHERE slug = ?",
            UUID.class,
            TenantClonerService.PLAYGROUND_SOURCE_SLUG);
    if (existing.isEmpty()) {
      jdbc.update(
          "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, billing_email,"
              + " status, settings, is_active, created_at, updated_at, version)"
              + " VALUES (?, ?, ?, 'Nexus Fabrics', 'TEMPLATE', 'test@example.com', 'ACTIVE', '{}',"
              + " true, now(), now(), 0)",
          UUID.randomUUID(),
          UUID.randomUUID().toString(),
          TenantClonerService.PLAYGROUND_SOURCE_SLUG);
    }
    catalogueRunner.run();
  }
}
