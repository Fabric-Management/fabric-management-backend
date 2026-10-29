package com.fabricmanagement.finance.payment.infra.repository;

import com.fabricmanagement.finance.payment.domain.Payment;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  /** Shared transaction gate with collection posting. No invoice/collection double-spend race. */
  @org.springframework.data.jpa.repository.Query(
      value =
          "select 1 from pg_advisory_xact_lock(hashtextextended(cast(:tenantId as text), 41004))",
      nativeQuery = true)
  int lockFinanceTenant(UUID tenantId);

  /**
   * The same gate, taken only if nobody holds it right now. Never waits: a reader that can do
   * without what the lock protects uses this and carries on when it is {@code false}.
   */
  @org.springframework.data.jpa.repository.Query(
      value = "select pg_try_advisory_xact_lock(hashtextextended(cast(:tenantId as text), 41004))",
      nativeQuery = true)
  boolean tryLockFinanceTenant(UUID tenantId);

  Optional<Payment> findByTenantIdAndId(UUID tenantId, UUID id);

  java.util.List<Payment> findByTenantIdAndIdIn(UUID tenantId, java.util.List<UUID> ids);

  Page<Payment> findByTenantId(UUID tenantId, Pageable pageable);

  Page<Payment> findByTenantIdAndTradingPartnerId(UUID tenantId, UUID partnerId, Pageable pageable);

  Page<Payment> findByTenantIdAndDirection(
      UUID tenantId, PaymentDirection direction, Pageable pageable);

  boolean existsByTenantIdAndPaymentNumber(UUID tenantId, String paymentNumber);
}
