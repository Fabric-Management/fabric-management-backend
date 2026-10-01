package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.app.TenantOnboardingService;
import com.fabricmanagement.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.platform.auth.dto.SignupIntent;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.sales.orderintake.app.OrderIntakeAvailabilityService;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeAvailabilityDto;
import com.fabricmanagement.testsupport.PostgresImage;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The colour/stock demo through the real playground provisioning chain (register-first PLAYGROUND
 * signup → demo transactions → {@link SalesQuoteDemoSeeder}), verified in the database rather than
 * through mocks: this is the run that silently rolled back while the unit test stayed green. Covers
 * the seed itself, the numbers the order form reads, and a replay.
 *
 * <p>Runs on the real application role: the primary datasource is {@code fabric_app} (NOSUPERUSER,
 * NOBYPASSRLS), so every read and write goes through row-level security exactly as in the running
 * backend. On the superuser the shared base class uses, RLS is bypassed and a transaction opened
 * before the tenant is bound still sees the playground run row — which is how a seed gate that
 * answered "no playground" for every real playground passed here. Assertions read through {@code
 * systemJdbcTemplate} (fabric_system, BYPASSRLS), the only {@link JdbcTemplate} bean.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SalesQuoteDemoSeedIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      PostgresImage.container()
          .withDatabaseName("fabric_test")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    try (Connection conn =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS PASSWORD 'app_test'");
      stmt.execute(
          "CREATE ROLE fabric_system LOGIN NOSUPERUSER NOCREATEDB BYPASSRLS PASSWORD 'system_test'");
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create test database roles", e);
    }
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "fabric_app");
    registry.add("spring.datasource.password", () -> "app_test");
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("application.system-datasource.username", () -> "fabric_system");
    registry.add("application.system-datasource.password", () -> "system_test");
  }

  /** The primary (fabric_app) datasource, used only to prove which role the backend runs as. */
  @Autowired private DataSource dataSource;

  @Autowired private TenantOnboardingService onboardingService;
  @Autowired private SalesQuoteDemoSeeder salesQuoteDemoSeeder;
  @Autowired private OrderIntakeAvailabilityService availabilityService;
  @Autowired private JdbcTemplate jdbc;

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
  void theBackendUnderTestRunsAsTheRlsBoundApplicationRole() {
    Map<String, Object> role =
        new JdbcTemplate(dataSource)
            .queryForMap(
                "SELECT current_user AS usr, r.rolsuper AS sup, r.rolbypassrls AS bypass"
                    + " FROM pg_roles r WHERE r.rolname = current_user");

    assertThat(role.get("usr")).isEqualTo("fabric_app");
    assertThat(role.get("sup")).isEqualTo(false);
    assertThat(role.get("bypass")).isEqualTo(false);
  }

  @Test
  void playgroundSignupSeedsColoursPartnerCodesAndColouredStock_andReplayAddsNothing() {
    long startedAt = System.nanoTime();
    UUID tenant = signupPlayground();
    Duration signupTook = Duration.ofNanos(System.nanoTime() - startedAt);

    // The seed publishes a few hundred stock-unit events at commit. A pool deadlock in the async
    // listeners (two pooled connections per listener) once stretched this signup to ~5 minutes of
    // 30-second connection timeouts and rejected tasks; the bound below is generous for a healthy
    // run and far below that failure mode.
    assertThat(signupTook).isLessThan(Duration.ofSeconds(90));
    awaitBackgroundEventsSettled(tenant);

    // Colour cards: every demo card exists, family/type are set, only Navy and Indigo are approved.
    Map<String, Map<String, Object>> colours = colours(tenant);
    assertThat(colours.keySet())
        .containsAll(
            List.of("NAVY-01", "ECRU-02", "CHAR-03", "PFD-00", "GREIGE-00", "RUST-04", "IND-05"));
    assertThat(colours.get("NAVY-01"))
        .containsEntry("standard_status", "APPROVED")
        .containsEntry("color_family", "BLUE")
        .containsEntry("color_type", "DYED")
        .containsEntry("pantone_code", "19-4024");
    assertThat(colours.get("NAVY-01").get("target_lab_l")).isNotNull();
    assertThat(colours.get("IND-05"))
        .containsEntry("standard_status", "APPROVED")
        .containsEntry("color_type", "DYED");
    assertThat(colours.get("ECRU-02")).containsEntry("standard_status", "DRAFT");
    assertThat(colours.get("ECRU-02").get("target_lab_l")).isNotNull();
    for (String undyed : List.of("PFD-00", "GREIGE-00")) {
      assertThat(colours.get(undyed).get("color_hex")).isNull();
      assertThat(colours.get(undyed).get("pantone_code")).isNull();
      assertThat(colours.get(undyed).get("target_lab_l")).isNull();
      assertThat(colours.get(undyed).get("delta_e_tolerance")).isNull();
    }

    // Partner codes: Albion's Navy primary + previous-season alias, Albion's Ecru, supplier Indigo.
    assertThat(partnerCodes(tenant))
        .containsAll(
            List.of(
                "CUSTOMER|NAVY-01|ALB-NVY-26|true",
                "CUSTOMER|NAVY-01|SS26-NAVY|false",
                "CUSTOMER|ECRU-02|ALB-ECR-26|true",
                "SUPPLIER|IND-05|AYM-IND-2201|true"));

    // Stock: fabric lots are booked in metres and the coloured yarn lot exists.
    assertThat(
            jdbc.queryForList(
                "SELECT unit FROM production.production_execution_batch"
                    + " WHERE tenant_id = ? AND product_type = 'FABRIC'",
                String.class,
                tenant))
        .isNotEmpty()
        .containsOnly("M");
    assertThat(lotColour(tenant, "LOT-24035")).isEqualTo("IND-05");
    assertThat(lotColour(tenant, "LOT-24011")).isEqualTo("NAVY-01");

    // The order form's numbers: Navy stock exists; Rust has an honest zero; Indigo yarn is fully
    // free in kilograms.
    OrderIntakeAvailabilityDto navy = availability(tenant, "LOT-24011", "NAVY-01");
    assertThat(navy.unit()).isEqualTo("M");
    assertThat(navy.availableQuantity().add(navy.unknownQuantity())).isPositive();
    OrderIntakeAvailabilityDto rust = availability(tenant, "LOT-24011", "RUST-04");
    assertThat(rust.availableQuantity()).isZero();
    assertThat(rust.lotCount()).isZero();
    OrderIntakeAvailabilityDto indigo = availability(tenant, "LOT-24035", "IND-05");
    assertThat(indigo.unit()).isEqualTo("KG");
    assertThat(indigo.availableQuantity()).isEqualByComparingTo("600");

    // Replay: same colours, lots, pieces, partner codes, quotes and reservations.
    Map<String, Long> before = counts(tenant);
    TenantContext.executeInTenantContext(tenant, () -> salesQuoteDemoSeeder.seedFor(tenant));
    assertThat(counts(tenant)).isEqualTo(before);
  }

  @Test
  void trialSignupGetsNoPlaygroundColourDemo() {
    UUID trial = signup(SignupIntent.TRIAL);

    assertThat(colours(trial)).isEmpty();
    assertThat(counts(trial).get("lots")).isZero();
    assertThat(counts(trial).get("partnerRefs")).isZero();
  }

  private UUID signupPlayground() {
    return signup(SignupIntent.PLAYGROUND);
  }

  /**
   * Every event publication a playground signup raises must complete: a rejected async task, a
   * listener that died on a connection timeout, the ownership listener without a tenant ownership
   * policy, or the QC listener without a trusted actor on the fibre fixtures each leave a
   * publication without a completion date — and are retried on every restart. The failing
   * publications are listed, not just counted, so a regression names its listener.
   */
  private void awaitBackgroundEventsSettled(UUID tenant) {
    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.queryForList(
                            "SELECT listener_id FROM event_publication"
                                + " WHERE completion_date IS NULL AND serialized_event LIKE ?",
                            String.class,
                            "%" + tenant + "%"))
                    .isEmpty());
  }

  private UUID signup(SignupIntent intent) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    return onboardingService
        .createSelfServiceTenant(
            SelfSignupRequest.builder()
                .organizationName("Colour Playground " + suffix)
                .taxId("CPG" + suffix)
                .organizationType(OrganizationType.SPINNER)
                .firstName("Demo")
                .lastName("Prospect")
                .email("colour-playground-" + suffix + "@example.com")
                .intent(intent)
                .acceptedTerms(true)
                .build())
        .getTenantId();
  }

  private Map<String, Map<String, Object>> colours(UUID tenant) {
    Map<String, Map<String, Object>> byCode = new HashMap<>();
    for (Map<String, Object> row :
        jdbc.queryForList(
            "SELECT code, color_type, color_family, standard_status, color_hex, pantone_code,"
                + " target_lab_l, delta_e_tolerance FROM production.color WHERE tenant_id = ?",
            tenant)) {
      byCode.put((String) row.get("code"), row);
    }
    return byCode;
  }

  private List<String> partnerCodes(UUID tenant) {
    return jdbc.queryForList(
        "SELECT r.role || '|' || c.code || '|' || pc.external_code || '|' || pc.is_primary"
            + " FROM production.color_partner_code pc"
            + " JOIN production.color_partner_ref r ON r.id = pc.color_partner_ref_id"
            + " JOIN production.color c ON c.id = r.color_id"
            + " WHERE pc.tenant_id = ? AND pc.is_active",
        String.class,
        tenant);
  }

  private String lotColour(UUID tenant, String batchCode) {
    return jdbc.queryForObject(
        "SELECT c.code FROM production.production_execution_batch b"
            + " JOIN production.color c ON c.id = b.color_id"
            + " WHERE b.tenant_id = ? AND b.batch_code = ?",
        String.class,
        tenant,
        batchCode);
  }

  private OrderIntakeAvailabilityDto availability(UUID tenant, String batchCode, String colour) {
    UUID productId =
        jdbc.queryForObject(
            "SELECT product_id FROM production.production_execution_batch"
                + " WHERE tenant_id = ? AND batch_code = ?",
            UUID.class,
            tenant,
            batchCode);
    UUID colorId =
        jdbc.queryForObject(
            "SELECT id FROM production.color WHERE tenant_id = ? AND code = ?",
            UUID.class,
            tenant,
            colour);
    return TenantContext.executeInTenantContext(
        tenant, () -> availabilityService.forProductColour(productId, colorId, null));
  }

  private Map<String, Long> counts(UUID tenant) {
    Map<String, Long> counts = new HashMap<>();
    counts.put(
        "colours", count("SELECT count(*) FROM production.color WHERE tenant_id = ?", tenant));
    counts.put(
        "partnerRefs",
        count("SELECT count(*) FROM production.color_partner_ref WHERE tenant_id = ?", tenant));
    counts.put(
        "partnerCodes",
        count("SELECT count(*) FROM production.color_partner_code WHERE tenant_id = ?", tenant));
    counts.put(
        "lots",
        count(
            "SELECT count(*) FROM production.production_execution_batch WHERE tenant_id = ?",
            tenant));
    counts.put(
        "pieces", count("SELECT count(*) FROM production.stock_unit WHERE tenant_id = ?", tenant));
    counts.put("quotes", count("SELECT count(*) FROM sales.quote WHERE tenant_id = ?", tenant));
    counts.put(
        "reservations",
        count(
            "SELECT count(*) FROM production.production_execution_batch_reservation"
                + " WHERE tenant_id = ?",
            tenant));
    return counts;
  }

  private long count(String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0L : value;
  }
}
