package com.fabricmanagement.sales.salesorder.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;

@ResourceLock("sales-order-creation-sequence")
class OrderCoverActivationConcurrencyIT extends AbstractIntegrationTest {
  @Autowired DataSource dataSource;

  @Test
  void insertHoldingSharedLockCommitsAsLegacyBeforeActivationBoundary() throws Exception {
    Fixture fixture = fixture();
    try (Connection insert = dataSource.getConnection();
        Connection activation = dataSource.getConnection()) {
      insert.setAutoCommit(false);
      activation.setAutoCommit(false);
      UUID before = insertOrder(insert, fixture, "before");
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
        Future<Long> waiting = executor.submit(() -> activate(activation, fixture.tenantId()));
        assertBlocked(waiting);
        insert.commit();
        long boundary = waiting.get(2, TimeUnit.SECONDS);
        activation.commit();

        assertThat(creationSequence(before)).isLessThanOrEqualTo(boundary);
        UUID after = insertOrderAutoCommit(fixture, "after");
        assertThat(creationSequence(after)).isGreaterThan(boundary);
        ActivationRecord firstActivation = activationRecord(fixture.tenantId());
        try (Connection repeated = dataSource.getConnection()) {
          repeated.setAutoCommit(false);
          assertThat(activate(repeated, fixture.tenantId())).isEqualTo(boundary);
          repeated.commit();
        }
        assertThat(activationRecord(fixture.tenantId())).isEqualTo(firstActivation);
      } finally {
        executor.shutdownNow();
      }
    } finally {
      deleteFixture(fixture);
    }
  }

  @Test
  void activationHoldingExclusiveLockMakesWaitingInsertGoverned() throws Exception {
    Fixture fixture = fixture();
    try (Connection activation = dataSource.getConnection();
        Connection insert = dataSource.getConnection()) {
      activation.setAutoCommit(false);
      insert.setAutoCommit(false);
      long boundary = activate(activation, fixture.tenantId());
      ExecutorService executor = Executors.newSingleThreadExecutor();
      try {
        Future<UUID> waiting =
            executor.submit(
                () -> {
                  try {
                    return insertOrder(insert, fixture, "waiting");
                  } catch (SQLException failure) {
                    throw new CompletionException(failure);
                  }
                });
        assertBlocked(waiting);
        activation.commit();
        UUID orderId = waiting.get(2, TimeUnit.SECONDS);
        insert.commit();

        assertThat(creationSequence(orderId)).isGreaterThan(boundary);
      } finally {
        executor.shutdownNow();
      }
    } finally {
      deleteFixture(fixture);
    }
  }

  private void assertBlocked(Future<?> waiting) throws InterruptedException {
    Thread.sleep(150);
    assertThat(waiting).isNotDone();
  }

  private long activate(Connection connection, UUID tenantId) {
    try {
      try (PreparedStatement lock =
          connection.prepareStatement(
              "select pg_advisory_xact_lock((('x'||substr(md5(?),1,16))::bit(64)::bigint))")) {
        lock.setString(1, tenantId + ":ORDER_COVER_ACTIVATION");
        lock.execute();
      }
      long boundary;
      try (PreparedStatement query =
              connection.prepareStatement(
                  "select coalesce(pg_sequence_last_value('sales_ord.sales_order_creation_seq'),0)");
          ResultSet result = query.executeQuery()) {
        result.next();
        boundary = result.getLong(1);
      }
      try (PreparedStatement insert =
          connection.prepareStatement(
              "insert into sales_ord.order_cover_activation"
                  + "(tenant_id,id,uid,created_at,created_by,updated_at,updated_by,is_active,version,"
                  + "boundary_seq,activated_at,activated_by)"
                  + " values(?,?,?,now(),?,now(),?,true,0,?,now(),?)"
                  + " on conflict do nothing")) {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        insert.setObject(1, tenantId);
        insert.setObject(2, id);
        insert.setString(3, "SYS-000-OCA-" + id.toString().replace("-", ""));
        insert.setObject(4, actorId);
        insert.setObject(5, actorId);
        insert.setLong(6, boundary);
        insert.setObject(7, actorId);
        insert.executeUpdate();
      }
      try (PreparedStatement query =
          connection.prepareStatement(
              "select boundary_seq from sales_ord.order_cover_activation where tenant_id=?")) {
        query.setObject(1, tenantId);
        try (ResultSet result = query.executeQuery()) {
          result.next();
          return result.getLong(1);
        }
      }
    } catch (SQLException failure) {
      throw new CompletionException(failure);
    }
  }

  private UUID insertOrderAutoCommit(Fixture fixture, String suffix) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      return insertOrder(connection, fixture, suffix);
    }
  }

  private UUID insertOrder(Connection connection, Fixture fixture, String suffix)
      throws SQLException {
    UUID orderId = UUID.randomUUID();
    try (PreparedStatement insert =
        connection.prepareStatement(
            "insert into sales_ord.sales_order"
                + "(id,tenant_id,uid,trading_partner_id,order_number,order_date)"
                + " values(?,?,?,?,?,current_date)")) {
      insert.setObject(1, orderId);
      insert.setObject(2, fixture.tenantId());
      insert.setString(3, "IT-SO-" + orderId);
      insert.setObject(4, fixture.partnerId());
      insert.setString(5, "IT-" + suffix + "-" + orderId);
      insert.executeUpdate();
    }
    return orderId;
  }

  private long creationSequence(UUID orderId) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement query =
            connection.prepareStatement(
                "select creation_seq from sales_ord.sales_order where id=?")) {
      query.setObject(1, orderId);
      try (ResultSet result = query.executeQuery()) {
        result.next();
        return result.getLong(1);
      }
    }
  }

  private ActivationRecord activationRecord(UUID tenantId) throws SQLException {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement query =
            connection.prepareStatement(
                "select boundary_seq,activated_at from sales_ord.order_cover_activation"
                    + " where tenant_id=?")) {
      query.setObject(1, tenantId);
      try (ResultSet result = query.executeQuery()) {
        result.next();
        return new ActivationRecord(result.getLong(1), result.getTimestamp(2).toInstant());
      }
    }
  }

  private Fixture fixture() throws SQLException {
    Fixture fixture = new Fixture(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    try (Connection connection = dataSource.getConnection()) {
      try (PreparedStatement tenant =
          connection.prepareStatement(
              "insert into common_tenant.common_tenant(id,uid,slug,name) values(?,?,?,?)")) {
        tenant.setObject(1, fixture.tenantId());
        tenant.setString(2, "IT-" + fixture.tenantId());
        tenant.setString(3, "it-" + fixture.tenantId());
        tenant.setString(4, "Order cover activation IT");
        tenant.executeUpdate();
      }
      try (PreparedStatement registry =
          connection.prepareStatement(
              "insert into common_company.trading_partner_registry(id,uid,official_name)"
                  + " values(?,?,?)")) {
        registry.setObject(1, fixture.registryId());
        registry.setString(2, "IT-REG-" + fixture.registryId());
        registry.setString(3, "Order cover activation customer");
        registry.executeUpdate();
      }
      try (PreparedStatement partner =
          connection.prepareStatement(
              "insert into common_company.common_trading_partner"
                  + "(id,tenant_id,uid,registry_id,partner_type) values(?,?,?,?,?)")) {
        partner.setObject(1, fixture.partnerId());
        partner.setObject(2, fixture.tenantId());
        partner.setString(3, "IT-TP-" + fixture.partnerId());
        partner.setObject(4, fixture.registryId());
        partner.setString(5, "CUSTOMER");
        partner.executeUpdate();
      }
    }
    return fixture;
  }

  private void deleteFixture(Fixture fixture) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      execute(
          connection, "delete from sales_ord.sales_order where tenant_id=?", fixture.tenantId());
      execute(
          connection,
          "delete from sales_ord.order_cover_activation where tenant_id=?",
          fixture.tenantId());
      execute(
          connection,
          "delete from common_company.common_trading_partner where id=?",
          fixture.partnerId());
      execute(
          connection,
          "delete from common_company.trading_partner_registry where id=?",
          fixture.registryId());
      execute(connection, "delete from common_tenant.common_tenant where id=?", fixture.tenantId());
    }
  }

  private static void execute(Connection connection, String sql, UUID value) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, value);
      statement.executeUpdate();
    }
  }

  private record Fixture(UUID tenantId, UUID registryId, UUID partnerId) {}

  private record ActivationRecord(long boundarySequence, Instant activatedAt) {}
}
