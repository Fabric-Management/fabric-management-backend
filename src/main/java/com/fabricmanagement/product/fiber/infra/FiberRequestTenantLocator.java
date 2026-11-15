package com.fabricmanagement.product.fiber.infra;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.product.fiber.domain.FiberRequestStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Which tenant each fibre request belongs to, for the platform's review queue.
 *
 * <p>Fibre requests are tenant rows under row-level security, and platform staff work in the
 * platform's own tenant, so a repository query from their request sees none of the requests other
 * companies sent. This reads only the request id, its tenant and the queue order through the system
 * role; the request itself is then read and decided in its own tenant, under row-level security.
 */
@Component
@RequiredArgsConstructor
public class FiberRequestTenantLocator {

  private static final String TABLE = "production.production_fiber_request";

  private final SystemTransactionExecutor systemExecutor;

  /** One request in the queue and the tenant it belongs to. */
  public record LocatedRequest(UUID requestId, UUID tenantId) {}

  /** One page of the queue, newest first, with the total for paging. */
  public record LocatedPage(List<LocatedRequest> requests, long total) {}

  /** The tenant of one request; empty when no such request exists. */
  public Optional<UUID> tenantOf(UUID requestId) {
    if (requestId == null) {
      return Optional.empty();
    }
    return systemExecutor
        .executeQuery(
            "SELECT tenant_id FROM " + TABLE + " WHERE id = ?",
            (rs, rowNum) -> rs.getObject(1, UUID.class),
            requestId)
        .stream()
        .findFirst();
  }

  /**
   * One page of every tenant's requests, newest first.
   *
   * @param status the status to keep; {@code null} keeps every status
   */
  public LocatedPage page(FiberRequestStatus status, long offset, int limit) {
    String where = status == null ? "" : " WHERE status = ?";
    List<Object> args = new ArrayList<>();
    if (status != null) {
      args.add(status.name());
    }
    Long total =
        systemExecutor.executeQueryForObject(
            "SELECT count(*) FROM " + TABLE + where, (rs, rowNum) -> rs.getLong(1), args.toArray());
    args.add(limit);
    args.add(offset);
    List<LocatedRequest> requests =
        systemExecutor.executeQuery(
            "SELECT id, tenant_id FROM "
                + TABLE
                + where
                + " ORDER BY created_at DESC, id LIMIT ? OFFSET ?",
            (rs, rowNum) ->
                new LocatedRequest(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)),
            args.toArray());
    return new LocatedPage(requests, total == null ? 0L : total);
  }
}
