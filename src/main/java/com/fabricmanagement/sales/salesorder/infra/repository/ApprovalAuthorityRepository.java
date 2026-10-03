package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthority;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalAuthorityRepository extends JpaRepository<ApprovalAuthority, UUID> {

  /** Every authority recorded for the customer, newest first, revoked ones included. */
  List<ApprovalAuthority> findByTenantIdAndTradingPartnerIdOrderByGrantedAtDesc(
      UUID tenantId, UUID tradingPartnerId);

  /** The contact's authority that is not revoked; at most one exists (partial unique index). */
  Optional<ApprovalAuthority> findByTenantIdAndTradingPartnerIdAndContactIdAndRevokedAtIsNull(
      UUID tenantId, UUID tradingPartnerId, UUID contactId);

  /** The contact's latest authority for the customer, ended or not. */
  Optional<ApprovalAuthority>
      findFirstByTenantIdAndTradingPartnerIdAndContactIdOrderByGrantedAtDesc(
          UUID tenantId, UUID tradingPartnerId, UUID contactId);

  /**
   * The open authorities bound to a contact point, their rows locked until the transaction ends. A
   * row ended by another transaction while this one waited is re-checked by the database and left
   * out.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select a from ApprovalAuthority a where a.tenantId = :tenantId and a.contactId = :contactId"
          + " and a.revokedAt is null")
  List<ApprovalAuthority> lockOpenByTenantIdAndContactId(
      @Param("tenantId") UUID tenantId, @Param("contactId") UUID contactId);

  /** The authority a request was sent under, revoked or not. */
  Optional<ApprovalAuthority> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<ApprovalAuthority> findByTenantIdAndTradingPartnerIdAndId(
      UUID tenantId, UUID tradingPartnerId, UUID id);

  /**
   * The authority, its row locked until the transaction ends. Revoking and every use that sends or
   * decides under the authority take this lock, so they happen one after the other: a revocation
   * that committed first is seen by the later use, and a use in progress finishes before the
   * revocation (ADR-0014 OD-13). An instance already loaded in the same transaction keeps its old
   * state; the caller refreshes it after the lock.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from ApprovalAuthority a where a.tenantId = :tenantId and a.id = :id")
  Optional<ApprovalAuthority> lockByTenantIdAndId(
      @Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
