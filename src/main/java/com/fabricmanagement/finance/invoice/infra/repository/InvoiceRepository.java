package com.fabricmanagement.finance.invoice.infra.repository;

import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

  Optional<Invoice> findByTenantIdAndId(UUID tenantId, UUID id);

  Page<Invoice> findByTenantId(UUID tenantId, Pageable pageable);

  Page<Invoice> findByTenantIdAndTradingPartnerId(
      UUID tenantId, UUID tradingPartnerId, Pageable pageable);

  Page<Invoice> findByTenantIdAndStatus(UUID tenantId, InvoiceStatus status, Pageable pageable);

  Page<Invoice> findByTenantIdAndInvoiceType(
      UUID tenantId, InvoiceType invoiceType, Pageable pageable);

  Page<Invoice> findByTenantIdAndTradingPartnerIdAndStatusNot(
      UUID tenantId, UUID tradingPartnerId, InvoiceStatus status, Pageable pageable);

  @Query(
      "SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.status IN "
          + "('SENT','PARTIALLY_PAID','OVERDUE') AND i.dueDate < :today")
  Page<Invoice> findOverdue(
      @Param("tenantId") UUID tenantId, @Param("today") LocalDate today, Pageable pageable);

  @Query(
      "SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.status IN "
          + "('SENT','PARTIALLY_PAID','OVERDUE')")
  Page<Invoice> findAwaitingPayment(@Param("tenantId") UUID tenantId, Pageable pageable);

  @Query(
      "SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.invoiceType = 'SALES' "
          + "AND i.status NOT IN ('CANCELLED','VOIDED','DRAFT')")
  Page<Invoice> findAccountsReceivable(@Param("tenantId") UUID tenantId, Pageable pageable);

  @Query(
      "SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.invoiceType = 'PURCHASE' "
          + "AND i.status NOT IN ('CANCELLED','VOIDED','DRAFT')")
  Page<Invoice> findAccountsPayable(@Param("tenantId") UUID tenantId, Pageable pageable);

  @Query(
      "SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.status IN "
          + "('SENT','PARTIALLY_PAID') AND i.dueDate < :today")
  List<Invoice> findInvoicesEligibleForOverdue(
      @Param("tenantId") UUID tenantId, @Param("today") LocalDate today);

  @Query(value = "SELECT nextval('finance.invoice_number_seq')", nativeQuery = true)
  Long getNextInvoiceSequence();

  boolean existsByTenantIdAndInvoiceNumber(UUID tenantId, String invoiceNumber);
}
