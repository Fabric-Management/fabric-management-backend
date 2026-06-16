package com.fabricmanagement.finance.payment.infra.repository;

import com.fabricmanagement.finance.payment.domain.PaymentAllocation;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.domain.PaymentStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {

  Optional<PaymentAllocation>
      findFirstByTenantIdAndPaymentIdAndInvoiceIdAndIsActiveTrueOrderByCreatedAtDesc(
          UUID tenantId, UUID paymentId, UUID invoiceId);

  @Query(
      "SELECT i.tradingPartnerId, i.dueDate, p.paymentDate "
          + "FROM PaymentAllocation a "
          + "JOIN Payment p ON p.id = a.paymentId AND p.tenantId = a.tenantId "
          + "JOIN Invoice i ON i.id = a.invoiceId AND i.tenantId = a.tenantId "
          + "WHERE a.tenantId = :tenantId "
          + "AND a.isActive = true "
          + "AND p.status <> :voidedStatus "
          + "AND p.direction = :direction "
          + "AND p.paymentDate BETWEEN :fromDate AND :toDate")
  List<Object[]> findPaymentTimingRows(
      @Param("tenantId") UUID tenantId,
      @Param("direction") PaymentDirection direction,
      @Param("voidedStatus") PaymentStatus voidedStatus,
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate);
}
