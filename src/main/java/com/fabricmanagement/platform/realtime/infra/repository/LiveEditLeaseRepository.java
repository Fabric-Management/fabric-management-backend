package com.fabricmanagement.platform.realtime.infra.repository;

import com.fabricmanagement.platform.realtime.domain.LiveEditLease;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Field leases of one tenant (CEDIT-07). RLS isolates tenants and every statement names the tenant
 * too. Every locking read orders its rows by scope then part (the order of {@code LiveLeaseKey}):
 * two writers that lock rows of one resource always lock them in the same order and cannot
 * deadlock. Nothing here deletes a row; ended rows are removed by the retention job only.
 */
public interface LiveEditLeaseRepository extends JpaRepository<LiveEditLease, UUID> {

  /** Every row of the resource in the given scopes, locked, in key order. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select l from LiveEditLease l where l.tenantId = :tenantId"
          + " and l.resourceType = :resourceType and l.resourceId = :resourceId"
          + " and l.leaseScope in :scopes order by l.leaseScope, l.leasePart")
  List<LiveEditLease> lockInScopes(
      @Param("tenantId") UUID tenantId,
      @Param("resourceType") String resourceType,
      @Param("resourceId") UUID resourceId,
      @Param("scopes") Collection<String> scopes);

  /** The rows of the resource whose current period carries one of these tokens, locked. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select l from LiveEditLease l where l.tenantId = :tenantId"
          + " and l.resourceType = :resourceType and l.resourceId = :resourceId"
          + " and l.token in :tokens order by l.leaseScope, l.leasePart")
  List<LiveEditLease> lockByTokens(
      @Param("tenantId") UUID tenantId,
      @Param("resourceType") String resourceType,
      @Param("resourceId") UUID resourceId,
      @Param("tokens") Collection<UUID> tokens);

  /** The session's unreleased rows on the resource, locked: what a session close releases. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select l from LiveEditLease l where l.tenantId = :tenantId"
          + " and l.resourceType = :resourceType and l.resourceId = :resourceId"
          + " and l.sessionId = :sessionId and l.releasedAt is null"
          + " order by l.leaseScope, l.leasePart")
  List<LiveEditLease> lockUnreleasedOfSession(
      @Param("tenantId") UUID tenantId,
      @Param("resourceType") String resourceType,
      @Param("resourceId") UUID resourceId,
      @Param("sessionId") UUID sessionId);

  /** Leases held now on the resource in its current generation, in key order. Not locked. */
  @Query(
      "select l from LiveEditLease l where l.tenantId = :tenantId"
          + " and l.resourceType = :resourceType and l.resourceId = :resourceId"
          + " and l.resourceGeneration = :generation and l.releasedAt is null"
          + " and l.expiresAt > :now order by l.leaseScope, l.leasePart")
  List<LiveEditLease> findHeld(
      @Param("tenantId") UUID tenantId,
      @Param("resourceType") String resourceType,
      @Param("resourceId") UUID resourceId,
      @Param("generation") long generation,
      @Param("now") Instant now);

  /**
   * How many leases the session holds now on its resource in the resource's current generation: the
   * per-session bound (CEDIT-07 R2). A row of an older generation is void and not counted; an edit
   * session belongs to one resource, so this is every holding the session has.
   */
  @Query(
      "select count(l) from LiveEditLease l where l.tenantId = :tenantId"
          + " and l.resourceType = :resourceType and l.resourceId = :resourceId"
          + " and l.sessionId = :sessionId and l.resourceGeneration = :generation"
          + " and l.releasedAt is null and l.expiresAt > :now")
  long countHeldBySession(
      @Param("tenantId") UUID tenantId,
      @Param("resourceType") String resourceType,
      @Param("resourceId") UUID resourceId,
      @Param("sessionId") UUID sessionId,
      @Param("generation") long generation,
      @Param("now") Instant now);

  /**
   * How many leases are held now on the resource in its current generation: the per-resource bound
   * (CEDIT-07 R2). Void rows of an older generation are not counted. Rows themselves stay bounded
   * because the consumer accepts keys of its own catalogue and real lines only, one row per key.
   */
  @Query(
      "select count(l) from LiveEditLease l where l.tenantId = :tenantId"
          + " and l.resourceType = :resourceType and l.resourceId = :resourceId"
          + " and l.resourceGeneration = :generation"
          + " and l.releasedAt is null and l.expiresAt > :now")
  long countHeldOnResource(
      @Param("tenantId") UUID tenantId,
      @Param("resourceType") String resourceType,
      @Param("resourceId") UUID resourceId,
      @Param("generation") long generation,
      @Param("now") Instant now);
}
