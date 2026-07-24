package com.fabricmanagement.platform.tradingpartner.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class TradingPartnerAcquiredByMigrationIT {

  private static final String CREATOR_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("trading_partner_acquirer_migration")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @BeforeAll
  static void migrateToPreChangeAndSeedPartners() throws SQLException {
    migrateTo("20260724120000");
    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status)
          VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'RSF2A-MIGRATION',
                  'rsf2a-migration', 'RSF-2a Migration', 'ACTIVE');

          INSERT INTO common_company.trading_partner_registry
            (id, uid, official_name, country, verified_status, is_active,
             created_at, updated_at, version)
          VALUES
            ('10000000-0000-4000-8000-000000000001', 'RSF2A-REG-CREATED',
             'Created Customer', 'GBR', 'UNVERIFIED', true, now(), now(), 0),
            ('10000000-0000-4000-8000-000000000002', 'RSF2A-REG-UNKNOWN',
             'Unknown Customer', 'GBR', 'UNVERIFIED', true, now(), now(), 0),
            ('10000000-0000-4000-8000-000000000003', 'RSF2A-REG-SUPPLIER',
             'Supplier', 'GBR', 'UNVERIFIED', true, now(), now(), 0);

          INSERT INTO common_company.common_trading_partner
            (id, tenant_id, uid, registry_id, partner_type, status, created_by,
             is_active, created_at, updated_at, version)
          VALUES
            ('20000000-0000-4000-8000-000000000001',
             'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'RSF2A-CUSTOMER-CREATED',
             '10000000-0000-4000-8000-000000000001', 'CUSTOMER', 'ACTIVE',
             'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', true, now(), now(), 0),
            ('20000000-0000-4000-8000-000000000002',
             'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'RSF2A-CUSTOMER-UNKNOWN',
             '10000000-0000-4000-8000-000000000002', 'CUSTOMER', 'ACTIVE',
             null, true, now(), now(), 0),
            ('20000000-0000-4000-8000-000000000003',
             'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'RSF2A-SUPPLIER-CREATED',
             '10000000-0000-4000-8000-000000000003', 'SUPPLIER', 'ACTIVE',
             'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', true, now(), now(), 0);
          """);
    }
  }

  @Test
  void backfillsOnlyCustomerRelationshipsWithKnownCreators() throws SQLException {
    migrateTo("20260724130000");

    Map<String, String> acquiredByUid = new HashMap<>();
    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                SELECT uid, acquired_by_id::text
                FROM common_company.common_trading_partner
                WHERE tenant_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
                ORDER BY uid
                """)) {
      while (result.next()) {
        acquiredByUid.put(result.getString(1), result.getString(2));
      }
    }

    assertThat(acquiredByUid)
        .containsEntry("RSF2A-CUSTOMER-CREATED", CREATOR_ID)
        .containsKeys("RSF2A-CUSTOMER-UNKNOWN", "RSF2A-SUPPLIER-CREATED");
    assertThat(acquiredByUid.get("RSF2A-CUSTOMER-UNKNOWN")).isNull();
    assertThat(acquiredByUid.get("RSF2A-SUPPLIER-CREATED")).isNull();
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

  private static Connection ownerConnection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
