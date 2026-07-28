package com.fabricmanagement.sales.ownership.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class OwnershipTruthMigrationIT {

  private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
  private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
  private static final String ACQUIRER = "aaaaaaaa-0000-4000-8000-000000000001";
  private static final String SOLE_MEMBER = "aaaaaaaa-0000-4000-8000-000000000002";
  private static final String SECOND_MEMBER = "aaaaaaaa-0000-4000-8000-000000000003";
  private static final String CUSTOMER_ACQUIRER = "aaaaaaaa-0000-4000-8000-000000000011";
  private static final String CUSTOMER_SOLE_TEAM = "aaaaaaaa-0000-4000-8000-000000000012";
  private static final String CUSTOMER_MULTI_TEAM = "aaaaaaaa-0000-4000-8000-000000000013";
  private static final String CUSTOMER_EXEMPT = "bbbbbbbb-0000-4000-8000-000000000011";

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("ownership_truth_migration")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @BeforeAll
  static void migrateAndSeed() throws SQLException {
    migrateTo("20260724131000");
    seedLegacyData();
    migrateTo("20260728121000");
    execute(
        "UPDATE sales.ownership_policy SET default_mode = 'EXEMPT' WHERE tenant_id = '"
            + TENANT_B
            + "'");
    migrateTo("20260728124000");
  }

  @Test
  void backfillIsModeScopedDeterministicAndDoesNotInventLegacyTime() throws SQLException {
    assertThat(
            scalar(
                "SELECT source FROM sales.customer_commercial_assignment WHERE customer_id = '"
                    + CUSTOMER_ACQUIRER
                    + "'"))
        .isEqualTo("BACKFILL_ACQUIRER");
    assertThat(
            scalar(
                "SELECT representative_id::text FROM sales.customer_commercial_assignment "
                    + "WHERE customer_id = '"
                    + CUSTOMER_ACQUIRER
                    + "'"))
        .isEqualTo(ACQUIRER);
    assertThat(
            scalar(
                "SELECT source FROM sales.customer_commercial_assignment WHERE customer_id = '"
                    + CUSTOMER_SOLE_TEAM
                    + "'"))
        .isEqualTo("BACKFILL_SOLE_TEAM_MEMBER");
    assertThat(
            scalar(
                "SELECT representative_id::text FROM sales.customer_commercial_assignment "
                    + "WHERE customer_id = '"
                    + CUSTOMER_SOLE_TEAM
                    + "'"))
        .isEqualTo(SOLE_MEMBER);
    assertThat(countForCustomer(CUSTOMER_MULTI_TEAM)).isZero();
    assertThat(countForCustomer(CUSTOMER_EXEMPT)).isZero();
    assertThat(
            scalar(
                "SELECT policy_version FROM sales.customer_commercial_assignment "
                    + "WHERE customer_id = '"
                    + CUSTOMER_ACQUIRER
                    + "'"))
        .isEqualTo("OWNERSHIP_BACKFILL_V1");
    assertThat(
            scalar(
                "SELECT customer_relationship_established_at::text "
                    + "FROM common_company.common_trading_partner WHERE id = '"
                    + CUSTOMER_ACQUIRER
                    + "'"))
        .isNull();
    assertThat(
            scalar(
                "SELECT owner_resolution_reason FROM sales.quote "
                    + "WHERE quote_number = 'LEGACY-OWNED'"))
        .isEqualTo("LEGACY_UNKNOWN");
    assertThat(
            scalar(
                "SELECT assigned_to_id::text FROM sales.quote "
                    + "WHERE quote_number = 'LEGACY-OWNED'"))
        .isEqualTo(ACQUIRER);
  }

  @Test
  void relationshipEstablishedAtIsWriteOnceAndLegacyRowsRemainNull() throws SQLException {
    execute(
        "UPDATE common_company.common_trading_partner "
            + "SET customer_relationship_established_at = '2026-07-28T09:00:00Z' "
            + "WHERE id = '"
            + CUSTOMER_EXEMPT
            + "'");

    assertThatThrownBy(
            () ->
                execute(
                    "UPDATE common_company.common_trading_partner "
                        + "SET customer_relationship_established_at = '2026-07-28T10:00:00Z' "
                        + "WHERE id = '"
                        + CUSTOMER_EXEMPT
                        + "'"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("immutable");
    assertThat(
            scalar(
                "SELECT customer_relationship_established_at::text "
                    + "FROM common_company.common_trading_partner WHERE id = '"
                    + CUSTOMER_SOLE_TEAM
                    + "'"))
        .isNull();
  }

  @Test
  void quoteOwnerIsNullableAndReasonIsPersistedIndependentlyOfTheFlag() throws SQLException {
    String quoteId = UUID.randomUUID().toString();
    execute(
        "INSERT INTO sales.quote "
            + "(id, tenant_id, uid, quote_number, customer_id, assigned_to_id, module_type, "
            + "valid_until, owner_resolution_reason) VALUES ('"
            + quoteId
            + "', '"
            + TENANT_A
            + "', gen_random_uuid()::text, 'NULL-OWNER-"
            + quoteId
            + "', '"
            + CUSTOMER_MULTI_TEAM
            + "', NULL, 'FABRIC', CURRENT_DATE + 30, 'OPTIONAL_UNASSIGNED')");

    assertThat(scalar("SELECT assigned_to_id::text FROM sales.quote WHERE id = '" + quoteId + "'"))
        .isNull();
    assertThat(
            scalar("SELECT owner_resolution_reason FROM sales.quote WHERE id = '" + quoteId + "'"))
        .isEqualTo("OPTIONAL_UNASSIGNED");
  }

  @Test
  void partialUniqueAllowsOnlyCloseThenInsert() throws SQLException {
    String customerId = UUID.randomUUID().toString();
    insertOpen(customerId, "USER", ACQUIRER, null);

    assertThatThrownBy(() -> insertOpen(customerId, "SYSTEM", null, "OWNERSHIP_POLICY"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("uq_customer_commercial_assignment_open");

    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      statement.executeUpdate(
          "UPDATE sales.customer_commercial_assignment "
              + "SET valid_to = now(), closure_reason = 'SUPERSEDED', "
              + "closed_by_type = 'SYSTEM', "
              + "closed_by_system_code = 'OWNERSHIP_POLICY' WHERE customer_id = '"
              + customerId
              + "'");
      statement.execute(
          insertSql(customerId, "SYSTEM", null, "OWNERSHIP_POLICY", null, null, null, null));
      connection.commit();
    }

    assertThat(
            count(
                "SELECT count(*) FROM sales.customer_commercial_assignment WHERE customer_id = '"
                    + customerId
                    + "' AND valid_to IS NULL"))
        .isEqualTo(1);
  }

  @Test
  void actorAndStateChecksRejectEveryInvalidTuple() {
    assertInsertRejected("USER", null, "OWNERSHIP_POLICY", null, null, null, null);
    assertInsertRejected("SYSTEM", ACQUIRER, null, null, null, null, null);
    assertInsertRejected("USER", ACQUIRER, null, "SYSTEM", ACQUIRER, null, "SUPERSEDED");
    assertInsertRejected("USER", ACQUIRER, null, "USER", null, "OWNERSHIP_POLICY", "SUPERSEDED");
    assertInsertRejected("USER", ACQUIRER, null, "SYSTEM", null, "OWNERSHIP_POLICY", null);
    assertInsertRejected("USER", ACQUIRER, null, null, null, null, "SUPERSEDED");
  }

  @Test
  void creationProvenanceClosedRowsAndDeletesAreDatabaseGuarded() throws SQLException {
    String customerId = UUID.randomUUID().toString();
    insertOpen(customerId, "USER", ACQUIRER, null);

    assertMutationRejected(
        "UPDATE sales.customer_commercial_assignment SET representative_id = '"
            + SOLE_MEMBER
            + "' WHERE customer_id = '"
            + customerId
            + "'");
    assertMutationRejected(
        "DELETE FROM sales.customer_commercial_assignment WHERE customer_id = '"
            + customerId
            + "'");

    execute(
        "UPDATE sales.customer_commercial_assignment "
            + "SET valid_to = now(), closure_reason = 'SUPERSEDED', closed_by_type = 'SYSTEM', "
            + "closed_by_system_code = 'OWNERSHIP_POLICY' WHERE customer_id = '"
            + customerId
            + "'");
    assertMutationRejected(
        "UPDATE sales.customer_commercial_assignment SET valid_to = NULL, "
            + "closure_reason = NULL, closed_by_type = NULL, closed_by_system_code = NULL "
            + "WHERE customer_id = '"
            + customerId
            + "'");
  }

  @Test
  void tenantWholePurgeIsTheOnlyDeleteEscapeHatch() throws SQLException {
    String customerId = UUID.randomUUID().toString();
    insertOpen(customerId, "SYSTEM", null, "OWNERSHIP_POLICY");

    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      statement.execute(
          "SELECT set_config('app.customer_commercial_assignment_purge_tenant', '"
              + TENANT_A
              + "', true)");
      assertThat(
              statement.executeUpdate(
                  "DELETE FROM sales.customer_commercial_assignment WHERE customer_id = '"
                      + customerId
                      + "'"))
          .isEqualTo(1);
      connection.rollback();
    }

    assertThat(countForCustomer(customerId)).isEqualTo(1);
  }

  private static void seedLegacyData() throws SQLException {
    execute(
        """
        INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status)
        VALUES
          ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'OWN-A', 'ownership-a',
           'Ownership A', 'ACTIVE'),
          ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'OWN-B', 'ownership-b',
           'Ownership B', 'ACTIVE');

        INSERT INTO common_company.common_organization
          (id, tenant_id, uid, name, tax_id, organization_type)
        VALUES
          ('aaaaaaaa-1000-4000-8000-000000000001',
           'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
           'OWN-ORG-A', 'Ownership Org A', 'OWN-A', 'VERTICAL_MILL'),
          ('bbbbbbbb-1000-4000-8000-000000000001',
           'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
           'OWN-ORG-B', 'Ownership Org B', 'OWN-B', 'VERTICAL_MILL');

        INSERT INTO common_user.common_user
          (id, tenant_id, uid, first_name, last_name, organization_id, user_type, is_active)
        VALUES
          ('aaaaaaaa-0000-4000-8000-000000000001',
           'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
           'OWN-ACQUIRER', 'Active', 'Acquirer',
           'aaaaaaaa-1000-4000-8000-000000000001', 'INTERNAL', TRUE),
          ('aaaaaaaa-0000-4000-8000-000000000002',
           'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
           'OWN-SOLE', 'Sole', 'Member',
           'aaaaaaaa-1000-4000-8000-000000000001', 'INTERNAL', TRUE),
          ('aaaaaaaa-0000-4000-8000-000000000003',
           'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
           'OWN-SECOND', 'Second', 'Member',
           'aaaaaaaa-1000-4000-8000-000000000001', 'INTERNAL', TRUE);

        INSERT INTO common_company.trading_partner_registry
          (id, uid, official_name, country)
        VALUES
          ('aaaaaaaa-2000-4000-8000-000000000001', 'OWN-REG-1', 'Acquirer Customer', 'GBR'),
          ('aaaaaaaa-2000-4000-8000-000000000002', 'OWN-REG-2', 'Sole Team Customer', 'GBR'),
          ('aaaaaaaa-2000-4000-8000-000000000003', 'OWN-REG-3', 'Multi Team Customer', 'GBR'),
          ('bbbbbbbb-2000-4000-8000-000000000001', 'OWN-REG-4', 'Exempt Customer', 'GBR');

        INSERT INTO common_company.common_trading_partner
          (id, tenant_id, uid, registry_id, partner_type, status, acquired_by_id)
        VALUES
          ('aaaaaaaa-0000-4000-8000-000000000011',
           'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
           'OWN-CUSTOMER-1', 'aaaaaaaa-2000-4000-8000-000000000001',
           'CUSTOMER', 'ACTIVE', 'aaaaaaaa-0000-4000-8000-000000000001'),
          ('aaaaaaaa-0000-4000-8000-000000000012',
           'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
           'OWN-CUSTOMER-2', 'aaaaaaaa-2000-4000-8000-000000000002',
           'CUSTOMER', 'ACTIVE', NULL),
          ('aaaaaaaa-0000-4000-8000-000000000013',
           'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
           'OWN-CUSTOMER-3', 'aaaaaaaa-2000-4000-8000-000000000003',
           'CUSTOMER', 'ACTIVE', NULL),
          ('bbbbbbbb-0000-4000-8000-000000000011',
           'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
           'OWN-CUSTOMER-4', 'bbbbbbbb-2000-4000-8000-000000000001',
           'CUSTOMER', 'ACTIVE', NULL);

        INSERT INTO sales.customer_account_team_member
          (tenant_id, customer_id, user_id, uid)
        VALUES
          ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
           'aaaaaaaa-0000-4000-8000-000000000012',
           'aaaaaaaa-0000-4000-8000-000000000002', 'OWN-TEAM-1'),
          ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
           'aaaaaaaa-0000-4000-8000-000000000013',
           'aaaaaaaa-0000-4000-8000-000000000002', 'OWN-TEAM-2'),
          ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
           'aaaaaaaa-0000-4000-8000-000000000013',
           'aaaaaaaa-0000-4000-8000-000000000003', 'OWN-TEAM-3');

        INSERT INTO sales.quote
          (id, tenant_id, uid, quote_number, customer_id, assigned_to_id,
           module_type, valid_until)
        VALUES
          ('aaaaaaaa-3000-4000-8000-000000000001',
           'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
           'OWN-QUOTE-1', 'LEGACY-OWNED',
           'aaaaaaaa-0000-4000-8000-000000000011',
           'aaaaaaaa-0000-4000-8000-000000000001',
           'FABRIC', CURRENT_DATE + 30);
        """);
  }

  private static void insertOpen(
      String customerId, String actorType, String userId, String systemCode) throws SQLException {
    execute(insertSql(customerId, actorType, userId, systemCode, null, null, null, null));
  }

  private static void assertInsertRejected(
      String decidedType,
      String decidedUserId,
      String decidedSystemCode,
      String closedType,
      String closedUserId,
      String closedSystemCode,
      String closureReason) {
    assertThatThrownBy(
            () ->
                execute(
                    insertSql(
                        UUID.randomUUID().toString(),
                        decidedType,
                        decidedUserId,
                        decidedSystemCode,
                        closedType,
                        closedUserId,
                        closedSystemCode,
                        closureReason)))
        .isInstanceOf(SQLException.class);
  }

  private static String insertSql(
      String customerId,
      String decidedType,
      String decidedUserId,
      String decidedSystemCode,
      String closedType,
      String closedUserId,
      String closedSystemCode,
      String closureReason) {
    boolean closed =
        closedType != null
            || closedUserId != null
            || closedSystemCode != null
            || closureReason != null;
    return """
        INSERT INTO sales.customer_commercial_assignment (
          id, tenant_id, uid, customer_id, representative_id, valid_from, valid_to,
          source, decided_by_type, decided_by_user_id, decided_by_system_code,
          closed_by_type, closed_by_user_id, closed_by_system_code, closure_reason,
          policy_version, created_at, updated_at, is_active, version
        ) VALUES (
          gen_random_uuid(), '%s', gen_random_uuid()::text, '%s', '%s', now() - interval '1 hour',
          %s, 'MANUAL', '%s', %s, %s, %s, %s, %s, %s,
          'OWNERSHIP_POLICY_V1', now(), now(), TRUE, 0
        )
        """
        .formatted(
            TENANT_A,
            customerId,
            ACQUIRER,
            closed ? "now()" : "NULL",
            decidedType,
            sqlUuid(decidedUserId),
            sqlText(decidedSystemCode),
            sqlText(closedType),
            sqlUuid(closedUserId),
            sqlText(closedSystemCode),
            sqlText(closureReason));
  }

  private static String sqlUuid(String value) {
    return value == null ? "NULL" : "'" + value + "'::UUID";
  }

  private static String sqlText(String value) {
    return value == null ? "NULL" : "'" + value + "'";
  }

  private static void assertMutationRejected(String sql) {
    assertThatThrownBy(() -> execute(sql))
        .isInstanceOf(SQLException.class)
        .satisfies(
            error ->
                assertThat(error.getMessage())
                    .containsAnyOf("retention-stable", "one-time closure"));
  }

  private static long countForCustomer(String customerId) throws SQLException {
    return count(
        "SELECT count(*) FROM sales.customer_commercial_assignment WHERE customer_id = '"
            + customerId
            + "'");
  }

  private static long count(String sql) throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  private static String scalar(String sql) throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getString(1);
    }
  }

  private static void execute(String sql) throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static void migrateTo(String target) {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .schemas("common_tenant")
        .defaultSchema("common_tenant")
        .target(target)
        .load()
        .migrate();
  }

  private static Connection connection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
