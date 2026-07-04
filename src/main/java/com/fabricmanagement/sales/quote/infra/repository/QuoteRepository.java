package com.fabricmanagement.sales.quote.infra.repository;

import com.fabricmanagement.sales.quote.domain.Quote;
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
