package com.fabricmanagement.procurement.quote.infra.repository;

import com.fabricmanagement.procurement.quote.domain.SupplierQuoteToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierQuoteTokenRepository extends JpaRepository<SupplierQuoteToken, UUID> {
  Optional<SupplierQuoteToken> findByTokenAndIsActiveTrue(String token);

  @Query(
      """
      SELECT t FROM SupplierQuoteToken t
      WHERE t.rfqRecipientId = :recipientId
        AND t.status = 'PENDING'
        AND t.isActive = true
      """)
  Optional<SupplierQuoteToken> findPendingByRecipientId(@Param("recipientId") UUID recipientId);
}
