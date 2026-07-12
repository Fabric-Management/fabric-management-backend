package com.fabricmanagement.procurement.quote.app;

import com.fabricmanagement.common.infrastructure.events.StuckEventPresentation;
import com.fabricmanagement.common.infrastructure.events.StuckEventPresenter;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.infra.repository.SupplierQuoteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SupplierQuoteStuckPresenter implements StuckEventPresenter {

  private static final String EVENT_TYPE = "SUPPLIER_QUOTE_ACCEPTED";
  private static final String ENTITY_TYPE = "SUPPLIER_QUOTE";

  private final SupplierQuoteRepository quoteRepository;

  @Override
  public boolean supports(String eventType) {
    return EVENT_TYPE.equals(eventType);
  }

  @Override
  public StuckEventPresentation present(UUID tenantId, JsonNode payload) {
    UUID quoteId = parseQuoteId(payload);
    if (quoteId == null) {
      return fallback(null);
    }

    return quoteRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId)
        .map(quote -> presentation(quoteId, quote))
        .orElseGet(() -> fallback(quoteId));
  }

  private StuckEventPresentation presentation(UUID quoteId, SupplierQuote quote) {
    UUID affectedUserId = SystemUser.ID.equals(quote.getCreatedBy()) ? null : quote.getCreatedBy();
    return new StuckEventPresentation(
        ENTITY_TYPE,
        quoteId,
        quote.getQuoteNumber(),
        "Purchase order creation for quote " + quote.getQuoteNumber() + " did not complete.",
        ENTITY_TYPE,
        quoteId,
        affectedUserId);
  }

  private StuckEventPresentation fallback(UUID quoteId) {
    return new StuckEventPresentation(
        ENTITY_TYPE,
        quoteId,
        null,
        "Purchase order creation for a supplier quote did not complete.",
        quoteId == null ? null : ENTITY_TYPE,
        quoteId,
        null);
  }

  private UUID parseQuoteId(JsonNode payload) {
    try {
      JsonNode quoteId = payload == null ? null : payload.get("quoteId");
      return quoteId == null || quoteId.isNull() ? null : UUID.fromString(quoteId.asText());
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
