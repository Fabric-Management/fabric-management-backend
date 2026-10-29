package com.fabricmanagement.production.core.stockunit.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.testsupport.PostgresImage;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Executes the real cut migrations, then accesses their tables as the restricted runtime role. */
@Testcontainers(disabledWithoutDocker = true)
class StockCutRuntimePrivilegesIT {
  @Container static final PostgreSQLContainer<?> postgres = PostgresImage.container();

  @BeforeAll
  static void migrateWithClosedDefaults() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var sql = connection.createStatement()) {
      sql.execute("CREATE ROLE fabric_app LOGIN NOBYPASSRLS PASSWORD 'app_test'");
      sql.execute("CREATE ROLE fabric_system LOGIN BYPASSRLS PASSWORD 'system_test'");
      sql.execute("CREATE SCHEMA production");
      sql.execute("GRANT USAGE ON SCHEMA production TO fabric_app, fabric_system");
      // The parent stock table belongs to an earlier migration. No default grants are installed.
      sql.execute(
          """
          CREATE TABLE production.stock_unit (
            id uuid PRIMARY KEY, length numeric, length_unit text, current_weight numeric,
            unit text, quality_grade_id uuid, quality_disposition text, flagged boolean)
          """);
      for (String migration : new String[] {"V20261025100000__traceable_roll_cut_plans.sql"}) {
        sql.execute(
            new ClassPathResource("db/migration/" + migration)
                .getContentAsString(StandardCharsets.UTF_8));
      }
    }
  }

  @Test
  void runtimeCanWritePlansSessionsAndReceiptsButCannotRewriteReceiptsOrCrossTenants()
      throws Exception {
    UUID tenant = UUID.randomUUID(), source = UUID.randomUUID();
    try (var owner =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var sql = owner.createStatement()) {
      sql.execute("INSERT INTO production.stock_unit(id) VALUES ('" + source + "')");
    }
    try (var app = DriverManager.getConnection(postgres.getJdbcUrl(), "fabric_app", "app_test");
        var sql = app.createStatement()) {
      assertThat(scalar(app, "SELECT current_user")).isEqualTo("fabric_app");
      sql.execute("SET app.current_tenant = '" + tenant + "'");
      sql.execute(
          """
          INSERT INTO production.stock_unit_cut_plan
            (id,tenant_id,created_at,updated_at,target_type,target_id,target_line_id,product_id,
             target_fingerprint,delivery_quantity,cut_source_id,sources,cut_delivery_quantity,
             state,prepared_by,prepared_at)
          VALUES (gen_random_uuid(),'%s',now(),now(),'SALES_ORDER',gen_random_uuid(),
            gen_random_uuid(),gen_random_uuid(),'terms',2050,'%s','[]',50,'PREPARED',gen_random_uuid(),now())
          """
              .formatted(tenant, source));
      sql.execute(
          """
          INSERT INTO production.stock_unit_cut_session
            (id,tenant_id,created_at,updated_at,session_id,actor_id,target_type,target_id,
             target_line_id,product_id,target_fingerprint)
          VALUES (gen_random_uuid(),'%s',now(),now(),gen_random_uuid(),gen_random_uuid(),
            'SALES_ORDER',gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),'terms')
          """
              .formatted(tenant));
      sql.execute(
          """
          INSERT INTO production.stock_unit_cut_plan_operation
            (id,tenant_id,created_at,updated_at,operation_id,actor_id,target_line_id,request_hash,result_json)
          VALUES (gen_random_uuid(),'%s',now(),now(),gen_random_uuid(),gen_random_uuid(),
            gen_random_uuid(),'command','{}')
          """
              .formatted(tenant));
      assertThat(sql.executeUpdate("UPDATE production.stock_unit_cut_plan SET state='APPROVED'"))
          .isEqualTo(1);
      assertThat(sql.executeUpdate("UPDATE production.stock_unit_cut_session SET ended=true"))
          .isEqualTo(1);
      assertThatThrownBy(
              () ->
                  sql.executeUpdate(
                      "UPDATE production.stock_unit_cut_plan_operation SET result_json='changed'"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("permission denied");
      for (String table :
          new String[] {
            "stock_unit_cut_plan", "stock_unit_cut_session", "stock_unit_cut_plan_operation"
          }) {
        assertThat(scalar(app, "SELECT count(*) FROM production." + table)).isEqualTo("1");
        assertThatThrownBy(() -> sql.executeUpdate("DELETE FROM production." + table))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("permission denied");
      }
      sql.execute("SET app.current_tenant = '" + UUID.randomUUID() + "'");
      for (String table :
          new String[] {
            "stock_unit_cut_plan", "stock_unit_cut_session", "stock_unit_cut_plan_operation"
          }) {
        assertThat(scalar(app, "SELECT count(*) FROM production." + table)).isEqualTo("0");
      }
      assertThatThrownBy(
              () ->
                  sql.executeUpdate(
                      """
          INSERT INTO production.stock_unit_cut_plan_operation
            (id,tenant_id,created_at,updated_at,operation_id,actor_id,target_line_id,request_hash,result_json)
          VALUES (gen_random_uuid(),'%s',now(),now(),gen_random_uuid(),gen_random_uuid(),
            gen_random_uuid(),'forged','{}')
          """
                          .formatted(tenant)))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("row-level security");
    }
    try (var system =
            DriverManager.getConnection(postgres.getJdbcUrl(), "fabric_system", "system_test");
        var sql = system.createStatement()) {
      assertThat(sql.executeUpdate("DELETE FROM production.stock_unit_cut_plan_operation"))
          .isEqualTo(1);
      assertThat(sql.executeUpdate("DELETE FROM production.stock_unit_cut_session")).isEqualTo(1);
      assertThat(sql.executeUpdate("DELETE FROM production.stock_unit_cut_plan")).isEqualTo(1);
    }
  }

  private static String scalar(Connection connection, String query) throws SQLException {
    try (var sql = connection.createStatement();
        var result = sql.executeQuery(query)) {
      result.next();
      return result.getString(1);
    }
  }
}
