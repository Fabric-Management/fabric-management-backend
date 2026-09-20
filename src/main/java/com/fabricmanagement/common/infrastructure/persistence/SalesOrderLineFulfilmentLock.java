package com.fabricmanagement.common.infrastructure.persistence;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serializes competing fulfilment writers for one tenant-scoped sales-order line.
 *
 * <p>Every taker waits for a bounded time. The order-cover settlement sets its own transaction-wide
 * lock timeout, but stock reservation and work-order creation do not, and an unbounded wait on one
 * side of a shared lock leaves that side hanging for as long as the other side's transaction lives.
 * The timeout is therefore applied here, around the advisory lock only, and the caller's previous
 * setting is restored afterwards so this class never changes the rest of the caller's transaction.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class SalesOrderLineFulfilmentLock {
  private static final String LOCK_WAIT = "5s";
  private static final String LOCK_NOT_AVAILABLE = "55P03";

  private final JdbcTemplate jdbc;

  public SalesOrderLineFulfilmentLock(@Qualifier("dataSource") DataSource dataSource) {
    // Bind to the same primary DataSource as the JPA transaction and tenant connection provider.
    this.jdbc = new JdbcTemplate(dataSource);
  }

  public void lock(UUID tenantId, UUID lineId) {
    lockAll(tenantId, List.of(lineId));
  }

  /** Locks in a deterministic order so two multi-line takers cannot deadlock each other. */
  public void lockAll(UUID tenantId, Collection<UUID> lineIds) {
    List<UUID> ordered =
        lineIds.stream().distinct().sorted(Comparator.comparing(UUID::toString)).toList();
    if (ordered.isEmpty()) {
      return;
    }
    String previous = jdbc.queryForObject("select current_setting('lock_timeout')", String.class);
    try {
      setLockTimeout(LOCK_WAIT);
      for (UUID lineId : ordered) {
        jdbc.query(
            "select pg_advisory_xact_lock((('x'||substr(md5(?),1,16))::bit(64)::bigint))",
            (rs, row) -> true,
            tenantId + ":SALES_ORDER_LINE_FULFILMENT:" + lineId);
      }
    } catch (DataAccessException failure) {
      // The transaction is aborted after a failed statement; there is nothing to restore.
      if (isLockTimeout(failure)) {
        throw new FulfilmentBusyException(failure);
      }
      throw failure;
    }
    setLockTimeout(previous);
  }

  private void setLockTimeout(String value) {
    jdbc.queryForObject("select set_config('lock_timeout', ?, true)", String.class, value);
  }

  private static boolean isLockTimeout(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && LOCK_NOT_AVAILABLE.equals(sql.getSQLState())) {
        return true;
      }
    }
    return false;
  }

  /** Typed, retryable 409. The SQL cause is kept so outer translators can still recognise it. */
  public static final class FulfilmentBusyException extends DomainException {
    FulfilmentBusyException(Throwable cause) {
      super(
          "Another fulfilment decision for this sales-order line is in progress; retry shortly",
          "FULFILMENT_LINE_BUSY",
          409,
          cause);
    }
  }
}
