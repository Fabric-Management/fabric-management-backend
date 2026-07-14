package com.fabricmanagement.sales.quote.infra.repository;

import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, UUID> {

  @EntityGraph(attributePaths = "lines")
  Optional<Quote> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);

  Optional<Quote> findByTenantIdAndQuoteNumberAndIsActiveTrue(UUID tenantId, String quoteNumber);

  @EntityGraph(attributePaths = "lines")
  Page<Quote> findAllByTenantIdAndIsActiveTrue(UUID tenantId, Pageable pageable);

  @EntityGraph(attributePaths = "lines")
  Page<Quote> findAllByTenantIdAndStatusAndIsActiveTrue(
      UUID tenantId, QuoteStatus status, Pageable pageable);

  @EntityGraph(attributePaths = "lines")
  @Query(
      """
      SELECT q FROM Quote q
      WHERE q.tenantId = :tenantId
        AND q.isActive = true
        AND (:status IS NULL OR q.status = :status)
        AND LOWER(q.quoteNumber) LIKE LOWER(:pattern) ESCAPE :escapeCharacter
      """)
  Page<Quote> searchByQuoteNumber(
      @Param("tenantId") UUID tenantId,
      @Param("status") QuoteStatus status,
      @Param("pattern") String pattern,
      @Param("escapeCharacter") char escapeCharacter,
      Pageable pageable);

  @EntityGraph(attributePaths = "lines")
  @Query(
      """
      SELECT q FROM Quote q
      WHERE q.tenantId = :tenantId
        AND q.isActive = true
        AND (:status IS NULL OR q.status = :status)
        AND (
          LOWER(q.quoteNumber) LIKE LOWER(:pattern) ESCAPE :escapeCharacter
          OR q.customerId IN :customerIds
        )
      """)
  Page<Quote> searchByQuoteNumberOrCustomerId(
      @Param("tenantId") UUID tenantId,
      @Param("status") QuoteStatus status,
      @Param("pattern") String pattern,
      @Param("escapeCharacter") char escapeCharacter,
      @Param("customerIds") List<UUID> customerIds,
      Pageable pageable);

  @Query(
      """
      SELECT q.status AS status, COUNT(q.id) AS count
      FROM Quote q
      WHERE q.tenantId = :tenantId AND q.isActive = true
      GROUP BY q.status
      """)
  List<StatusCountProjection> countActiveByStatus(@Param("tenantId") UUID tenantId);

  interface StatusCountProjection {
    QuoteStatus getStatus();

    Long getCount();
  }

  List<Quote> findAllByTenantIdAndCustomerIdAndIsActiveTrue(UUID tenantId, UUID customerId);

  @Query(
      """
      SELECT q FROM Quote q
      WHERE q.tenantId = :tenantId
        AND q.assignedToId = :salespersonId
        AND q.isActive = true
        ORDER BY q.createdAt DESC
      """)
  List<Quote> findRecentBySalesperson(
      @Param("tenantId") UUID tenantId, @Param("salespersonId") UUID salespersonId);
}
