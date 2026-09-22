package com.fabricmanagement.platform.dbprivilege;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.testsupport.PostgresImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class TablePrivilegeClassIT {
  private static final String BASELINE_TARGET = "20260915150000";
  private static final String ROLLBACK_SCRIPT =
      "db/rollback/V20260916120000_ROLLBACK__customer_commercial_assignment_privileges.sql";
  private static final String MEASUREMENT_SCRIPT = "db/privilege/effective-grants.sql";
  private static final String DEFAULT_ACL_BASELINE = "20260916120000";
  private static final String DEFAULT_ACL_VERSION = "20260916160000";
  private static final String DEFAULT_ACL_MIGRATION =
      "db/migration/V" + DEFAULT_ACL_VERSION + "__close_default_table_privileges.sql";
  private static final String DEFAULT_ACL_ROLLBACK =
      "db/rollback/V" + DEFAULT_ACL_VERSION + "_ROLLBACK__close_default_table_privileges.sql";
  private static final String FINALISED_PRIVILEGES_VERSION = "20260917100000";
  private static final String FINALISED_PRIVILEGES_MIGRATION =
      "db/migration/V" + FINALISED_PRIVILEGES_VERSION + "__finalise_ledger_privileges.sql";
  private static final String FINALISED_PRIVILEGES_ROLLBACK =
      "db/rollback/V" + FINALISED_PRIVILEGES_VERSION + "_ROLLBACK__finalise_ledger_privileges.sql";
  private static final String OWNER_PASSWORD = "owner_test";

  private static final String TENANT = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
  private static final String OTHER_TENANT = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
  private static final String CCA_ID = "aaaaaaaa-1000-4000-8000-000000000001";
  private static final String CCA_CUSTOMER = "aaaaaaaa-1000-4000-8000-000000000002";
  private static final String CCA_REPRESENTATIVE = "aaaaaaaa-1000-4000-8000-000000000003";
  private static final String CCA_OTHER_REPRESENTATIVE = "aaaaaaaa-1000-4000-8000-000000000004";
  private static final String QUALITY_DECISION = "aaaaaaaa-2000-4000-8000-000000000001";
  private static final String QUALITY_BATCH = "aaaaaaaa-2000-4000-8000-000000000002";
  private static final String QUALITY_UNIT = "aaaaaaaa-2000-4000-8000-000000000003";
  private static final String QUALITY_ACTOR = "aaaaaaaa-2000-4000-8000-000000000004";
  private static final String ORDER = "aaaaaaaa-3000-4000-8000-000000000001";
  private static final String CASE = "aaaaaaaa-3000-4000-8000-000000000002";
  private static final String STREAM = "aaaaaaaa-3000-4000-8000-000000000003";
  private static final String EVIDENCE = "aaaaaaaa-3000-4000-8000-000000000004";
  private static final String DECISION_PROJECTION = "aaaaaaaa-4000-4000-8000-000000000001";
  private static final String DECISION_CASE = "aaaaaaaa-4000-4000-8000-000000000002";
  private static final String OTHER_DECISION_PROJECTION = "bbbbbbbb-4000-4000-8000-000000000001";
  private static final String OTHER_DECISION_CASE = "bbbbbbbb-4000-4000-8000-000000000002";

  private static final String CATALOGUE_QUERY =
      """
      SELECT n.nspname AS schema_name,
             c.relname AS relation_name,
             c.relkind,
             runtime_role.role_name,
             has_table_privilege(runtime_role.role_name, c.oid, 'SELECT') AS select_granted,
             has_table_privilege(runtime_role.role_name, c.oid, 'INSERT') AS insert_granted,
             has_table_privilege(runtime_role.role_name, c.oid, 'UPDATE') AS update_granted,
             has_table_privilege(runtime_role.role_name, c.oid, 'DELETE') AS delete_granted,
             has_table_privilege(runtime_role.role_name, c.oid, 'TRUNCATE') AS truncate_granted,
             has_table_privilege(runtime_role.role_name, c.oid, 'REFERENCES')
               AS references_granted,
             has_table_privilege(runtime_role.role_name, c.oid, 'TRIGGER') AS trigger_granted,
             has_table_privilege(
               runtime_role.role_name, c.oid, 'SELECT WITH GRANT OPTION') AS select_option,
             has_table_privilege(
               runtime_role.role_name, c.oid, 'INSERT WITH GRANT OPTION') AS insert_option,
             has_table_privilege(
               runtime_role.role_name, c.oid, 'UPDATE WITH GRANT OPTION') AS update_option,
             has_table_privilege(
               runtime_role.role_name, c.oid, 'DELETE WITH GRANT OPTION') AS delete_option,
             has_table_privilege(
               runtime_role.role_name, c.oid, 'TRUNCATE WITH GRANT OPTION') AS truncate_option,
             has_table_privilege(
               runtime_role.role_name, c.oid, 'REFERENCES WITH GRANT OPTION')
               AS references_option,
             has_table_privilege(
               runtime_role.role_name, c.oid, 'TRIGGER WITH GRANT OPTION') AS trigger_option
      FROM pg_class c
      JOIN pg_namespace n ON n.oid = c.relnamespace
      CROSS JOIN (VALUES ('fabric_app'::name), ('fabric_system'::name))
        runtime_role(role_name)
      WHERE c.relkind IN ('r', 'p', 'v', 'm')
        AND n.nspname NOT IN ('pg_catalog', 'information_schema', 'pg_toast')
      ORDER BY n.nspname, c.relname, runtime_role.role_name
      """;

  @Container
  static final PostgreSQLContainer<?> FULL_POSTGRES =
      PostgresImage.container()
          .withDatabaseName("table_privilege_full")
          .withUsername("test_admin")
          .withPassword("fabric123");

  @Container
  static final PostgreSQLContainer<?> BASELINE_POSTGRES =
      PostgresImage.container()
          .withDatabaseName("table_privilege_baseline")
          .withUsername("test_admin")
          .withPassword("fabric123");

  @Container
  static final PostgreSQLContainer<?> UPGRADE_POSTGRES =
      PostgresImage.container()
          .withDatabaseName("table_privilege_upgrade")
          .withUsername("test_admin")
          .withPassword("fabric123");

  @BeforeAll
  static void migrateAndSeed() throws SQLException {
    createRuntimeRoles(FULL_POSTGRES);
    createRuntimeRoles(BASELINE_POSTGRES);
    createRuntimeRoles(UPGRADE_POSTGRES);
    migrate(FULL_POSTGRES, null);
    migrate(BASELINE_POSTGRES, BASELINE_TARGET);
    seedFullMigrationFixtures();
  }

  @Test
  void pinnedTargetReportsThreeHistoricalPrivilegeSurpluses() throws SQLException {
    try (Connection connection = ownerConnection(BASELINE_POSTGRES)) {
      assertThat(pinnedBaselineDifferences(connection))
          .containsExactly(
              difference("sales.customer_commercial_assignment", "fabric_app", "surplus: DELETE"),
              difference(
                  "sales.customer_commercial_assignment", "fabric_system", "surplus: UPDATE"),
              difference("sales_ord.order_cover_evidence_stream", "fabric_app", "surplus: DELETE"));
    }
  }

  @Test
  void fullMigrationMatchesEveryDeclaredPrivilegeClassAndRelationKind() throws SQLException {
    try (Connection connection = ownerConnection(FULL_POSTGRES)) {
      List<MeasuredRelation> measured = measure(connection);
      TablePrivilegeComparator.assertNoDifferences(
          TablePrivilegeComparator.compare(
              measured, TablePrivilegeClassification.declaredEntries()));

      MeasuredRelation jobRunrView =
          measured.stream()
              .filter(relation -> relation.relation().equals("public.jobrunr_jobs_stats"))
              .findFirst()
              .orElseThrow();
      assertThat(jobRunrView.relkind()).isEqualTo('v');
    }
  }

  @Test
  void measurementSqlDeclaresExactlyTheJavaClassification() {
    String sql = readClasspathScript(MEASUREMENT_SCRIPT);
    var classification =
        Pattern.compile(
                "CASE\\s+schema_name\\s*\\|\\|\\s*'\\.'\\s*\\|\\|\\s*relation_name"
                    + "(.*?)ELSE\\s+'([^']+)'\\s+END\\s+AS\\s+declared_class",
                Pattern.DOTALL)
            .matcher(sql);
    assertThat(classification.find())
        .as("Measurement SQL must declare its classification CASE")
        .isTrue();
    String branches = classification.group(1);
    assertThat(classification.group(2))
        .as("Measurement SQL default class")
        .isEqualTo(TablePrivilegeClass.MUTABLE.name());
    assertThat(classification.find()).as("Only one classification CASE is allowed").isFalse();

    var branch = Pattern.compile("WHEN\\s+'([^']+)'\\s+THEN\\s+'([^']+)'").matcher(branches);
    Map<String, String> sqlEntries = new LinkedHashMap<>();
    while (branch.find()) {
      String relation = branch.group(1);
      assertThat(sqlEntries.putIfAbsent(relation, branch.group(2)))
          .as("Duplicate classification in measurement SQL: %s", relation)
          .isNull();
    }
    assertThat(branch.replaceAll("").isBlank())
        .as("Every classification branch must be a literal relation-to-class mapping")
        .isTrue();
    Map<String, String> javaEntries = new LinkedHashMap<>();
    TablePrivilegeClassification.declaredEntries()
        .forEach((relation, privilegeClass) -> javaEntries.put(relation, privilegeClass.name()));
    assertThat(sqlEntries)
        .as("Measurement SQL classifications must match TablePrivilegeClassification")
        .containsExactlyInAnyOrderEntriesOf(javaEntries);
  }

  @Test
  void measurementSqlClassifiesEveryCatalogueRelationLikeJava() throws SQLException {
    try (Connection connection = ownerConnection(FULL_POSTGRES);
        Statement statement = connection.createStatement()) {
      Map<String, Map<RuntimeDatabaseRole, String>> expected = new LinkedHashMap<>();
      for (MeasuredRelation relation : measure(connection)) {
        Map<RuntimeDatabaseRole, String> roles = new EnumMap<>(RuntimeDatabaseRole.class);
        for (RuntimeDatabaseRole role : RuntimeDatabaseRole.values()) {
          roles.put(role, TablePrivilegeClassification.classFor(relation.relation()).name());
        }
        expected.put(relation.relation(), roles);
      }
      assertThat(expected).as("Full migration catalogue must not be empty").isNotEmpty();
      // Execute the operator's resource unchanged; skip Q0 context to read Q1 classifications.
      assertThat(statement.execute(readClasspathScript(MEASUREMENT_SCRIPT))).isTrue();
      assertThat(statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT)).isTrue();
      Map<String, Map<RuntimeDatabaseRole, String>> actual = new LinkedHashMap<>();
      try (ResultSet result = statement.getResultSet()) {
        while (result.next()) {
          String relation =
              result.getString("schema_name") + "." + result.getString("relation_name");
          RuntimeDatabaseRole role =
              RuntimeDatabaseRole.fromDatabaseName(result.getString("role_name"));
          Map<RuntimeDatabaseRole, String> roles =
              actual.computeIfAbsent(relation, ignored -> new EnumMap<>(RuntimeDatabaseRole.class));
          assertThat(roles.containsKey(role))
              .as("Duplicate measurement row: %s / %s", relation, role.databaseName())
              .isFalse();
          roles.put(role, result.getString("declared_class"));
        }
      }
      assertThat(actual)
          .as("Operator SQL must classify every catalogue relation for both runtime roles")
          .containsExactlyInAnyOrderEntriesOf(expected);
    }
  }

  @Test
  void applicationRoleIsDeniedBeforeEveryAppendOnlyTrigger() throws SQLException {
    for (String sql : appendOnlyMutations()) {
      assertAppSqlState(sql, "42501");
    }
  }

  @Test
  void ownerReachesAndIsRejectedByEveryAppendOnlyTrigger() throws SQLException {
    for (String sql : appendOnlyMutations()) {
      try (Connection connection = ownerConnection(FULL_POSTGRES)) {
        assertSqlState(connection, sql, "55000");
      }
    }
  }

  @Test
  void systemPurgeRequiresMatchingTenantForEveryLedger() throws SQLException {
    List<PurgeMutation> mutations =
        List.of(
            new PurgeMutation(
                "app.quality_decision_purge_tenant",
                "DELETE FROM production.quality_decision_unit WHERE decision_id = '"
                    + QUALITY_DECISION
                    + "'"),
            new PurgeMutation(
                "app.quality_decision_purge_tenant",
                "DELETE FROM production.quality_decision WHERE id = '" + QUALITY_DECISION + "'"),
            new PurgeMutation(
                "app.order_cover_evidence_purge_tenant",
                "DELETE FROM sales_ord.order_cover_evidence WHERE id = '" + EVIDENCE + "'"),
            new PurgeMutation(
                "app.customer_commercial_assignment_purge_tenant",
                "DELETE FROM sales.customer_commercial_assignment WHERE id = '" + CCA_ID + "'"));

    for (PurgeMutation mutation : mutations) {
      assertSystemPurgeRejected(mutation, null);
      assertSystemPurgeRejected(mutation, OTHER_TENANT);
    }

    assertSystemPurgeSucceeds(
        "app.quality_decision_purge_tenant",
        List.of(
            "DELETE FROM production.quality_decision_unit WHERE decision_id = '"
                + QUALITY_DECISION
                + "'",
            "DELETE FROM production.quality_decision WHERE id = '" + QUALITY_DECISION + "'"));
    assertSystemPurgeSucceeds(
        "app.order_cover_evidence_purge_tenant",
        List.of("DELETE FROM sales_ord.order_cover_evidence WHERE id = '" + EVIDENCE + "'"));
    assertSystemPurgeSucceeds(
        "app.customer_commercial_assignment_purge_tenant",
        List.of("DELETE FROM sales.customer_commercial_assignment WHERE id = '" + CCA_ID + "'"));
  }

  @Test
  void customerAssignmentDeleteStopsAtPrivilegeForAppAndAtTriggerForOwner() throws SQLException {
    assertAppSqlState(
        "DELETE FROM sales.customer_commercial_assignment WHERE id = '" + CCA_ID + "'", "42501");
    try (Connection connection = ownerConnection(FULL_POSTGRES)) {
      assertSqlState(
          connection,
          "DELETE FROM sales.customer_commercial_assignment WHERE id = '" + CCA_ID + "'",
          "55000");
    }
  }

  @Test
  void systemCannotUpdateCustomerAssignmentEvenWithAValidDomainClosure() throws SQLException {
    try (Connection connection = systemConnection()) {
      connection.setAutoCommit(false);
      try {
        assertSqlState(connection, fullClosureSql(CCA_REPRESENTATIVE), "42501");
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void appCannotDeleteOrderCoverEvidenceStream() throws SQLException {
    try (Connection connection = appConnection()) {
      connection.setAutoCommit(false);
      try {
        assertSqlState(
            connection,
            "DELETE FROM sales_ord.order_cover_evidence_stream WHERE id = '" + STREAM + "'",
            "42501");
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void decisionProjectionForcesTenantRlsForTheApplicationRole() throws SQLException {
    try (Connection owner = ownerConnection(FULL_POSTGRES);
        Statement statement = owner.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                select relrowsecurity, relforcerowsecurity
                from pg_class c join pg_namespace n on n.oid=c.relnamespace
                where n.nspname='flowboard' and c.relname='decision_subject_projection'
                """)) {
      assertThat(result.next()).isTrue();
      assertThat(result.getBoolean("relrowsecurity")).isTrue();
      assertThat(result.getBoolean("relforcerowsecurity")).isTrue();
    }

    try (Connection connection = appConnection()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        try (ResultSet rows =
            statement.executeQuery(
                "select case_id from flowboard.decision_subject_projection where case_id in ('"
                    + DECISION_CASE
                    + "','"
                    + OTHER_DECISION_CASE
                    + "') order by case_id")) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getString(1)).isEqualTo(DECISION_CASE);
          assertThat(rows.next()).isFalse();
        }
        assertSqlState(
            connection,
            decisionProjectionInsert(
                "bbbbbbbb-4000-4000-8000-000000000003",
                OTHER_TENANT,
                "bbbbbbbb-4000-4000-8000-000000000004",
                "DB-PRIV-DSP-CROSS"),
            "42501");
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void systemCanPurgeOrderCoverEvidenceBeforeItsStream() throws SQLException {
    assertSystemPurgeSucceeds(
        "app.order_cover_evidence_purge_tenant",
        List.of(
            "DELETE FROM sales_ord.order_cover_evidence WHERE id = '" + EVIDENCE + "'",
            "DELETE FROM sales_ord.order_cover_evidence_stream WHERE id = '" + STREAM + "'"));
  }

  @Test
  void customerAssignmentAcceptsTheFullDomainShapedClosure() throws SQLException {
    try (Connection connection = appConnection()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        assertThat(statement.executeUpdate(fullClosureSql(CCA_REPRESENTATIVE))).isEqualTo(1);
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void customerAssignmentRejectsASecondClosure() throws SQLException {
    try (Connection connection = appConnection()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        assertThat(statement.executeUpdate(fullClosureSql(CCA_REPRESENTATIVE))).isEqualTo(1);
        assertThatThrownBy(
                () ->
                    statement.executeUpdate(
                        "UPDATE sales.customer_commercial_assignment "
                            + "SET updated_at = now(), version = version + 1 WHERE id = '"
                            + CCA_ID
                            + "'"))
            .isInstanceOfSatisfying(
                SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("55000"));
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void customerAssignmentRejectsClosureThatChangesAProtectedColumn() throws SQLException {
    try (Connection connection = appConnection()) {
      connection.setAutoCommit(false);
      try {
        assertSqlState(connection, fullClosureSql(CCA_OTHER_REPRESENTATIVE), "55000");
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void customerAssignmentRejectsClosureColumnsWithoutValidTo() throws SQLException {
    try (Connection connection = appConnection()) {
      connection.setAutoCommit(false);
      try {
        assertSqlState(
            connection,
            """
            UPDATE sales.customer_commercial_assignment
            SET closure_reason = 'SUPERSEDED',
                closed_by_type = 'SYSTEM',
                closed_by_system_code = 'OWNERSHIP_POLICY',
                updated_at = now(),
                version = version + 1
            WHERE id = '%s'
            """
                .formatted(CCA_ID),
            "55000");
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void catalogueReaderDetectsTransactionalPublicInheritanceMissingAndGrantOptionProbes()
      throws SQLException {
    assertProbe(
        List.of("GRANT UPDATE ON production.quality_decision TO PUBLIC"),
        List.of(
            difference("production.quality_decision", "fabric_app", "surplus: UPDATE"),
            difference("production.quality_decision", "fabric_system", "surplus: UPDATE")));

    assertProbe(
        List.of(
            "CREATE ROLE probe_parent NOLOGIN",
            "GRANT UPDATE ON production.quality_decision TO probe_parent",
            "GRANT probe_parent TO fabric_app"),
        List.of(difference("production.quality_decision", "fabric_app", "surplus: UPDATE")));

    assertProbe(
        List.of("REVOKE SELECT ON public.processed_event FROM fabric_app"),
        List.of(difference("public.processed_event", "fabric_app", "missing: SELECT")));

    assertProbe(
        List.of("GRANT SELECT ON public.processed_event TO fabric_app WITH GRANT OPTION"),
        List.of(difference("public.processed_event", "fabric_app", "grant option: SELECT")));

    try (Connection connection = ownerConnection(FULL_POSTGRES)) {
      assertThat(differences(connection)).isEmpty();
    }
  }

  @Test
  void rollbackRestoresTheSingleCustomerAssignmentDivergence() throws SQLException {
    try (Connection connection = ownerConnection(FULL_POSTGRES)) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(readClasspathScript(ROLLBACK_SCRIPT));
        assertThat(differences(connection))
            .containsExactly(
                difference(
                    "sales.customer_commercial_assignment", "fabric_app", "surplus: DELETE"));
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void finalisedPrivilegeRollbackRestoresOnlyTwoSurplusesAndReapplicationClosesThem()
      throws SQLException {
    try (Connection connection = ownerConnection(FULL_POSTGRES)) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute(readClasspathScript(FINALISED_PRIVILEGES_ROLLBACK));
        assertThat(differences(connection))
            .containsExactly(
                difference(
                    "sales.customer_commercial_assignment", "fabric_system", "surplus: UPDATE"),
                difference(
                    "sales_ord.order_cover_evidence_stream", "fabric_app", "surplus: DELETE"));
        statement.execute(readClasspathScript(FINALISED_PRIVILEGES_MIGRATION));
        assertThat(differences(connection)).isEmpty();
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void latestDefaultAclGuardIsEmpty() throws SQLException {
    try (Connection connection = ownerConnection(FULL_POSTGRES)) {
      TablePrivilegeComparator.assertNoDifferences(DefaultAclGuard.differences(connection));
    }
  }

  @Test
  void defaultAclUpgradeRollbackAndReapplicationPreserveExistingPrivilegeVectors()
      throws SQLException {
    migrate(UPGRADE_POSTGRES, DEFAULT_ACL_BASELINE);
    Set<DefaultAclGuard.Entry> historical = historicalDefaults();
    List<MeasuredRelation> baseline;
    try (Connection connection = ownerConnection(UPGRADE_POSTGRES)) {
      assertThat(DefaultAclGuard.violations(connection)).isEqualTo(historical);
      baseline = measure(connection);
    }

    // This upper pin must not follow latest: later tickets intentionally alter table grants.
    migrate(UPGRADE_POSTGRES, DEFAULT_ACL_VERSION);
    try (Connection connection = ownerConnection(UPGRADE_POSTGRES);
        Statement statement = connection.createStatement()) {
      assertThat(measure(connection)).containsExactlyInAnyOrderElementsOf(baseline);
      assertThat(DefaultAclGuard.violations(connection)).isEmpty();
      Set<DefaultAclGuard.Entry> afterUpgrade = DefaultAclGuard.snapshot(connection);

      statement.execute(readClasspathScript(DEFAULT_ACL_ROLLBACK));
      assertThat(measure(connection)).containsExactlyInAnyOrderElementsOf(baseline);
      Set<DefaultAclGuard.Entry> afterRollback = DefaultAclGuard.snapshot(connection);
      Set<DefaultAclGuard.Entry> expectedRollback = new LinkedHashSet<>(afterUpgrade);
      expectedRollback.addAll(historical);
      assertThat(afterRollback).isEqualTo(expectedRollback);
      assertThat(DefaultAclGuard.violations(connection)).isEqualTo(historical);

      statement.execute(readClasspathScript(DEFAULT_ACL_MIGRATION));
      assertThat(DefaultAclGuard.violations(connection)).isEmpty();
      assertThat(DefaultAclGuard.snapshot(connection)).isEqualTo(afterUpgrade);
      assertThat(measure(connection)).containsExactlyInAnyOrderElementsOf(baseline);

      // Execute the body again, rather than asking Flyway to skip an applied version.
      statement.execute(readClasspathScript(DEFAULT_ACL_MIGRATION));
      assertThat(DefaultAclGuard.snapshot(connection)).isEqualTo(afterUpgrade);
    }
  }

  @Test
  void newOwnerTablesHaveNoRuntimePrivilegesInEveryMigrationSchemaAndPublic() throws SQLException {
    inDefaultTransaction(
        false,
        connection -> {
          Set<String> schemas = new LinkedHashSet<>(defaultAclSchemas());
          schemas.add("public");
          try (Statement statement = connection.createStatement()) {
            for (String schema : schemas) {
              String relation = schema + ".db_priv_2_probe";
              statement.execute("CREATE TABLE " + relation + " (id bigint)");
              assertTableProbe(connection, new TableProbe(relation, Set.of(), Set.of()));
            }
          }
        });
  }

  @Test
  void newTablesRequireExplicitClassGrants() throws SQLException {
    inDefaultTransaction(
        false,
        connection -> {
          String mutable = "sales.db_priv_2_mutable";
          String ledger = "sales.db_priv_2_ledger";
          try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + mutable + " (id bigint)");
            statement.execute("CREATE TABLE " + ledger + " (id bigint)");
            List<PrivilegeDifference> missing = new ArrayList<>();
            for (RuntimeDatabaseRole role : RuntimeDatabaseRole.values()) {
              for (TablePrivilege privilege :
                  TablePrivilegeClass.MUTABLE.expected(role).granted()) {
                missing.add(difference(mutable, role.databaseName(), "missing: " + privilege));
              }
            }
            assertThat(TablePrivilegeComparator.compare(measureOnly(connection, mutable), Map.of()))
                .containsExactlyInAnyOrderElementsOf(missing);

            statement.execute(
                "GRANT SELECT, INSERT, UPDATE, DELETE ON "
                    + mutable
                    + " TO fabric_app, fabric_system");
            assertThat(TablePrivilegeComparator.compare(measureOnly(connection, mutable), Map.of()))
                .isEmpty();

            statement.execute("GRANT SELECT, INSERT ON " + ledger + " TO fabric_app");
            statement.execute("GRANT SELECT, INSERT, DELETE ON " + ledger + " TO fabric_system");
            assertThat(
                    TablePrivilegeComparator.compare(
                        measureOnly(connection, ledger),
                        Map.of(ledger, TablePrivilegeClass.APPEND_ONLY_LEDGER)))
                .isEmpty();
          }
        });
  }

  @Test
  void defaultAclGuardDetectsExactOwnerGlobalPublicAndMembershipViolations() throws SQLException {
    // (a) Owner-scoped default.
    assertAclProbe(
        false,
        List.of("ALTER DEFAULT PRIVILEGES IN SCHEMA sales GRANT SELECT ON TABLES TO fabric_app"),
        Set.of(defaultEntry("fabric_owner", "sales", "fabric_app", "SELECT")));
    assertAclProbe(
        false,
        List.of(
            "ALTER DEFAULT PRIVILEGES IN SCHEMA sales "
                + "GRANT SELECT ON TABLES TO fabric_app WITH GRANT OPTION"),
        Set.of(
            new DefaultAclGuard.Entry(
                "fabric_owner", "sales", "TABLES", "fabric_app", "SELECT", true)));
    // (b) Global default.
    assertAclProbe(
        false,
        List.of("ALTER DEFAULT PRIVILEGES GRANT SELECT ON TABLES TO fabric_system"),
        Set.of(defaultEntry("fabric_owner", "<global>", "fabric_system", "SELECT")));
    // (c) PUBLIC reaches both runtime roles.
    assertAclProbe(
        false,
        List.of("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO PUBLIC"),
        Set.of(defaultEntry("fabric_owner", "public", "PUBLIC", "SELECT")),
        new TableProbe(
            "public.db_priv_2_probe",
            Set.of(TablePrivilege.SELECT),
            Set.of(TablePrivilege.SELECT)));
    // (d) A foreign creator's defaults cannot be ignored.
    Set<DefaultAclGuard.Entry> foreignDefaults = new LinkedHashSet<>();
    for (TablePrivilege privilege :
        TablePrivilegeClass.MUTABLE.expected(RuntimeDatabaseRole.FABRIC_APP).granted()) {
      foreignDefaults.add(defaultEntry("probe_owner", "sales", "fabric_app", privilege.name()));
    }
    assertAclProbe(
        true,
        List.of(
            "CREATE ROLE probe_owner NOLOGIN",
            "ALTER DEFAULT PRIVILEGES FOR ROLE probe_owner IN SCHEMA sales "
                + "GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO fabric_app"),
        foreignDefaults);
    // (e) Direct parent.
    assertAclProbe(
        true,
        List.of(
            "CREATE ROLE probe_parent NOLOGIN",
            "GRANT probe_parent TO fabric_app",
            "ALTER DEFAULT PRIVILEGES FOR ROLE fabric_owner IN SCHEMA sales "
                + "GRANT UPDATE ON TABLES TO probe_parent"),
        Set.of(defaultEntry("fabric_owner", "sales", "probe_parent", "UPDATE")),
        new TableProbe("sales.db_priv_2_probe", Set.of(TablePrivilege.UPDATE), Set.of()));
    // (f), (g) Two-level closure, including an edge which does not inherit privileges.
    for (boolean inherit : List.of(true, false)) {
      assertAclProbe(
          true,
          List.of(
              "CREATE ROLE probe_parent NOLOGIN",
              "CREATE ROLE probe_grandparent NOLOGIN",
              "GRANT probe_grandparent TO probe_parent",
              "GRANT probe_parent TO fabric_system WITH INHERIT " + inherit,
              "ALTER DEFAULT PRIVILEGES FOR ROLE fabric_owner IN SCHEMA sales "
                  + "GRANT DELETE ON TABLES TO probe_grandparent"),
          Set.of(defaultEntry("fabric_owner", "sales", "probe_grandparent", "DELETE")),
          new TableProbe(
              "sales.db_priv_2_probe",
              Set.of(),
              inherit ? Set.of(TablePrivilege.DELETE) : Set.of()));
    }
    // (h) Non-table defaults are listed, but are not violations.
    inDefaultTransaction(
        true,
        connection -> {
          try (Statement statement = connection.createStatement()) {
            statement.execute(
                "ALTER DEFAULT PRIVILEGES IN SCHEMA sales GRANT EXECUTE ON FUNCTIONS TO fabric_app");
          }
          assertThat(DefaultAclGuard.violations(connection)).isEmpty();
          assertThat(DefaultAclGuard.snapshot(connection))
              .contains(
                  new DefaultAclGuard.Entry(
                      "test_admin", "sales", "FUNCTIONS", "fabric_app", "EXECUTE", false));
        });
    try (Connection connection = ownerConnection(FULL_POSTGRES)) {
      TablePrivilegeComparator.assertNoDifferences(DefaultAclGuard.differences(connection));
    }
  }

  private static void assertAclProbe(
      boolean admin,
      List<String> statements,
      Set<DefaultAclGuard.Entry> expected,
      TableProbe... tables)
      throws SQLException {
    inDefaultTransaction(
        admin,
        connection -> {
          try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
              statement.execute(sql);
            }
            assertThat(DefaultAclGuard.violations(connection)).isEqualTo(expected);
            assertThat(DefaultAclGuard.differences(connection))
                .containsExactlyElementsOf(
                    expected.stream().map(DefaultAclGuard.Entry::difference).sorted().toList());
            for (TableProbe table : tables) {
              if (admin) {
                statement.execute("SET LOCAL ROLE fabric_owner");
              }
              statement.execute("CREATE TABLE " + table.relation() + " (id bigint)");
              if (admin) {
                statement.execute("RESET ROLE");
              }
              assertTableProbe(connection, table);
            }
          }
        });
  }

  private static void assertTableProbe(Connection connection, TableProbe table)
      throws SQLException {
    List<MeasuredRelation> measured = measureOnly(connection, table.relation());
    MeasuredRelation relation = measured.getFirst();
    assertThat(relation.privileges())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                RuntimeDatabaseRole.FABRIC_APP, MeasuredPrivileges.of(table.app()),
                RuntimeDatabaseRole.FABRIC_SYSTEM, MeasuredPrivileges.of(table.system())));
    List<PrivilegeDifference> expected = new ArrayList<>();
    table
        .app()
        .forEach(
            privilege ->
                expected.add(difference(table.relation(), "fabric_app", "surplus: " + privilege)));
    table
        .system()
        .forEach(
            privilege ->
                expected.add(
                    difference(table.relation(), "fabric_system", "surplus: " + privilege)));
    assertThat(
            TablePrivilegeComparator.compare(
                measured, Map.of(table.relation(), TablePrivilegeClass.NO_RUNTIME_ACCESS)))
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  private static List<MeasuredRelation> measureOnly(Connection connection, String relation)
      throws SQLException {
    List<MeasuredRelation> measured =
        measure(connection).stream().filter(row -> row.relation().equals(relation)).toList();
    assertThat(measured).as("Catalogue contains probe table %s", relation).hasSize(1);
    return measured;
  }

  private static Set<String> defaultAclSchemas() {
    var array =
        Pattern.compile("schemas\\s+text\\[\\]\\s*:=\\s*ARRAY\\[(.*?)]", Pattern.DOTALL)
            .matcher(readClasspathScript(DEFAULT_ACL_MIGRATION));
    assertThat(array.find()).as("Migration must declare its schema array").isTrue();
    var literals = Pattern.compile("'([^']+)'").matcher(array.group(1));
    Set<String> schemas = new LinkedHashSet<>();
    while (literals.find()) {
      schemas.add(literals.group(1));
    }
    assertThat(schemas).hasSize(22).doesNotContain("public");
    return schemas;
  }

  private static Set<DefaultAclGuard.Entry> historicalDefaults() {
    Set<DefaultAclGuard.Entry> historical = new LinkedHashSet<>();
    for (String schema : defaultAclSchemas()) {
      for (RuntimeDatabaseRole role : RuntimeDatabaseRole.values()) {
        for (TablePrivilege privilege : TablePrivilegeClass.MUTABLE.expected(role).granted()) {
          historical.add(
              defaultEntry("fabric_owner", schema, role.databaseName(), privilege.name()));
        }
      }
    }
    return historical;
  }

  private static DefaultAclGuard.Entry defaultEntry(
      String owner, String schema, String grantee, String privilege) {
    return new DefaultAclGuard.Entry(owner, schema, "TABLES", grantee, privilege, false);
  }

  private static void inDefaultTransaction(boolean admin, SqlProbe probe) throws SQLException {
    try (Connection connection =
        admin ? adminConnection(FULL_POSTGRES) : ownerConnection(FULL_POSTGRES)) {
      connection.setAutoCommit(false);
      try {
        probe.run(connection);
      } finally {
        connection.rollback();
      }
    }
  }

  @FunctionalInterface
  private interface SqlProbe {
    void run(Connection connection) throws SQLException;
  }

  private record TableProbe(String relation, Set<TablePrivilege> app, Set<TablePrivilege> system) {}

  private static void assertProbe(List<String> probeStatements, List<PrivilegeDifference> expected)
      throws SQLException {
    try (Connection connection = adminConnection(FULL_POSTGRES)) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        for (String probeStatement : probeStatements) {
          statement.execute(probeStatement);
        }
        assertThat(differences(connection)).containsExactlyElementsOf(expected);
      } finally {
        connection.rollback();
      }
    }
  }

  private static List<PrivilegeDifference> differences(Connection connection) throws SQLException {
    return TablePrivilegeComparator.compare(
        measure(connection), TablePrivilegeClassification.declaredEntries());
  }

  private static List<PrivilegeDifference> pinnedBaselineDifferences(Connection connection)
      throws SQLException {
    List<MeasuredRelation> measured = measure(connection);
    Set<String> baselineRelations =
        measured.stream()
            .map(MeasuredRelation::relation)
            .collect(java.util.stream.Collectors.toSet());
    Map<String, TablePrivilegeClass> baselineClassifications = new LinkedHashMap<>();
    TablePrivilegeClassification.declaredEntries()
        .forEach(
            (relation, privilegeClass) -> {
              if (baselineRelations.contains(relation)) {
                baselineClassifications.put(relation, privilegeClass);
              }
            });
    return TablePrivilegeComparator.compare(measured, baselineClassifications);
  }

  private static List<MeasuredRelation> measure(Connection connection) throws SQLException {
    Map<String, RelationMeasurement> relations = new LinkedHashMap<>();
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(CATALOGUE_QUERY)) {
      while (result.next()) {
        String relation = result.getString("schema_name") + "." + result.getString("relation_name");
        RelationMeasurement measurement =
            relations.computeIfAbsent(
                relation,
                ignored ->
                    new RelationMeasurement(
                        relation, resultRelkind(result), new EnumMap<>(RuntimeDatabaseRole.class)));
        RuntimeDatabaseRole role =
            RuntimeDatabaseRole.fromDatabaseName(result.getString("role_name"));
        measurement.privileges().put(role, measuredPrivileges(result));
      }
    }
    return relations.values().stream()
        .map(
            measurement ->
                new MeasuredRelation(
                    measurement.relation(), measurement.relkind(), measurement.privileges()))
        .toList();
  }

  private static char resultRelkind(ResultSet result) {
    try {
      return result.getString("relkind").charAt(0);
    } catch (SQLException exception) {
      throw new IllegalStateException("Could not read relation kind", exception);
    }
  }

  private static MeasuredPrivileges measuredPrivileges(ResultSet result) throws SQLException {
    EnumSet<TablePrivilege> granted = EnumSet.noneOf(TablePrivilege.class);
    EnumSet<TablePrivilege> grantOptions = EnumSet.noneOf(TablePrivilege.class);
    addIfTrue(result, "select_granted", TablePrivilege.SELECT, granted);
    addIfTrue(result, "insert_granted", TablePrivilege.INSERT, granted);
    addIfTrue(result, "update_granted", TablePrivilege.UPDATE, granted);
    addIfTrue(result, "delete_granted", TablePrivilege.DELETE, granted);
    addIfTrue(result, "truncate_granted", TablePrivilege.TRUNCATE, granted);
    addIfTrue(result, "references_granted", TablePrivilege.REFERENCES, granted);
    addIfTrue(result, "trigger_granted", TablePrivilege.TRIGGER, granted);
    addIfTrue(result, "select_option", TablePrivilege.SELECT, grantOptions);
    addIfTrue(result, "insert_option", TablePrivilege.INSERT, grantOptions);
    addIfTrue(result, "update_option", TablePrivilege.UPDATE, grantOptions);
    addIfTrue(result, "delete_option", TablePrivilege.DELETE, grantOptions);
    addIfTrue(result, "truncate_option", TablePrivilege.TRUNCATE, grantOptions);
    addIfTrue(result, "references_option", TablePrivilege.REFERENCES, grantOptions);
    addIfTrue(result, "trigger_option", TablePrivilege.TRIGGER, grantOptions);
    return new MeasuredPrivileges(granted, grantOptions);
  }

  private static void addIfTrue(
      ResultSet result, String column, TablePrivilege privilege, Set<TablePrivilege> destination)
      throws SQLException {
    if (result.getBoolean(column)) {
      destination.add(privilege);
    }
  }

  private static List<String> appendOnlyMutations() {
    return List.of(
        "UPDATE production.quality_decision SET remarks = remarks WHERE id = '"
            + QUALITY_DECISION
            + "'",
        "DELETE FROM production.quality_decision WHERE id = '" + QUALITY_DECISION + "'",
        "UPDATE production.quality_decision_unit SET stock_unit_id = stock_unit_id "
            + "WHERE decision_id = '"
            + QUALITY_DECISION
            + "'",
        "DELETE FROM production.quality_decision_unit WHERE decision_id = '"
            + QUALITY_DECISION
            + "'",
        "UPDATE sales_ord.order_cover_evidence SET revision = revision WHERE id = '"
            + EVIDENCE
            + "'",
        "DELETE FROM sales_ord.order_cover_evidence WHERE id = '" + EVIDENCE + "'");
  }

  private static String fullClosureSql(String representativeId) {
    return """
        UPDATE sales.customer_commercial_assignment
        SET valid_to = now(),
            closure_reason = 'SUPERSEDED',
            closed_by_type = 'SYSTEM',
            closed_by_system_code = 'OWNERSHIP_POLICY',
            representative_id = '%s',
            updated_at = now(),
            version = version + 1
        WHERE id = '%s'
        """
        .formatted(representativeId, CCA_ID);
  }

  private static void assertAppSqlState(String sql, String expectedState) throws SQLException {
    try (Connection connection = appConnection()) {
      assertSqlState(connection, sql, expectedState);
    }
  }

  private static void assertSystemPurgeRejected(PurgeMutation mutation, String purgeTenant)
      throws SQLException {
    try (Connection connection = systemConnection()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        if (purgeTenant != null) {
          statement.execute(
              "SELECT set_config('" + mutation.guc() + "', '" + purgeTenant + "', true)");
        }
        assertThatThrownBy(() -> statement.executeUpdate(mutation.sql()))
            .isInstanceOfSatisfying(
                SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("55000"));
      } finally {
        connection.rollback();
      }
    }
  }

  private static void assertSystemPurgeSucceeds(String guc, List<String> statements)
      throws SQLException {
    try (Connection connection = systemConnection()) {
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("SELECT set_config('" + guc + "', '" + TENANT + "', true)");
        for (String sql : statements) {
          assertThat(statement.executeUpdate(sql)).isEqualTo(1);
        }
      } finally {
        connection.rollback();
      }
    }
  }

  private static void assertSqlState(Connection connection, String sql, String expectedState)
      throws SQLException {
    try (Statement statement = connection.createStatement()) {
      assertThatThrownBy(() -> statement.executeUpdate(sql))
          .isInstanceOfSatisfying(
              SQLException.class,
              error -> assertThat(error.getSQLState()).isEqualTo(expectedState));
    }
  }

  private static void createRuntimeRoles(PostgreSQLContainer<?> postgres) throws SQLException {
    try (Connection connection = adminConnection(postgres);
        Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE ROLE fabric_owner LOGIN NOSUPERUSER NOCREATEROLE BYPASSRLS PASSWORD '"
              + OWNER_PASSWORD
              + "'");
      statement.execute(
          "ALTER DATABASE \"" + postgres.getDatabaseName() + "\" OWNER TO fabric_owner");
      statement.execute(
          "CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS PASSWORD 'app_test'");
      statement.execute(
          "CREATE ROLE fabric_system LOGIN NOSUPERUSER NOCREATEDB BYPASSRLS PASSWORD 'system_test'");
    }
  }

  private static void migrate(PostgreSQLContainer<?> postgres, String target) throws SQLException {
    try (Connection connection = ownerConnection(postgres);
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT current_user, rolsuper, rolcreaterole, rolbypassrls "
                    + "FROM pg_roles WHERE rolname = current_user")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString("current_user")).isEqualTo("fabric_owner");
      assertThat(result.getBoolean("rolsuper")).isFalse();
      assertThat(result.getBoolean("rolcreaterole")).isFalse();
      assertThat(result.getBoolean("rolbypassrls")).isTrue();
    }
    var configuration =
        Flyway.configure()
            .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
            .dataSource(postgres.getJdbcUrl(), "fabric_owner", OWNER_PASSWORD)
            .locations("classpath:db/migration")
            .schemas("common_tenant")
            .defaultSchema("common_tenant");
    if (target != null) {
      configuration.target(target);
    }
    configuration.load().migrate();
  }

  private static void seedFullMigrationFixtures() throws SQLException {
    try (Connection connection = adminConnection(FULL_POSTGRES);
        Statement statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      try {
        statement.execute("SET LOCAL session_replication_role = replica");
        statement.execute(
            """
            INSERT INTO production.quality_decision
              (id, tenant_id, batch_id, decision_scope, outcome, actor_id, origin,
               decided_at, seq, created_at)
            VALUES ('%s', '%s', '%s', 'FULL_LOT', 'RELEASED', '%s', 'MANUAL', now(), 1, now())
            """
                .formatted(QUALITY_DECISION, TENANT, QUALITY_BATCH, QUALITY_ACTOR));
        statement.execute(
            """
            INSERT INTO production.quality_decision_unit (tenant_id, decision_id, stock_unit_id)
            VALUES ('%s', '%s', '%s')
            """
                .formatted(TENANT, QUALITY_DECISION, QUALITY_UNIT));
        statement.execute(
            """
            INSERT INTO sales_ord.sales_order
              (id, tenant_id, uid, trading_partner_id, order_number, order_date)
            VALUES ('%s', '%s', 'DB-PRIV-ORDER', gen_random_uuid(), 'DB-PRIV-ORDER', current_date)
            """
                .formatted(ORDER, TENANT));
        statement.execute(
            """
            INSERT INTO sales_ord.order_cover_evidence_stream
              (id, tenant_id, uid, created_at, updated_at, case_id, sales_order_id)
            VALUES ('%s', '%s', 'DB-PRIV-STREAM', now(), now(), '%s', '%s')
            """
                .formatted(STREAM, TENANT, CASE, ORDER));
        statement.execute(
            """
            INSERT INTO sales_ord.order_cover_evidence
              (id, tenant_id, uid, created_at, updated_at, case_id, sales_order_id, revision,
               order_version, computed_at, input_fingerprint, rule_version,
               requirements, inputs, lines)
            VALUES ('%s', '%s', 'DB-PRIV-EVIDENCE', now(), now(), '%s', '%s', 1,
                    0, now(), '%s', 'DB_PRIVILEGE_V1', '{}', '{}', '[]')
            """
                .formatted(EVIDENCE, TENANT, CASE, ORDER, "a".repeat(64)));
        statement.execute(
            decisionProjectionInsert(
                DECISION_PROJECTION, TENANT, DECISION_CASE, "DB-PRIV-DSP-TENANT"));
        statement.execute(
            decisionProjectionInsert(
                OTHER_DECISION_PROJECTION, OTHER_TENANT, OTHER_DECISION_CASE, "DB-PRIV-DSP-OTHER"));
        statement.execute(
            """
            INSERT INTO sales.customer_commercial_assignment
              (id, tenant_id, uid, customer_id, representative_id, valid_from, source,
               decided_by_type, decided_by_system_code, policy_version, created_at,
               updated_at, is_active, version)
            VALUES ('%s', '%s', 'DB-PRIV-CCA', '%s', '%s', now() - interval '1 hour',
                    'SYSTEM_POLICY', 'SYSTEM', 'OWNERSHIP_POLICY', 'OWNERSHIP_POLICY_V1',
                    now(), now(), true, 0)
            """
                .formatted(CCA_ID, TENANT, CCA_CUSTOMER, CCA_REPRESENTATIVE));
        connection.commit();
      } catch (SQLException failure) {
        connection.rollback();
        throw failure;
      }
    }
  }

  private static Connection appConnection() throws SQLException {
    Connection connection =
        DriverManager.getConnection(FULL_POSTGRES.getJdbcUrl(), "fabric_app", "app_test");
    setTenant(connection);
    return connection;
  }

  private static String decisionProjectionInsert(
      String id, String tenantId, String caseId, String uid) {
    return """
        insert into flowboard.decision_subject_projection
          (id,tenant_id,uid,created_at,updated_at,is_active,version,case_id,kind,
           subject_type,subject_id,subject_number,case_state,case_revision,
           unresolved_line_count,case_opened_at,verdict_code,projected_at)
        values ('%s','%s','%s',now(),now(),true,0,'%s','ORDER_COVER',
                'SALES_ORDER',gen_random_uuid(),'DB-PRIV-ORDER','OPEN',1,0,now(),
                'NO_EVIDENCE',now())
        """
        .formatted(id, tenantId, uid, caseId);
  }

  private static Connection systemConnection() throws SQLException {
    return DriverManager.getConnection(FULL_POSTGRES.getJdbcUrl(), "fabric_system", "system_test");
  }

  private static void setTenant(Connection connection) throws SQLException {
    try (var statement =
        connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
      statement.setString(1, TENANT);
      statement.execute();
    }
  }

  private static Connection ownerConnection(PostgreSQLContainer<?> postgres) throws SQLException {
    return DriverManager.getConnection(postgres.getJdbcUrl(), "fabric_owner", OWNER_PASSWORD);
  }

  private static Connection adminConnection(PostgreSQLContainer<?> postgres) throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  private static String readClasspathScript(String path) {
    try (var input = new ClassPathResource(path).getInputStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read SQL script: " + path, exception);
    }
  }

  private static PrivilegeDifference difference(String relation, String role, String delta) {
    return new PrivilegeDifference(relation, role, delta);
  }

  private record RelationMeasurement(
      String relation, char relkind, EnumMap<RuntimeDatabaseRole, MeasuredPrivileges> privileges) {}

  private record PurgeMutation(String guc, String sql) {}
}
