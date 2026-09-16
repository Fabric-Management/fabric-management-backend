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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
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
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @Container
  static final PostgreSQLContainer<?> BASELINE_POSTGRES =
      PostgresImage.container()
          .withDatabaseName("table_privilege_baseline")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @BeforeAll
  static void migrateAndSeed() throws SQLException {
    createRuntimeRoles(FULL_POSTGRES);
    createRuntimeRoles(BASELINE_POSTGRES);
    migrate(FULL_POSTGRES, null);
    migrate(BASELINE_POSTGRES, BASELINE_TARGET);
    seedFullMigrationFixtures();
  }

  @Test
  void pinnedTargetReportsOnlyCustomerAssignmentDeleteAsSurplus() throws SQLException {
    try (Connection connection = ownerConnection(BASELINE_POSTGRES)) {
      assertThat(differences(connection))
          .containsExactly(
              difference("sales.customer_commercial_assignment", "fabric_app", "surplus: DELETE"));
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
      // Execute the operator's resource unchanged; its first result contains the classifications.
      assertThat(statement.execute(readClasspathScript(MEASUREMENT_SCRIPT))).isTrue();
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

  private static void assertProbe(List<String> probeStatements, List<PrivilegeDifference> expected)
      throws SQLException {
    try (Connection connection = ownerConnection(FULL_POSTGRES)) {
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
    try (Connection connection = ownerConnection(postgres);
        Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS PASSWORD 'app_test'");
      statement.execute(
          "CREATE ROLE fabric_system LOGIN NOSUPERUSER NOCREATEDB BYPASSRLS PASSWORD 'system_test'");
    }
  }

  private static void migrate(PostgreSQLContainer<?> postgres, String target) {
    var configuration =
        Flyway.configure()
            .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .locations("classpath:db/migration")
            .schemas("common_tenant")
            .defaultSchema("common_tenant");
    if (target != null) {
      configuration.target(target);
    }
    configuration.load().migrate();
  }

  private static void seedFullMigrationFixtures() throws SQLException {
    try (Connection connection = ownerConnection(FULL_POSTGRES);
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
