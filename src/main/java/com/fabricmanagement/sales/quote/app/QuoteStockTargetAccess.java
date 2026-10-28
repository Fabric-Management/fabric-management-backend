package com.fabricmanagement.sales.quote.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver.PermissionFreshness;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewRequest;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Quote aggregate first, stock gate second. Conversion provenance is never an editable target. */
@Component
@RequiredArgsConstructor
public class QuoteStockTargetAccess {
  private final QuoteRepository quotes;
  private final QuoteAccessPolicy access;
  private final EntityManager entityManager;

  public Quote writable(UUID quoteId, UUID actor) {
    var quote = lock(quoteId, actor);
    if (quote.getStatus() != QuoteStatus.DRAFT && quote.getStatus() != QuoteStatus.EVALUATION)
      throw OrderIntakeException.conflict(
          "QUOTE_NOT_EDITABLE", "Only an unsent draft quote can change its stock choice");
    return quote;
  }

  public Quote lock(UUID quoteId, UUID actor) {
    var quote =
        quotes
            .findByTenantIdAndIdAndIsActiveTrue(TenantContext.requireTenantId(), quoteId)
            .orElseThrow(() -> OrderIntakeException.notFound("Quote", quoteId));
    entityManager.refresh(quote, LockModeType.PESSIMISTIC_WRITE);
    if (!quote.isActive()
        || !access.canWrite(
            TenantContext.requireTenantId(), actor, quote, PermissionFreshness.FRESH))
      throw OrderIntakeException.notFound("Quote", quoteId);
    return quote;
  }

  public void verifyReadContext(UUID quoteId, UUID lineId, UUID productId, UUID actor) {
    var quote =
        quotes
            .findByTenantIdAndIdAndIsActiveTrue(TenantContext.requireTenantId(), quoteId)
            .filter(
                value ->
                    access.canWrite(
                        TenantContext.requireTenantId(), actor, value, PermissionFreshness.FRESH))
            .orElseThrow(() -> OrderIntakeException.notFound("Quote", quoteId));
    if (quote.getStatus() != QuoteStatus.DRAFT && quote.getStatus() != QuoteStatus.EVALUATION)
      throw OrderIntakeException.conflict(
          "QUOTE_NOT_EDITABLE", "The stock context is no longer editable");
    if (lineId != null && !line(quote, lineId).getProductId().equals(productId))
      throw OrderIntakeException.productNotAvailable(productId);
  }

  public QuoteLine line(Quote quote, UUID lineId) {
    return quote
        .findLine(lineId)
        .filter(QuoteLine::isActive)
        .orElseThrow(() -> OrderIntakeException.notFound("Quote line", lineId));
  }

  public void assertTerms(
      Quote quote, UUID lineId, UUID productId, OrderIntakeStockPreviewRequest request) {
    if (!Objects.equals(quote.getCustomerId(), request.customerId())
        || !Objects.equals(productId, request.productId()))
      throw OrderIntakeException.conflict(
          "STOCK_TARGET_CHANGED", "The proposal belongs to different quote terms");
    if (lineId == null) return;
    var line = line(quote, lineId);
    var original =
        line.getCustomerRequestQty() == null
            ? line.getRequestedQty()
            : line.getCustomerRequestQty();
    if (!Objects.equals(line.getProductId(), productId)
        || !Objects.equals(line.getColorId(), request.colorId())
        || !line.getUnit().equalsIgnoreCase(request.unit())
        || original.compareTo(request.requestedQty()) != 0
        || (line.getFinishedWidth() != null
            && (request.finishedWidth() == null
                || line.getFinishedWidth().compareTo(request.finishedWidth()) != 0
                || !Objects.equals(line.getFinishedWidthUnit(), request.finishedWidthUnit())))
        || line.isSingleLotRequired() != Boolean.TRUE.equals(request.singleLotRequired())
        || line.getToleranceUpPct() != null
            && (request.toleranceUpPct() == null
                || line.getToleranceUpPct().compareTo(request.toleranceUpPct()) != 0)
        || line.getToleranceDownPct() != null
            && (request.toleranceDownPct() == null
                || line.getToleranceDownPct().compareTo(request.toleranceDownPct()) != 0))
      throw OrderIntakeException.conflict(
          "STOCK_TARGET_CHANGED", "The quote line changed; evaluate its current terms");
  }
}
