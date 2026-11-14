package com.fabricmanagement.platform.auth.infra.repository;

import com.fabricmanagement.platform.auth.domain.PendingSignup;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Pending self-service signups. The table is global (no tenant_id, no RLS), so these queries work
 * for the unauthenticated caller of the public signup endpoint.
 *
 * <p>{@code taxKey} is the tax number trimmed and lower-cased, the same expression as the partial
 * unique index {@code uk_pending_signup_open_tax}.
 */
@Repository
public interface PendingSignupRepository extends JpaRepository<PendingSignup, UUID> {

  Optional<PendingSignup> findByEmailAndCompletedAtIsNull(String email);

  /** For checking a link without using it. */
  Optional<PendingSignup> findByTokenHash(String tokenHash);

  /**
   * The signup a link belongs to, locked for the rest of the transaction: two requests carrying the
   * same link (a double click) take turns, so only one of them can build the tenant.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PendingSignup p where p.tokenHash = :tokenHash")
  Optional<PendingSignup> findLockedByTokenHash(@Param("tokenHash") String tokenHash);

  /**
   * Marks an open signup as completed by the tenant built from it. Flushes first, so the tenant row
   * the foreign key points at is already written.
   *
   * @return 1 if marked, 0 if it was already completed
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update PendingSignup p set p.completedAt = :now, p.completedTenantId = :tenantId,"
          + " p.updatedAt = :now where p.id = :id and p.completedAt is null")
  int markCompleted(
      @Param("id") UUID id, @Param("tenantId") UUID tenantId, @Param("now") Instant now);

  @Query(
      "select count(p) > 0 from PendingSignup p where p.completedAt is null"
          + " and lower(trim(p.taxId)) = :taxKey and p.email <> :email")
  boolean existsOpenForTaxKeyOfOtherEmail(
      @Param("taxKey") String taxKey, @Param("email") String email);

  /**
   * Removes expired, not completed signups that hold this e-mail or tax number, so the unique
   * indexes (which cannot test the clock) do not keep them blocked. The sales lead of such a signup
   * stays.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "delete from PendingSignup p where p.completedAt is null and p.expiresAt < :now"
          + " and (p.email = :email or lower(trim(p.taxId)) = :taxKey)")
  int deleteExpiredHolding(
      @Param("now") Instant now, @Param("email") String email, @Param("taxKey") String taxKey);

  /**
   * Gives an open signup its latest form and a new link, unless it has already had {@code maxLinks}
   * links. One atomic statement, so two simultaneous repeats cannot both read the same count and
   * the limit cannot be passed.
   *
   * @return 1 if replaced, 0 if the signup is completed, gone, or at its link limit
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "UPDATE common_auth.pending_signup SET tax_id = :taxId,"
              + " request = CAST(:request AS jsonb), token_hash = :tokenHash,"
              + " terms_accepted_at = :termsAcceptedAt, expires_at = :expiresAt,"
              + " send_count = send_count + 1, updated_at = :now"
              + " WHERE id = :id AND completed_at IS NULL AND send_count < :maxLinks",
      nativeQuery = true)
  int replaceIfUnderLimit(
      @Param("id") UUID id,
      @Param("taxId") String taxId,
      @Param("request") String requestJson,
      @Param("tokenHash") String tokenHash,
      @Param("termsAcceptedAt") Instant termsAcceptedAt,
      @Param("expiresAt") Instant expiresAt,
      @Param("now") Instant now,
      @Param("maxLinks") int maxLinks);

  /**
   * Inserts a new open signup unless the e-mail or tax number is already held by an open one.
   * {@code ON CONFLICT DO NOTHING} (no target) covers both partial unique indexes, so two
   * simultaneous signups cannot both succeed and the loser does not abort its transaction.
   *
   * @return 1 if the row was inserted, 0 if another open signup holds the e-mail or tax number
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "INSERT INTO common_auth.pending_signup (id, email, tax_id, request, token_hash,"
              + " terms_accepted_at, expires_at) VALUES (:id, :email, :taxId,"
              + " CAST(:request AS jsonb), :tokenHash, :termsAcceptedAt, :expiresAt)"
              + " ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int insertIfAbsent(
      @Param("id") UUID id,
      @Param("email") String email,
      @Param("taxId") String taxId,
      @Param("request") String requestJson,
      @Param("tokenHash") String tokenHash,
      @Param("termsAcceptedAt") Instant termsAcceptedAt,
      @Param("expiresAt") Instant expiresAt);
}
