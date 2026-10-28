package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.sales.orderintake.app.ConfirmationGate;
import com.fabricmanagement.sales.salesorder.domain.CatalogLineInput;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The order-intake rules the classic sales-order write paths must honour (SOI). {@link
 * SalesOrderService} calls only this seam, so create, confirmation and cancellation share one
 * implementation of the intake rules instead of each carrying its own copy.
 */
@Component
@RequiredArgsConstructor
public class OrderIntakeHooks {

  static final String CANCELLATION_RELEASE = "ORDER_CANCELLED";

  private final CatalogLineValidator catalogLineValidator;
  private final ConfirmationGate confirmationGate;
  private final com.fabricmanagement.sales.orderintake.app.DraftStockSelectionService draftStock;
  private final com.fabricmanagement.sales.salesorder.domain.port.QuoteStockOwnershipTransferPort
      quoteStock;

  public void prepareSourceLines(
      SalesOrder order,
      List<SalesOrderLine> lines,
      List<com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest> requests,
      UUID actor) {
    for (int index = 0; index < requests.size(); index++) {
      var source = requests.get(index).getSourceQuoteLineId();
      if (source == null) continue;
      if (order.getQuoteId() == null)
        throw com.fabricmanagement.sales.common.exception.OrderIntakeException.rule(
            "QUOTE_STOCK_SOURCE_INVALID", "Quote source is required");
      quoteStock.prepareLine(order, lines.get(index), source, actor);
    }
  }

  public void transferOnCreation(
      SalesOrder order,
      List<SalesOrderLine> lines,
      List<com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest> requests,
      UUID actor) {
    if (requests == null) return;
    var sourceLines =
        requests.stream()
            .map(
                com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest
                    ::getSourceQuoteLineId)
            .filter(java.util.Objects::nonNull)
            .toList();
    if (sourceLines.size() != java.util.Set.copyOf(sourceLines).size())
      throw com.fabricmanagement.sales.common.exception.OrderIntakeException.rule(
          "QUOTE_STOCK_SOURCE_REPEATED", "A source quote line may be mapped only once");
    if (requests.stream().anyMatch(line -> line.getSourceQuoteLineId() != null)) {
      if (order.getQuoteId() == null)
        throw com.fabricmanagement.sales.common.exception.OrderIntakeException.rule(
            "QUOTE_STOCK_SOURCE_INVALID", "Quote source is required");
      // Root quote first, before any provisional line takes the stock writer gate.
      quoteStock.prepare(order, actor);
    }
    for (int index = 0; index < requests.size(); index++) {
      var request = requests.get(index);
      try {
        if (request.getCustomerRequestQty() != null
            && request.getCustomerRequestQty().compareTo(request.getRequestedQty()) != 0
            && request.getStockSelection() == null
            && request.getSourceQuoteLineId() == null)
          throw com.fabricmanagement.sales.common.exception.OrderIntakeException.rule(
              "STOCK_CHOICE_REQUIRED",
              "A different commercial quantity needs its applied stock choice");
        if (request.getSourceQuoteLineId() != null) {
          if (order.getQuoteId() == null
              || request.getStockSelection() != null
              || request.getPreviewStockSelection() != null)
            throw com.fabricmanagement.sales.common.exception.OrderIntakeException.rule(
                "QUOTE_STOCK_SOURCE_INVALID",
                "Quote conversion needs one explicit source and no provisional choice");
          quoteStock.transfer(order, lines.get(index), request.getSourceQuoteLineId(), actor);
        }
        if (request.getStockSelection() != null || request.getPreviewStockSelection() != null) {
          if (request.getClientLineId() == null || order.getCreationKey() == null)
            throw com.fabricmanagement.sales.common.exception.OrderIntakeException.rule(
                "STOCK_SELECTION_IDENTITY_REQUIRED",
                "Stock choices need a form line identity and an order creation key");
          if (request.getStockSelection() != null && request.getPreviewStockSelection() != null)
            throw com.fabricmanagement.sales.common.exception.OrderIntakeException.rule(
                "STOCK_SELECTION_AMBIGUOUS", "A line cannot apply and discard a preview together");
          if (request.getStockSelection() != null)
            draftStock.transferOnCreate(
                order,
                lines.get(index),
                request.getStockSelection(),
                request.getClientLineId(),
                actor);
          else
            draftStock.discardUnapplied(
                request.getPreviewStockSelection(), request.getClientLineId(), actor);
        }
      } catch (com.fabricmanagement.common.infrastructure.web.exception.DomainException failure) {
        throw failure.withDetail("clientLineId", request.getClientLineId());
      }
    }
  }

  /** Catalogue rules over every active line the order will hold after the write (SOI R02–R06). */
  public void validateLines(
      UUID tenantId, UUID customerId, Collection<? extends CatalogLineInput> lines) {
    catalogLineValidator.validate(tenantId, customerId, lines);
  }

  /** Refuses a confirmation or an approval request the intake rules block; changes nothing. */
  public void checkConfirmable(SalesOrder order, List<SalesOrderLine> lines) {
    List<ConfirmationGate.Block> blocks = confirmationGate.blocks(order, lines);
    if (!blocks.isEmpty()) {
      throw ConfirmationGate.toException(blocks.getFirst());
    }
  }

  /** The intake blocks as readable codes; empty when the order may be confirmed. */
  public List<String> confirmationBlockers(SalesOrder order, List<SalesOrderLine> lines) {
    return confirmationGate.blocks(order, lines).stream()
        .map(block -> block.lineId() == null ? block.code() : block.code() + "@" + block.lineId())
        .toList();
  }

  /** Re-checks and holds the accepted pieces in the confirmation transaction (SOI A05). */
  public void allocateAtConfirmation(SalesOrder order, List<SalesOrderLine> lines, UUID actor) {
    confirmationGate.allocate(order, lines, actor != null ? actor : SystemUser.ID);
  }

  /** A cancelled order gives its held pieces back. */
  public void releaseOnCancellation(Collection<UUID> lineIds, UUID actor) {
    confirmationGate.release(lineIds, actor != null ? actor : SystemUser.ID, CANCELLATION_RELEASE);
  }
}
