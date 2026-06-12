package com.fabricmanagement.finance.payment.infra.repository;

import com.fabricmanagement.finance.payment.domain.Payment;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  Optional<Payment> findByTenantIdAndId(UUID tenantId, UUID id);

  Page<Payment> findByTenantId(UUID tenantId, Pageable pageable);

  Page<Payment> findByTenantIdAndTradingPartnerId(UUID tenantId, UUID partnerId, Pageable pageable);

  Page<Payment> findByTenantIdAndDirection(
      UUID tenantId, PaymentDirection direction, Pageable pageable);

  boolean existsByTenantIdAndPaymentNumber(UUID tenantId, String paymentNumber);

  @Query(value = "SELECT nextval('finance.payment_number_seq')", nativeQuery = true)
  Long getNextPaymentSequence();
}
