package com.fabricmanagement.sales.infra.repository;

import com.fabricmanagement.sales.domain.quote.QuoteApprovalToken;
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
}
