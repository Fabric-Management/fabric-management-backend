package com.fabricmanagement.sales.quote.app;

import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Scoped quote-header queries exposed to other sales submodules. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuoteScopeQueryService {

  private final QuoteRepository quoteRepository;
  private final QuoteAccessPolicy accessPolicy;

  public Set<UUID> findReadableQuoteIds(
      UUID tenantId, UUID currentUserId, Collection<UUID> quoteIds) {
    if (quoteIds == null || quoteIds.isEmpty()) {
      return Set.of();
    }
    return accessPolicy.readableQuoteIds(
        tenantId, currentUserId, quoteRepository.findActiveHeadersByIds(tenantId, quoteIds));
  }

  public boolean canReadQuoteContainingLine(UUID tenantId, UUID currentUserId, UUID quoteLineId) {
    return quoteRepository
        .findActiveHeaderByLineId(tenantId, quoteLineId)
        .filter(quote -> accessPolicy.canRead(tenantId, currentUserId, quote))
        .isPresent();
  }
}
