package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderFieldHistoryDtos.Page;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderFieldChangeHistoryRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderFieldChangeHistoryRepository.Row;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads an order's saved field changes for a person who may read the order now (CEDIT-09 §3).
 *
 * <p>Every page decides access again from current data, exactly as the order's other read-side
 * channels do ({@link SalesOrderEditAccess#requireReadable}): the tenant may use the platform, the
 * person is active, their sales read scope is read fresh and the active order lies in it. Anything
 * else is "not found". No write permission, draft state, edit base, session or lease is needed, and
 * none is opened; reading the history writes nothing and takes no order lock.
 *
 * <p>Changes by every person are shown, not only the caller's own. A chain of pages is bounded by
 * the order version read for its first page, and later pages seek below the last entry shown, so
 * saves made meanwhile never move, repeat or skip an entry (§3.2).
 */
@Service
@RequiredArgsConstructor
public class SalesOrderFieldHistoryService {

  private final SalesOrderEditAccess access;
  private final OrderFieldChangeHistoryRepository history;
  private final SalesOrderFieldHistoryMapper mapper;
  private final UserQueryService users;

  /**
   * One page, newest first. Without a cursor a new chain starts at the order's current version;
   * with one, the next older page of that chain. Limit and cursor are checked before the order is
   * read, and their errors say nothing about the order.
   */
  @Transactional(readOnly = true)
  public Page page(UUID orderId, Integer limit, String cursor, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    int size = SalesOrderFieldHistoryCursor.limit(limit);
    SalesOrderFieldHistoryCursor after =
        cursor == null ? null : SalesOrderFieldHistoryCursor.decode(cursor, tenantId, orderId);

    long orderVersion = access.requireReadable(orderId, actor).getVersion();

    long snapshotVersion;
    List<Row> rows;
    if (after == null) {
      snapshotVersion = orderVersion;
      rows = history.newest(tenantId, orderId, snapshotVersion, size + 1);
    } else {
      if (after.snapshotVersion() > orderVersion) {
        throw SalesOrderFieldHistoryCursor.beyondOrder();
      }
      snapshotVersion = after.snapshotVersion();
      rows = history.olderThan(tenantId, orderId, after.lastVersion(), after.lastId(), size + 1);
    }

    boolean hasMore = rows.size() > size;
    List<Row> shown = hasMore ? rows.subList(0, size) : rows;
    Set<UUID> actors = shown.stream().map(Row::actorId).collect(Collectors.toSet());
    Map<UUID, String> names = users.findRecordedActorNames(tenantId, actors);

    String nextCursor = null;
    if (hasMore) {
      Row last = shown.getLast();
      nextCursor =
          new SalesOrderFieldHistoryCursor(
                  tenantId, orderId, snapshotVersion, last.orderVersion(), last.id())
              .encode();
    }
    return new Page(
        shown.stream().map(row -> mapper.entry(row, names.get(row.actorId()))).toList(),
        snapshotVersion,
        hasMore,
        nextCursor);
  }
}
