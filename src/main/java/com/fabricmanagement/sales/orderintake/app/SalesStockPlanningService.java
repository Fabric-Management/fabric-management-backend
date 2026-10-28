package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.AppRoutes;
import com.fabricmanagement.production.core.stockunit.api.StockUnitSoftHoldPort;
import com.fabricmanagement.sales.common.domain.SalesQuantityTerms;
import com.fabricmanagement.sales.common.dto.SalesQuantityTermsDto;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.dto.SalesStockPlanningDto;
import com.fabricmanagement.sales.orderintake.dto.SalesStockPlanningDto.*;
import com.fabricmanagement.sales.quote.app.QuoteAccessPolicy;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesStockPlanningService {
  private final OrderIntakeAccess orders;
  private final QuoteRepository quotes;
  private final QuoteAccessPolicy quoteAccess;
  private final StockUnitSoftHoldPort holds;
  private final com.fabricmanagement.production.core.batch.api.BatchLotQuantityIntentPort intents;
  private final com.fabricmanagement.production.core.stockunit.api.PieceAllocationPort allocations;
  private final com.fabricmanagement.production.core.stockunit.api.StockUnitCutPlanPort cutPlans;

  public SalesStockPlanningDto order(UUID orderId, UUID lineId, UUID actor) {
    var order = orders.readableOrder(orderId, actor);
    var line = orders.line(order, lineId);
    return view(
        lineId,
        line.getQuantityTerms(),
        line.getRecordedCustomerRequestQty(),
        line.getRequestedQty(),
        allocations.activeForLine(TenantContext.requireTenantId(), lineId).stream()
            .map(
                com.fabricmanagement.production.core.stockunit.api.PieceAllocationPort
                        .PieceAllocationView
                    ::stockUnitId)
            .toList(),
        intents.hasActiveIntents(lineId),
        orders.canWrite(order, actor),
        order.getStatus() == OrderStatus.DRAFT,
        AppRoutes.salesOrder(orderId));
  }

  public SalesStockPlanningDto quote(UUID quoteId, UUID lineId, UUID actor) {
    var quote =
        quotes
            .findByTenantIdAndIdAndIsActiveTrue(TenantContext.requireTenantId(), quoteId)
            .filter(value -> quoteAccess.canRead(TenantContext.requireTenantId(), actor, value))
            .orElseThrow(() -> OrderIntakeException.notFound("Quote", quoteId));
    var line =
        quote
            .findLine(lineId)
            .filter(com.fabricmanagement.sales.quote.domain.QuoteLine::isActive)
            .orElseThrow(() -> OrderIntakeException.notFound("Quote line", lineId));
    return view(
        lineId,
        line.getQuantityTerms(),
        line.getCustomerRequestQty(),
        line.getRequestedQty(),
        List.of(),
        intents.hasActiveIntents(lineId),
        quoteAccess.canWrite(TenantContext.requireTenantId(), actor, quote),
        quote.getStatus() == QuoteStatus.DRAFT || quote.getStatus() == QuoteStatus.EVALUATION,
        AppRoutes.salesQuote(quoteId));
  }

  private SalesStockPlanningDto view(
      UUID lineId,
      SalesQuantityTerms terms,
      BigDecimal request,
      BigDecimal quantity,
      List<UUID> allocated,
      boolean lotIntent,
      boolean canWrite,
      boolean editable,
      String route) {
    var pieces = holds.heldPieceIds(lineId);
    var assignment =
        !allocated.isEmpty()
            ? Assignment.ROLLS_ALLOCATED
            : !pieces.isEmpty()
                ? Assignment.ROLLS_PRE_RESERVED
                : lotIntent ? Assignment.LOT_QUANTITY_INTENT : Assignment.UNASSIGNED;
    Reason reason =
        !canWrite ? Reason.WRITE_ACCESS_REQUIRED : !editable ? Reason.DOCUMENT_NOT_EDITABLE : null;
    return new SalesStockPlanningDto(
        lineId,
        SalesQuantityTermsDto.from(terms),
        request,
        quantity,
        assignment,
        Review.PLANNING_REQUIRED,
        terms != null
                && terms.getPolicy()
                    == com.fabricmanagement.sales.common.domain.SalesQuantityPolicy.EXACT
            ? CutAssessment.EXACT_QUANTITY_REVIEW_REQUIRED
            : CutAssessment.NOT_ASSESSED,
        pieces,
        allocated,
        List.of(
            new Capability(Action.SELECT_ROLLS, reason == null, reason, route, false),
            new Capability(Action.RECORD_QUANTITY_POLICY, reason == null, reason, route, false),
            new Capability(
                Action.APPLY_CUT_PLAN,
                reason == null
                    && terms != null
                    && terms.getPolicy()
                        == com.fabricmanagement.sales.common.domain.SalesQuantityPolicy.EXACT,
                reason != null
                    ? reason
                    : terms == null
                            || terms.getPolicy()
                                != com.fabricmanagement.sales.common.domain.SalesQuantityPolicy
                                    .EXACT
                        ? Reason.EXACT_QUANTITY_REQUIRED
                        : null,
                route,
                true)),
        cutPlans.currentForLine(lineId).orElse(null));
  }
}
