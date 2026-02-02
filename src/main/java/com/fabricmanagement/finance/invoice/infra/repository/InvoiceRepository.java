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

/**
 * Repository for Invoice entity.
 *
 * <p>All queries are tenant-scoped for data isolation.
 */
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

  // ═══════════════════════════════════════════════════════════════════════════
  // Basic Lookups
  // ═══════════════════════════════════════════════════════════════════════════

  Optional<Invoice> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<Invoice> findByTenantIdAndUid(UUID tenantId, String uid);

  Optional<Invoice> findByTenantIdAndInvoiceNumber(UUID tenantId, String invoiceNumber);

  // ═══════════════════════════════════════════════════════════════════════════
  // TradingPartner Queries (Faz 1.5)
  // ═══════════════════════════════════════════════════════════════════════════

  /** Find invoices by trading partner. */
  List<Invoice> findByTenantIdAndTradingPartnerId(UUID tenantId, UUID tradingPartnerId);

  /** Find active invoices by trading partner. */
  @Query(
      """
      SELECT i FROM Invoice i
      WHERE i.tenantId = :tenantId
      AND i.tradingPartnerId = :partnerId
      AND i.isActive = true
      ORDER BY i.issueDate DESC
      """)
  List<Invoice> findActiveByPartner(
      @Param("tenantId") UUID tenantId, @Param("partnerId") UUID partnerId);

  /** Find unpaid invoices by trading partner. */
  @Query(
      """
      SELECT i FROM Invoice i
      WHERE i.tenantId = :tenantId
      AND i.tradingPartnerId = :partnerId
      AND i.isActive = true
      AND i.status IN ('SENT', 'PARTIALLY_PAID', 'OVERDUE')
      ORDER BY i.dueDate ASC
      """)
  List<Invoice> findUnpaidByPartner(
      @Param("tenantId") UUID tenantId, @Param("partnerId") UUID partnerId);

  /** Find invoices by trading partner with pagination. */
  Page<Invoice> findByTenantIdAndTradingPartnerIdAndIsActiveTrue(
      UUID tenantId, UUID tradingPartnerId, Pageable pageable);

  // ═══════════════════════════════════════════════════════════════════════════
  // Status Queries
  // ═══════════════════════════════════════════════════════════════════════════

  List<Invoice> findByTenantIdAndStatus(UUID tenantId, InvoiceStatus status);

  List<Invoice> findByTenantIdAndStatusIn(UUID tenantId, List<InvoiceStatus> statuses);

  /** Find overdue invoices. */
  @Query(
      """
      SELECT i FROM Invoice i
      WHERE i.tenantId = :tenantId
      AND i.isActive = true
      AND i.dueDate < :today
      AND i.status IN ('SENT', 'PARTIALLY_PAID')
      ORDER BY i.dueDate ASC
      """)
  List<Invoice> findOverdueInvoices(
      @Param("tenantId") UUID tenantId, @Param("today") LocalDate today);

  /** Find invoices awaiting payment. */
  @Query(
      """
      SELECT i FROM Invoice i
      WHERE i.tenantId = :tenantId
      AND i.isActive = true
      AND i.status IN ('SENT', 'PARTIALLY_PAID', 'OVERDUE')
      ORDER BY i.dueDate ASC
      """)
  List<Invoice> findAwaitingPayment(@Param("tenantId") UUID tenantId);

  // ═══════════════════════════════════════════════════════════════════════════
  // Type Queries (AR/AP)
  // ═══════════════════════════════════════════════════════════════════════════

  List<Invoice> findByTenantIdAndInvoiceType(UUID tenantId, InvoiceType invoiceType);

  /** Find AR invoices (sales). */
  @Query(
      """
      SELECT i FROM Invoice i
      WHERE i.tenantId = :tenantId
      AND i.isActive = true
      AND i.invoiceType = 'SALES'
      ORDER BY i.issueDate DESC
      """)
  List<Invoice> findAccountsReceivable(@Param("tenantId") UUID tenantId);

  /** Find AP invoices (purchases). */
  @Query(
      """
      SELECT i FROM Invoice i
      WHERE i.tenantId = :tenantId
      AND i.isActive = true
      AND i.invoiceType = 'PURCHASE'
      ORDER BY i.issueDate DESC
      """)
  List<Invoice> findAccountsPayable(@Param("tenantId") UUID tenantId);

  // ═══════════════════════════════════════════════════════════════════════════
  // Date Queries
  // ═══════════════════════════════════════════════════════════════════════════

  List<Invoice> findByTenantIdAndIssueDateBetween(
      UUID tenantId, LocalDate startDate, LocalDate endDate);

  List<Invoice> findByTenantIdAndDueDateBetween(
      UUID tenantId, LocalDate startDate, LocalDate endDate);

  // ═══════════════════════════════════════════════════════════════════════════
  // Pagination
  // ═══════════════════════════════════════════════════════════════════════════

  Page<Invoice> findByTenantIdAndIsActiveTrue(UUID tenantId, Pageable pageable);

  Page<Invoice> findByTenantIdAndStatusAndIsActiveTrue(
      UUID tenantId, InvoiceStatus status, Pageable pageable);

  Page<Invoice> findByTenantIdAndInvoiceTypeAndIsActiveTrue(
      UUID tenantId, InvoiceType invoiceType, Pageable pageable);

  // ═══════════════════════════════════════════════════════════════════════════
  // Aggregations
  // ═══════════════════════════════════════════════════════════════════════════

  long countByTenantIdAndStatus(UUID tenantId, InvoiceStatus status);

  long countByTenantIdAndTradingPartnerIdAndIsActiveTrue(UUID tenantId, UUID tradingPartnerId);

  @Query(
      """
      SELECT COALESCE(SUM(i.amountDue), 0) FROM Invoice i
      WHERE i.tenantId = :tenantId
      AND i.tradingPartnerId = :partnerId
      AND i.isActive = true
      AND i.status IN ('SENT', 'PARTIALLY_PAID', 'OVERDUE')
      """)
  java.math.BigDecimal sumOutstandingByPartner(
      @Param("tenantId") UUID tenantId, @Param("partnerId") UUID partnerId);

  // ═══════════════════════════════════════════════════════════════════════════
  // Invoice Number Generation
  // ═══════════════════════════════════════════════════════════════════════════

  @Query(
      """
      SELECT MAX(i.invoiceNumber) FROM Invoice i
      WHERE i.tenantId = :tenantId
      AND i.invoiceNumber LIKE :prefix%
      """)
  Optional<String> findMaxInvoiceNumber(
      @Param("tenantId") UUID tenantId, @Param("prefix") String prefix);
}
