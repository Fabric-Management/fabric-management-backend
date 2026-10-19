package com.fabricmanagement.sales.quote.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.stockunit.api.StockUnitSoftHoldPort;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.app.QuantityEvaluationService;
import com.fabricmanagement.sales.orderintake.domain.DraftStockSelection;
import com.fabricmanagement.sales.orderintake.domain.StockSelectionTerms;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeStockPreviewRequest;
import com.fabricmanagement.sales.orderintake.infra.repository.DraftStockSelectionRepository;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.port.QuoteStockOwnershipTransferPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Explicit quote-line mapping; customer, stock evidence and ownership are checked in create's
 * transaction.
 */
@Service
@RequiredArgsConstructor
public class QuoteStockOwnershipTransferService implements QuoteStockOwnershipTransferPort {
  private final QuoteRepository quotes;
  private final QuoteAccessPolicy access;
  private final StockUnitSoftHoldPort holds;
  private final QuantityEvaluationService evaluation;
  private final DraftStockSelectionRepository selections;
  private final EntityManager entityManager;
  private final Clock clock;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void prepare(SalesOrder order, UUID actor) {
    var quote =
        quotes
            .findByTenantIdAndIdAndIsActiveTrue(TenantContext.requireTenantId(), order.getQuoteId())
            .filter(
                value ->
                    access.canWrite(
                        TenantContext.requireTenantId(),
                        actor,
                        value,
                        com.fabricmanagement.sales.common.app.SalesAccessScopeResolver
                            .PermissionFreshness.FRESH))
            .orElseThrow(() -> OrderIntakeException.notFound("Source quote", order.getQuoteId()));
    entityManager.refresh(quote, LockModeType.PESSIMISTIC_WRITE);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void transfer(SalesOrder order, SalesOrderLine target, UUID sourceLineId, UUID actor) {
    var quote =
        quotes
            .findByTenantIdAndIdAndIsActiveTrue(TenantContext.requireTenantId(), order.getQuoteId())
            .filter(
                value ->
                    access.canWrite(
                        TenantContext.requireTenantId(),
                        actor,
                        value,
                        com.fabricmanagement.sales.common.app.SalesAccessScopeResolver
                            .PermissionFreshness.FRESH))
            .orElseThrow(() -> OrderIntakeException.notFound("Source quote", order.getQuoteId()));
    entityManager.refresh(quote, LockModeType.PESSIMISTIC_WRITE);
    if (order.getCreationKey() == null)
      throw OrderIntakeException.rule(
          "STOCK_SELECTION_IDENTITY_REQUIRED",
          "Quote stock conversion needs an order creation key");
    if (quote.getStatus() != QuoteStatus.APPROVED
        || !Objects.equals(quote.getCustomerId(), order.getTradingPartnerId()))
      throw OrderIntakeException.conflict(
          "QUOTE_NOT_CONVERTIBLE", "An approved quote for this customer is required");
    var source =
        quote.getLines().stream()
            .filter(line -> line.isActive() && line.getId().equals(sourceLineId))
            .findFirst()
            .orElseThrow(() -> OrderIntakeException.notFound("Source quote line", sourceLineId));
    if (!Objects.equals(source.getProductId(), target.getProductId())
        || !Objects.equals(source.getColorId(), target.getColorId())
        || !source.getUnit().equalsIgnoreCase(target.getUnit())
        || source.getRequestedQty().compareTo(target.getRequestedQty()) != 0)
      throw OrderIntakeException.conflict(
          "QUOTE_LINE_CHANGED", "The target distribution differs from the source quote line");
    var ids = holds.heldPieceIds(sourceLineId);
    if (!ids.isEmpty()) {
      var request =
          new OrderIntakeStockPreviewRequest(
              target.getProductId(),
              target.getColorId(),
              target.getFinishedWidth(),
              target.getFinishedWidthUnit(),
              order.getTradingPartnerId(),
              target.getCustomerRequestQty(),
              target.getUnit(),
              target.isSingleLotRequired(),
              target.getToleranceUpPct(),
              target.getToleranceDownPct(),
              ids);
      var run =
          evaluation.run(
              new QuantityEvaluationService.EvaluationRequest(
                  sourceLineId,
                  request.productId(),
                  request.colorId(),
                  request.finishedWidth(),
                  request.finishedWidthUnit(),
                  request.customerId(),
                  request.requestedQty(),
                  request.unit(),
                  request.singleLotRequired(),
                  request.toleranceUpPct(),
                  request.toleranceDownPct(),
                  List.of(),
                  ids));
      var option =
          run.evaluation().result().options().stream()
              .filter(QuantityOption::applicable)
              .filter(value -> Set.copyOf(value.pieceIds()).equals(Set.copyOf(ids)))
              .findFirst()
              .orElseThrow(
                  () ->
                      OrderIntakeException.conflict(
                          "QUOTE_STOCK_CHANGED",
                          "The quote's named rolls are no longer usable for this distribution"));
      if (option.quantity().compareTo(target.getRequestedQty()) != 0)
        throw OrderIntakeException.conflict(
            "QUOTE_STOCK_CHANGED",
            "The named stock differs from the quote quantity; evaluate a new choice");
      var selected =
          DraftStockSelection.open(
              order.getCreationKey(), target.getId(), target.getProductId(), actor, null);
      selected.hold(request, option, List.of(), clock.instant());
      selected.transfer(
          order.getId(), target.getId(), StockSelectionTerms.fingerprint(order, target));
      selected.sourceQuote(order.getQuoteId(), sourceLineId);
      selections.save(selected);
    }
    holds.transferQuoteHolds(
        order.getQuoteId(), sourceLineId, order.getId(), order.getOrderNumber(), target.getId());
  }
}
