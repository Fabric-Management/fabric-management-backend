package com.fabricmanagement.platform.realtime.infra.repository;

import com.fabricmanagement.platform.realtime.domain.LiveEditSession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Edit sessions of one tenant (CEDIT-06). RLS isolates tenants and every statement names the tenant
 * too. Renewal and closing are single conditional updates: whichever of two racing requests runs
 * second sees the row as it was left by the first, so a late renewal never revives a closed or
 * expired session and a close is never undone.
 */
public interface LiveEditSessionRepository extends JpaRepository<LiveEditSession, UUID> {

  /** Open sessions of one resource that have not expired at {@code now}, oldest first. */
  @Query(
      "select s from LiveEditSession s where s.tenantId = :tenantId"
          + " and s.resourceType = :resourceType and s.resourceId = :resourceId"
          + " and s.closedAt is null and s.expiresAt > :now order by s.openedAt, s.id")
  List<LiveEditSession> findLive(
      @Param("tenantId") UUID tenantId,
      @Param("resourceType") String resourceType,
      @Param("resourceId") UUID resourceId,
      @Param("now") Instant now);

  /** Ids of the same sessions, in id order: the input of the presence revision. */
  @Query(
      "select s.id from LiveEditSession s where s.tenantId = :tenantId"
          + " and s.resourceType = :resourceType and s.resourceId = :resourceId"
          + " and s.closedAt is null and s.expiresAt > :now order by s.id")
  List<UUID> findLiveIds(
      @Param("tenantId") UUID tenantId,
      @Param("resourceType") String resourceType,
      @Param("resourceId") UUID resourceId,
      @Param("now") Instant now);

  /**
   * One session of the resource, shared-locked until the transaction ends (CEDIT-07): a lease
   * decision reads it after this lock, so a close or a renewal of the session cannot cross the
   * decision. Ended sessions are returned too; the caller judges liveness with its own clock read.
   */
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query(
      "select s from LiveEditSession s where s.tenantId = :tenantId and s.id = :id"
          + " and s.resourceType = :resourceType and s.resourceId = :resourceId")
  Optional<LiveEditSession> lockForLease(
      @Param("tenantId") UUID tenantId,
      @Param("id") UUID id,
      @Param("resourceType") String resourceType,
      @Param("resourceId") UUID resourceId);

  /**
   * Extends one open, unexpired session of this user on this resource to {@code expiresAt}. Returns
   * 0 when there is no such session: closed, expired, another user's or another resource's.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update LiveEditSession s set s.lastSeenAt = :now, s.expiresAt = :expiresAt,"
          + " s.updatedAt = :now, s.updatedBy = :userId, s.version = s.version + 1"
          + " where s.tenantId = :tenantId and s.id = :id"
          + " and s.resourceType = :resourceType and s.resourceId = :resourceId"
          + " and s.userId = :userId and s.closedAt is null and s.expiresAt > :now")
  int renew(
      @Param("tenantId") UUID tenantId,
      @Param("id") UUID id,
      @Param("resourceType") String resourceType,
      @Param("resourceId") UUID resourceId,
      @Param("userId") UUID userId,
      @Param("now") Instant now,
      @Param("expiresAt") Instant expiresAt);

  /**
   * Closes one open session of this user on this resource; an already closed or someone else's
   * session is left as it is (0). An expired session is closed too, so its row records the end.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update LiveEditSession s set s.closedAt = :now,"
          + " s.updatedAt = :now, s.updatedBy = :userId, s.version = s.version + 1"
          + " where s.tenantId = :tenantId and s.id = :id"
          + " and s.resourceType = :resourceType and s.resourceId = :resourceId"
          + " and s.userId = :userId and s.closedAt is null")
  int close(
      @Param("tenantId") UUID tenantId,
      @Param("id") UUID id,
      @Param("resourceType") String resourceType,
      @Param("resourceId") UUID resourceId,
      @Param("userId") UUID userId,
      @Param("now") Instant now);
}
