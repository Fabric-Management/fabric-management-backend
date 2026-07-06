package com.fabricmanagement.sales.quote.infra.repository;

import com.fabricmanagement.sales.quote.domain.QuoteSendRequest;
import com.fabricmanagement.sales.quote.domain.QuoteSendRequestStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteSendRequestRepository extends JpaRepository<QuoteSendRequest, UUID> {

  Optional<QuoteSendRequest> findByTenantIdAndIdAndIsActiveTrue(UUID tenantId, UUID id);

  Optional<QuoteSendRequest> findByTenantIdAndQuoteIdAndStatusAndIsActiveTrue(
      UUID tenantId, UUID quoteId, QuoteSendRequestStatus status);

  default Optional<QuoteSendRequest> findPendingByTenantIdAndQuoteId(UUID tenantId, UUID quoteId) {
    return findByTenantIdAndQuoteIdAndStatusAndIsActiveTrue(
        tenantId, quoteId, QuoteSendRequestStatus.PENDING);
  }
}
