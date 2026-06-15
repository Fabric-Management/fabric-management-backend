package com.fabricmanagement.finance.payment.infra.repository;

import com.fabricmanagement.finance.payment.domain.PaymentAllocation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {

  Optional<PaymentAllocation>
      findFirstByTenantIdAndPaymentIdAndInvoiceIdAndIsActiveTrueOrderByCreatedAtDesc(
          UUID tenantId, UUID paymentId, UUID invoiceId);
}
