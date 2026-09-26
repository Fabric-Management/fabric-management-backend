package com.fabricmanagement.flowboard.generator.app.catalogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.domain.event.production.WorkOrderRecipeAssignmentNeededEvent;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.flowboard.generator.app.EventRouterService;
import com.fabricmanagement.flowboard.generator.app.TaskTemplateService;
import com.fabricmanagement.flowboard.generator.domain.AssigneeRole;
import com.fabricmanagement.flowboard.generator.domain.TaskTemplate;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueFinding;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueReconciliationResult;
import com.fabricmanagement.flowboard.generator.domain.catalogue.CatalogueSourceRow;
import com.fabricmanagement.flowboard.generator.domain.catalogue.TaskTemplateCatalogue;
import com.fabricmanagement.flowboard.generator.dto.CreateTaskTemplateRequest;
import com.fabricmanagement.flowboard.generator.dto.UpdateTaskTemplateRequest;
import com.fabricmanagement.flowboard.generator.infra.repository.TaskTemplateRepository;
import com.fabricmanagement.flowboard.task.domain.ModuleType;
import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.flowboard.task.domain.TaskType;
import com.fabricmanagement.platform.auth.app.onboarding.OnboardingContext;
import com.fabricmanagement.platform.auth.app.onboarding.ProvisionTenantCatalogueStep;
import com.fabricmanagement.testsupport.PostgresImage;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TASK-TEMPLATE-TENANCY-1 §8 on the real application role: the primary datasource is {@code
 * fabric_app} (NOSUPERUSER, NOBYPASSRLS, not the owner), so every read and write below goes through
 * RLS exactly as in the running backend. Arrangement and assertions that must see every tenant use
 * a separate owner connection. Covers T-TEN, T-SOFTDEL, T-ONB and the MANDATORY half of T-TXGUARD.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TaskTemplateTenancyIT {

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

  private static final UUID GOLDEN = TenantContext.TEMPLATE_TENANT_ID;
  private static final UUID SYSTEM = TenantContext.SYSTEM_TENANT_ID;
  private static final TaskTemplateCatalogue QUOTE =
      TaskTemplateCatalogue.QUOTE_SEND_REQUESTED__APPROVAL;
  private static final TaskTemplateCatalogue RECIPE =
      TaskTemplateCatalogue.WORK_ORDER_RECIPE_ASSIGNMENT_NEEDED__RECIPE_ASSIGNMENT;

  @Autowired private TaskTemplateCatalogueBackfillRunner runner;
  @Autowired private TenantCatalogueWriter writer;
  @Autowired private CatalogueSourceReader reader;
  @Autowired private TaskTemplateRepository repository;
  @Autowired private TaskTemplateService service;
  @Autowired private EventRouterService router;
  @Autowired private ProvisionTenantCatalogueStep onboardingStep;

  /**
   * The primary (fabric_app) datasource. Do not autowire {@code JdbcTemplate}: the only such bean
   * is {@code systemJdbcTemplate} (fabric_system, BYPASSRLS), which would verify the wrong role.
   */
  @Autowired private DataSource dataSource;

  @Autowired private PlatformTransactionManager transactionManager;

  private JdbcTemplate owner() {
    return new JdbcTemplate(
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  // ---------------------------------------------------------------------------------------------
  // Role
  // ---------------------------------------------------------------------------------------------

  @Test
  void theConnectionUnderTestIsTheRealApplicationRole() {
    UUID tenant = tenant("REGULAR");
    Map<String, Object> role =
        inTenant(
            tenant,
            () ->
                new JdbcTemplate(dataSource)
                    .queryForMap(
                        "SELECT current_user AS usr, r.rolsuper AS sup, r.rolbypassrls AS bypass"
                            + " FROM pg_roles r WHERE r.rolname = current_user"));
    System.out.println("TASK-TEMPLATE-TENANCY-1 connection under test: " + role);

    assertThat(role.get("usr")).isEqualTo("fabric_app");
    assertThat(role.get("sup")).isEqualTo(false);
    assertThat(role.get("bypass")).isEqualTo(false);
  }

  // ---------------------------------------------------------------------------------------------
  // §8.1 visibility, §8.2 router end-to-end, §8.3 isolation
  // ---------------------------------------------------------------------------------------------

  @Test
  void afterReconcileEachAdapterEventSeesExactlyTheTenantsOwnCatalogueRow() {
    UUID tenant = tenant("REGULAR");

    CatalogueReconciliationResult first = reconcile(tenant);
    CatalogueReconciliationResult second = reconcile(tenant);

    assertThat(first.inserted()).isEqualTo(5);
    assertThat(second.inserted()).isZero();
    assertThat(second.adopted()).isZero();
    for (TaskTemplateCatalogue entry : TaskTemplateCatalogue.values()) {
      List<TaskTemplate> visible =
          inTenant(tenant, () -> repository.findByEventTypeAndIsActiveTrue(entry.eventType()));
      assertThat(visible)
          .as(entry.key())
          .singleElement()
          .satisfies(
              row -> {
                assertThat(row.getTenantId()).isEqualTo(tenant);
                assertThat(row.getCatalogKey()).isEqualTo(entry.key());
              });
    }
  }

  @Test
  void recipeEventCreatesExactlyOneTaskOnTheTenantsGlobalBoard() {
    UUID tenant = tenant("REGULAR");
    reconcile(tenant);
    UUID board = globalBoard(tenant);
    UUID workOrder = UUID.randomUUID();

    inTenant(
        tenant,
        () -> {
          router.route(
              new WorkOrderRecipeAssignmentNeededEvent(
                  tenant, workOrder, UUID.randomUUID(), null, null));
          return null;
        });

    String key =
        "subjectType:WORK_ORDER:subjectId:"
            + workOrder
            + ":taskType:RECIPE_ASSIGNMENT:fulfillmentMode:NONE";
    assertThat(
            owner()
                .queryForObject(
                    "SELECT count(*) FROM flowboard.task WHERE tenant_id = ? AND generation_key = ?"
                        + " AND board_id = ?",
                    Integer.class,
                    tenant,
                    key,
                    board))
        .isEqualTo(1);
  }

  @Test
  void anotherTenantsPrivateTemplateAndSystemRowsAreInvisible() {
    UUID a = tenant("REGULAR");
    UUID b = tenant("REGULAR");
    reconcile(a);
    reconcile(b);
    owner()
        .update(
            """
            INSERT INTO flowboard.task_template (id, tenant_id, uid, name, event_type,
                title_template, task_type, default_priority, default_assignee_role, is_active,
                created_at, updated_at, version)
            VALUES (gen_random_uuid(), ?, gen_random_uuid()::varchar, 'B private',
                'IsolationProbe', 'Probe', 'GENERAL', 'LOW', 'ANY', TRUE, now(), now(), 0)
            """,
            b);

    assertThat(inTenant(a, () -> repository.findByEventTypeAndIsActiveTrue("IsolationProbe")))
        .isEmpty();
    assertThat(inTenant(b, () -> repository.findByEventTypeAndIsActiveTrue("IsolationProbe")))
        .hasSize(1);
    // V002 row that stays in SYSTEM (not a catalogue entry): invisible to every tenant.
    assertThat(inTenant(a, () -> repository.findByEventTypeAndIsActiveTrue("ApprovalPending")))
        .isEmpty();
    // golden's recipe row is invisible too: A sees only its own copy.
    assertThat(inTenant(a, () -> repository.findByEventTypeAndIsActiveTrue(RECIPE.eventType())))
        .singleElement()
        .extracting(TaskTemplate::getTenantId)
        .isEqualTo(a);
  }

  // ---------------------------------------------------------------------------------------------
  // §8.4 reconciliation cases (each second run writes nothing)
  // ---------------------------------------------------------------------------------------------

  @Test
  void r2AdoptsTheRandomUidQuoteSeedInsteadOfAddingASecondRow() {
    UUID tenant = tenant("REGULAR");
    UUID seed = insertSeedQuote(tenant, 0);

    CatalogueReconciliationResult result = reconcile(tenant);

    assertThat(result.adopted()).isEqualTo(1);
    assertThat(result.inserted()).isEqualTo(4);
    assertThat(quoteRows(tenant))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("id")).isEqualTo(seed);
              assertThat(row.get("catalog_key")).isEqualTo(QUOTE.key());
            });
    assertThat(reconcile(tenant).adopted()).isZero();
  }

  @Test
  void r0KeepsATenantDeactivatedOrDeletedKeyedRowAsItIs() {
    UUID tenant = tenant("REGULAR");
    reconcile(tenant);
    owner()
        .update(
            "UPDATE flowboard.task_template SET is_active = FALSE WHERE tenant_id = ?"
                + " AND catalog_key = ?",
            tenant,
            QUOTE.key());
    owner()
        .update(
            "UPDATE flowboard.task_template SET is_active = FALSE, deleted_at = now()"
                + " WHERE tenant_id = ? AND catalog_key = ?",
            tenant,
            RECIPE.key());

    CatalogueReconciliationResult result = reconcile(tenant);

    assertThat(result.inserted()).isZero();
    assertThat(keyedRows(tenant, QUOTE))
        .singleElement()
        .satisfies(row -> assertThat(row.get("is_active")).isEqualTo(false));
    assertThat(keyedRows(tenant, RECIPE))
        .singleElement()
        .satisfies(row -> assertThat(row.get("deleted_at")).isNotNull());
  }

  @Test
  void r3EditedSeedProducesAFindingAndNoWrite() {
    UUID tenant = tenant("REGULAR");
    insertSeedQuote(tenant, 1);

    CatalogueReconciliationResult result = reconcile(tenant);

    assertThat(result.findings())
        .singleElement()
        .extracting(CatalogueFinding::reason)
        .isEqualTo(CatalogueFinding.SIGNATURE_MISMATCH + "version");
    assertThat(quoteRows(tenant))
        .singleElement()
        .satisfies(row -> assertThat(row.get("catalog_key")).isNull());
  }

  @Test
  void r3TwoCandidatesProduceAFindingAndNoWrite() {
    UUID tenant = tenant("REGULAR");
    insertSeedQuote(tenant, 0);
    insertSeedQuote(tenant, 0);

    CatalogueReconciliationResult result = reconcile(tenant);

    assertThat(result.findings())
        .singleElement()
        .extracting(CatalogueFinding::reason)
        .isEqualTo(CatalogueFinding.MULTIPLE_CANDIDATES);
    assertThat(quoteRows(tenant))
        .hasSize(2)
        .allSatisfy(row -> assertThat(row.get("catalog_key")).isNull());
  }

  @Test
  void r3ApiCreatedTwinOfTheSeedIsNeverAdopted() {
    UUID tenant = tenant("REGULAR");
    CreateTaskTemplateRequest twin = new CreateTaskTemplateRequest();
    twin.setName(QUOTE.seedName());
    twin.setEventType(QUOTE.eventType());
    twin.setTitleTemplate("Quote send approval - {quote.quoteNumber}");
    twin.setTaskType(TaskType.APPROVAL);
    twin.setModuleType(ModuleType.GENERAL);
    twin.setDefaultPriority(Priority.HIGH);
    twin.setDefaultAssigneeRole(AssigneeRole.ANY);
    twin.setEstimatedHours(new BigDecimal("0.25"));
    inTenant(tenant, () -> service.createTemplate(twin));

    CatalogueReconciliationResult result = reconcile(tenant);

    assertThat(result.findings())
        .singleElement()
        .extracting(CatalogueFinding::reason)
        .isEqualTo(CatalogueFinding.SIGNATURE_MISMATCH + "createdBy");
    assertThat(quoteRows(tenant))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("catalog_key")).isNull();
              assertThat(row.get("created_by")).isNotNull();
            });
  }

  @Test
  void r0WithAnUnkeyedSiblingReportsOneSiblingFindingAndWritesNothing() {
    UUID tenant = tenant("REGULAR");
    reconcile(tenant);
    UUID sibling = insertSeedQuote(tenant, 0);

    CatalogueReconciliationResult result = reconcile(tenant);

    assertThat(result.inserted()).isZero();
    assertThat(result.adopted()).isZero();
    assertThat(result.findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.reason()).isEqualTo(CatalogueFinding.UNKEYED_SIBLING);
              assertThat(finding.candidateIds()).contains(sibling).hasSize(2);
            });
    assertThat(quoteRows(tenant)).hasSize(2);
  }

  // ---------------------------------------------------------------------------------------------
  // T-SOFTDEL: a deleted catalogue row never comes back through the API or the backfill
  // ---------------------------------------------------------------------------------------------

  @Test
  void deletedCatalogueRowIsGoneForTheApiAndStaysDeletedAcrossTheBackfill() {
    UUID tenant = tenant("REGULAR");
    reconcile(tenant);
    UUID quoteId = (UUID) keyedRows(tenant, QUOTE).getFirst().get("id");

    inTenant(
        tenant,
        () -> {
          service.deleteTemplate(quoteId);
          return null;
        });

    assertThat(keyedRows(tenant, QUOTE))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.get("is_active")).isEqualTo(false);
              assertThat(row.get("deleted_at")).isNotNull();
            });
    assertThat(inTenant(tenant, () -> service.getAllTemplates()))
        .noneMatch(dto -> dto.getId().equals(quoteId));
    assertThatThrownBy(() -> inTenant(tenant, () -> service.getTemplateById(quoteId)))
        .isInstanceOf(NotFoundException.class);
    UpdateTaskTemplateRequest reactivate = new UpdateTaskTemplateRequest();
    reactivate.setName("revived");
    reactivate.setEventType(QUOTE.eventType());
    reactivate.setTitleTemplate("x");
    reactivate.setTaskType(TaskType.APPROVAL);
    reactivate.setDefaultPriority(Priority.HIGH);
    reactivate.setDefaultAssigneeRole(AssigneeRole.ANY);
    reactivate.setActive(true);
    assertThatThrownBy(() -> inTenant(tenant, () -> service.updateTemplate(quoteId, reactivate)))
        .isInstanceOf(NotFoundException.class);
    assertThat(inTenant(tenant, () -> repository.findByEventTypeAndIsActiveTrue(QUOTE.eventType())))
        .isEmpty();

    CatalogueReconciliationResult afterRestart = reconcile(tenant);

    assertThat(afterRestart.inserted()).isZero();
    assertThat(keyedRows(tenant, QUOTE))
        .singleElement()
        .satisfies(row -> assertThat(row.get("deleted_at")).isNotNull());
  }

  // ---------------------------------------------------------------------------------------------
  // §8.5 targets, run through the startup runner
  // ---------------------------------------------------------------------------------------------

  @Test
  void runnerReachesBusinessPlaygroundAndSourceTenantsButNeverSystemGoldenOrDeleted() {
    UUID regular = tenant("REGULAR");
    UUID playground = tenant("PLAYGROUND");
    UUID deleted = tenant("REGULAR");
    owner()
        .update("UPDATE common_tenant.common_tenant SET deleted_at = now() WHERE id = ?", deleted);
    UUID nexus = playgroundSource();
    int goldenBefore = keyedCount(GOLDEN);

    runner.run();

    assertThat(keyedCount(regular)).isEqualTo(5);
    assertThat(keyedCount(playground)).isEqualTo(5);
    assertThat(keyedCount(nexus)).isEqualTo(5);
    assertThat(keyedCount(deleted)).isZero();
    assertThat(keyedCount(SYSTEM)).isZero();
    assertThat(keyedCount(GOLDEN)).isEqualTo(goldenBefore).isEqualTo(5);
  }

  // ---------------------------------------------------------------------------------------------
  // T-ONB / T-TXGUARD: the onboarding write joins the caller's transaction
  // ---------------------------------------------------------------------------------------------

  @Test
  void onboardingStepProvisionsTheNewTenantInsideTheOnboardingTransaction() {
    UUID tenant = tenant("REGULAR");

    inTenant(
        tenant,
        () -> {
          onboardingStep.execute(context(tenant));
          return null;
        });

    assertThat(keyedCount(tenant)).isEqualTo(5);
  }

  /**
   * Transaction participation of the catalogue writes only. The tenant row is pre-created here; in
   * real onboarding it is committed separately (ONBOARD-ATOMIC-1), so this is not a tenant-rollback
   * proof. The real signup path is covered by TenantOnboardingIntegrationTest.
   */
  @Test
  void aLaterOnboardingFailureLeavesNoTaskTemplateRows() {
    UUID tenant = tenant("REGULAR");

    assertThatThrownBy(
            () ->
                inTenant(
                    tenant,
                    () -> {
                      onboardingStep.execute(context(tenant));
                      throw new IllegalStateException("a later onboarding step failed");
                    }))
        .hasMessageContaining("later onboarding step");

    assertThat(rowCount(tenant)).isZero();
  }

  /** As above: proves the writer's rows roll back with the caller, not the tenant row. */
  @Test
  void aProvisioningFailureAfterWritesLeavesNoTaskTemplateRows() {
    UUID tenant = tenant("REGULAR");
    List<CatalogueSourceRow> withoutLastEntry =
        reader.readCompleteCatalogue(GOLDEN).stream()
            .filter(row -> !row.catalogKey().equals(RECIPE.key()))
            .toList();

    // The writer processes entries in catalogue order; the recipe entry comes last, so four rows
    // are written before it fails.
    assertThatThrownBy(() -> inTenant(tenant, () -> writer.reconcile(tenant, withoutLastEntry)))
        .hasMessageContaining(RECIPE.key());

    assertThat(rowCount(tenant)).isZero();
  }

  @Test
  void writerRefusesToRunWithoutACallerTransaction() {
    UUID tenant = tenant("REGULAR");
    List<CatalogueSourceRow> golden = reader.readCompleteCatalogue(GOLDEN);

    TenantContext.executeInTenantContext(
        tenant,
        () ->
            assertThatThrownBy(() -> writer.reconcile(tenant, golden))
                .isInstanceOf(IllegalTransactionStateException.class));
    assertThat(rowCount(tenant)).isZero();
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  private CatalogueReconciliationResult reconcile(UUID tenant) {
    List<CatalogueSourceRow> golden = reader.readCompleteCatalogue(GOLDEN);
    return inTenant(tenant, () -> writer.reconcile(tenant, golden));
  }

  /** Binds the tenant before the primary transaction starts, as the backfill does. */
  private <T> T inTenant(UUID tenant, Supplier<T> work) {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    return TenantContext.executeInTenantContext(
        tenant,
        () -> {
          TenantContext.setCurrentTenantUid(uid(tenant));
          return transaction.execute(status -> work.get());
        });
  }

  private UUID tenant(String type) {
    UUID id = UUID.randomUUID();
    owner()
        .update(
            "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, billing_email,"
                + " status, settings, is_active, created_at, updated_at, version)"
                + " VALUES (?, ?, ?, ?, ?, 'tenancy@example.com', 'ACTIVE', '{}', true, now(),"
                + " now(), 0)",
            id,
            uid(id),
            "tenancy-" + id,
            "Tenancy " + id,
            type);
    return id;
  }

  private UUID playgroundSource() {
    List<UUID> existing =
        owner()
            .queryForList(
                "SELECT id FROM common_tenant.common_tenant WHERE slug = 'nexus-fabrics'",
                UUID.class);
    if (!existing.isEmpty()) {
      return existing.getFirst();
    }
    UUID id = UUID.randomUUID();
    owner()
        .update(
            "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, billing_email,"
                + " status, settings, is_active, created_at, updated_at, version)"
                + " VALUES (?, ?, 'nexus-fabrics', 'Nexus Fabrics', 'TEMPLATE',"
                + " 'nexus@example.com', 'ACTIVE', '{}', true, now(), now(), 0)",
            id,
            uid(id));
    return id;
  }

  private static String uid(UUID tenant) {
    return "TT-" + tenant.toString().substring(0, 8).toUpperCase();
  }

  private UUID globalBoard(UUID tenant) {
    UUID board = UUID.randomUUID();
    owner()
        .update(
            """
            INSERT INTO flowboard.board
              (id, tenant_id, uid, name, board_type, wip_limit_default, default_view_type,
               is_active, created_at, updated_at, version)
            VALUES (?, ?, ?, 'Tenancy board', 'GLOBAL', 5, 'KANBAN', true, now(), now(), 0)
            """,
            board,
            tenant,
            board.toString());
    return board;
  }

  private UUID insertSeedQuote(UUID tenant, int version) {
    UUID id = UUID.randomUUID();
    owner()
        .update(
            """
            INSERT INTO flowboard.task_template (id, tenant_id, uid, name, event_type,
                title_template, task_type, module_type, default_priority, default_assignee_role,
                estimated_hours, is_active, created_at, updated_at, version)
            VALUES (?, ?, gen_random_uuid()::varchar, 'Quote send approval', 'QuoteSendRequested',
                'Quote send approval - {quote.quoteNumber}', 'APPROVAL', 'GENERAL', 'HIGH', 'ANY',
                0.25, TRUE, now(), now(), ?)
            """,
            id,
            tenant,
            version);
    return id;
  }

  private OnboardingContext context(UUID tenant) {
    OnboardingContext context = new OnboardingContext();
    context.setTenantId(tenant);
    return context;
  }

  private List<Map<String, Object>> quoteRows(UUID tenant) {
    return owner()
        .queryForList(
            "SELECT id, catalog_key, created_by FROM flowboard.task_template"
                + " WHERE tenant_id = ? AND event_type = 'QuoteSendRequested'",
            tenant);
  }

  private List<Map<String, Object>> keyedRows(UUID tenant, TaskTemplateCatalogue entry) {
    return owner()
        .queryForList(
            "SELECT id, is_active, deleted_at FROM flowboard.task_template"
                + " WHERE tenant_id = ? AND catalog_key = ?",
            tenant,
            entry.key());
  }

  private int keyedCount(UUID tenant) {
    return owner()
        .queryForObject(
            "SELECT count(*) FROM flowboard.task_template WHERE tenant_id = ?"
                + " AND catalog_key IS NOT NULL",
            Integer.class,
            tenant);
  }

  private int rowCount(UUID tenant) {
    return owner()
        .queryForObject(
            "SELECT count(*) FROM flowboard.task_template WHERE tenant_id = ?",
            Integer.class,
            tenant);
  }
}
