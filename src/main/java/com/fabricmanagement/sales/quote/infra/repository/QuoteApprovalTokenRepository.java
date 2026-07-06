package com.fabricmanagement.sales.quote.infra.repository;

import com.fabricmanagement.sales.quote.domain.QuoteApprovalToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface QuoteApprovalTokenRepository extends JpaRepository<QuoteApprovalToken, UUID> {

  Optional<QuoteApprovalToken> findByTokenAndIsActiveTrue(String token);

  /** Finds the active pending token for a given quote. */
  @Query(
      """
      SELECT t FROM QuoteApprovalToken t
      WHERE t.quoteId = :quoteId
        AND t.status = 'PENDING'
        AND t.isActive = true
      """)
  Optional<QuoteApprovalToken> findPendingByQuoteId(@Param("quoteId") UUID quoteId);

  @Query(
      """
      SELECT t FROM QuoteApprovalToken t
      WHERE t.tenantId = :tenantId
        AND t.quoteId = :quoteId
        AND t.status = 'PENDING'
        AND t.isActive = true
      """)
  List<QuoteApprovalToken> findPendingByTenantIdAndQuoteId(
      @Param("tenantId") UUID tenantId, @Param("quoteId") UUID quoteId);
}
