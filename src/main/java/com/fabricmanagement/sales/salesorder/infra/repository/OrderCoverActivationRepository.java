package com.fabricmanagement.sales.salesorder.infra.repository;

import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class OrderCoverActivationRepository {
  private final JdbcTemplate jdbc;

  public OrderCoverActivationRepository(@Qualifier("dataSource") DataSource dataSource) {
    this.jdbc = new JdbcTemplate(dataSource);
  }

  public void lockForOrderInsert(UUID tenantId) {
    advisoryLock(tenantId, false);
  }

  public void lockForActivation(UUID tenantId) {
    advisoryLock(tenantId, true);
  }

  private void advisoryLock(UUID tenantId, boolean exclusive) {
    String function = exclusive ? "pg_advisory_xact_lock" : "pg_advisory_xact_lock_shared";
    jdbc.query(
        "select " + function + "((('x'||substr(md5(?),1,16))::bit(64)::bigint))",
        (rs, row) -> true,
        tenantId + ":ORDER_COVER_ACTIVATION");
  }

  public Optional<Activation> find(UUID tenantId) {
    return jdbc
        .query(
            "select boundary_seq,activated_at,activated_by from sales_ord.order_cover_activation where tenant_id=?",
            (rs, row) ->
                new Activation(
                    tenantId,
                    rs.getLong(1),
                    rs.getTimestamp(2).toInstant(),
                    rs.getObject(3, UUID.class)),
            tenantId)
        .stream()
        .findFirst();
  }

  public Activation activateOnce(UUID tenantId, UUID actorId) {
    lockForActivation(tenantId);
    long boundary =
        Optional.ofNullable(
                jdbc.queryForObject(
                    "select coalesce(pg_sequence_last_value('sales_ord.sales_order_creation_seq'),0)",
                    Long.class))
            .orElse(0L);
    UUID id = UUID.randomUUID();
    String uid = "SYS-000-OCA-" + id.toString().replace("-", "");
    jdbc.update(
        """
        insert into sales_ord.order_cover_activation(
            tenant_id,id,uid,created_at,created_by,updated_at,updated_by,is_active,version,
            boundary_seq,activated_at,activated_by)
        values(?,?,?,now(),?,now(),?,true,0,?,now(),?)
        on conflict do nothing
        """,
        tenantId,
        id,
        uid,
        actorId,
        actorId,
        boundary,
        actorId);
    return find(tenantId).orElseThrow();
  }

  public record Activation(
      UUID tenantId, long boundarySeq, Instant activatedAt, UUID activatedBy) {}
}
