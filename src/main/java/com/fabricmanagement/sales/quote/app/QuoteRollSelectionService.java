package com.fabricmanagement.sales.quote.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.stockunit.api.StockUnitSoftHoldPort;
import com.fabricmanagement.sales.common.dto.SalesQuantityTermsDto;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.app.DraftStockSelectionService;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import com.fabricmanagement.sales.quote.domain.QuoteStockOperation;
import com.fabricmanagement.sales.quote.dto.QuoteRollSelectionDto;
import com.fabricmanagement.sales.quote.dto.QuoteRollSelectionRequests;
import com.fabricmanagement.sales.quote.dto.UpdateQuoteLineRequest;
import com.fabricmanagement.sales.quote.infra.repository.QuoteStockOperationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Versioned commercial commands. Quote lock precedes every product/lot/lease gate. */
@Service
@RequiredArgsConstructor
public class QuoteRollSelectionService {
  private final QuoteStockTargetAccess access;
  private final DraftStockSelectionService stock;
  private final QuoteService quotes;
  private final QuoteStockOperationRepository operations;
  private final StockUnitSoftHoldPort holds;
  private final EntityManager entityManager;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final com.fabricmanagement.sales.orderintake.app.ProvisionalStockLifecycleService
      provisionalLifecycle;

  @Transactional
  public QuoteRollSelectionDto apply(
      UUID quoteId, UUID lineId, QuoteRollSelectionRequests.Apply request, UUID actor) {
    var quote = access.lock(quoteId, actor);
    String hash = hash(List.of("APPLY", quoteId, lineId, request));
    var replay = replay(quoteId, request.operationId(), actor, hash);
    if (replay != null) return replay;
    requireVersion(quote, request.expectedVersion(), actor);
    var claim = stock.claimForQuote(quoteId, lineId, null, request.selection(), actor);
    var line = access.line(quote, lineId);
    var update = unchanged(line);
    update.setRequestedQty(
        claim.cutSourceId() == null
            ? claim.option().quantity()
            : claim.selection().getRequest().requestedQty());
    update.setSelectedLots(
        claim.cutSourceId() == null ? QuoteService.stockLots(claim.option()) : List.of());
    quotes.updateQuoteLineWithStock(quoteId, lineId, update, claim);
    return complete(quote, line, request.operationId(), actor, hash);
  }

  @Transactional
  public QuoteRollSelectionDto create(
      UUID quoteId, QuoteRollSelectionRequests.Create request, UUID actor) {
    var quote = access.lock(quoteId, actor);
    String hash = hash(List.of("CREATE", quoteId, request));
    var replay = replay(quoteId, request.operationId(), actor, hash);
    if (replay != null) return replay;
    requireVersion(quote, request.expectedVersion(), actor);
    if (request.line().getStockSelection() == null || request.line().getClientLineId() == null)
      throw OrderIntakeException.rule(
          "STOCK_SELECTION_IDENTITY_REQUIRED",
          "A stock line needs its applied proof and form line identity");
    int before = quote.getLines().size();
    quotes.addQuoteLine(quoteId, request.line(), actor);
    return complete(quote, quote.getLines().get(before), request.operationId(), actor, hash);
  }

  @Transactional
  public QuoteRollSelectionDto remove(
      UUID quoteId, UUID lineId, QuoteRollSelectionRequests.Remove request, UUID actor) {
    var quote = access.lock(quoteId, actor);
    String hash = hash(List.of("REMOVE", quoteId, lineId, request));
    var replay = replay(quoteId, request.operationId(), actor, hash);
    if (replay != null) return replay;
    requireVersion(quote, request.expectedVersion(), actor);
    var line = access.line(quote, lineId);
    var update = unchanged(line);
    update.setSelectedLots(List.of());
    // Releasing stock does not change the commercial quantity or invent an original request.
    quotes.updateQuoteLine(quoteId, lineId, update);
    provisionalLifecycle.ownerLine(lineId);
    return complete(quote, line, request.operationId(), actor, hash);
  }

  @Transactional
  public QuoteRollSelectionDto policy(
      UUID quoteId, UUID lineId, QuoteRollSelectionRequests.Policy request, UUID actor) {
    var quote = access.lock(quoteId, actor);
    String hash = hash(List.of("POLICY", quoteId, lineId, request));
    var replay = replay(quoteId, request.operationId(), actor, hash);
    if (replay != null) return replay;
    requireVersion(quote, request.expectedVersion(), actor);
    var line = access.line(quote, lineId);
    String previousTerms = QuoteService.stockTerms(line);
    line.recordQuantityPolicy(request.quantityPolicy(), actor, clock.instant(), request.reason());
    if (!previousTerms.equals(QuoteService.stockTerms(line)))
      provisionalLifecycle.ownerLine(lineId);
    quotes.advanceStockRevision(quote, request.expectedVersion());
    return complete(quote, line, request.operationId(), actor, hash);
  }

  private void requireVersion(Quote quote, Long version, UUID actor) {
    access.writable(quote.getId(), actor);
    if (!Objects.equals(quote.getVersion(), version))
      throw OrderIntakeException.conflict(
          "STALE_VERSION", "The quote changed; reload before applying");
  }

  private UpdateQuoteLineRequest unchanged(QuoteLine line) {
    var request = new UpdateQuoteLineRequest();
    request.setRequestedQty(line.getRequestedQty());
    request.setUnit(line.getUnit());
    request.setOfferedPrice(line.getOfferedPrice());
    request.setQualityGradeId(line.getQualityGradeId());
    request.setColorId(line.getColorId());
    request.setDeliveryStatus(line.getDeliveryStatus());
    request.setDeliveryDate(line.getDeliveryDate());
    request.setFulfillmentMode(line.getFulfillmentMode());
    return request;
  }

  private QuoteRollSelectionDto complete(
      Quote quote, QuoteLine line, UUID operation, UUID actor, String hash) {
    entityManager.flush();
    // The shared quote writer advanced the aggregate before this immutable receipt.
    var result =
        new QuoteRollSelectionDto(
            quote.getId(),
            line.getId(),
            quote.getVersion(),
            line.getRequestedQty(),
            line.getCustomerRequestQty(),
            SalesQuantityTermsDto.from(line.getQuantityTerms()),
            holds.heldPieceIds(line.getId()));
    operations.saveAndFlush(
        QuoteStockOperation.record(quote.getId(), actor, operation, hash, result));
    return result;
  }

  private QuoteRollSelectionDto replay(UUID quoteId, UUID operation, UUID actor, String hash) {
    entityManager
        .createNativeQuery(
            "select 1 from (select pg_advisory_xact_lock(hashtextextended(:key, 0))) held")
        .setParameter(
            "key", TenantContext.requireTenantId() + ":QUOTE_STOCK_OPERATION:" + operation)
        .getSingleResult();
    var found = operations.findByTenantIdAndOperationId(TenantContext.requireTenantId(), operation);
    if (found.isEmpty()) return null;
    var value = found.get();
    if (!value.getQuoteId().equals(quoteId)
        || !value.getActorId().equals(actor)
        || !value.getRequestHash().equals(hash))
      throw OrderIntakeException.conflict(
          "IDEMPOTENCY_KEY_REUSED", "The operation belongs to another quote command");
    return value.getResult();
  }

  private String hash(Object value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
    } catch (JsonProcessingException | NoSuchAlgorithmException failure) {
      throw new IllegalStateException("Cannot fingerprint quote command", failure);
    }
  }
}
