package com.fabricmanagement.sales.orderintake.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.web.AppRoutes;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.core.stockunit.api.StockUnitCutTargetPort;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver.PermissionFreshness;
import com.fabricmanagement.sales.common.domain.SalesQuantityPolicy;
import com.fabricmanagement.sales.orderintake.domain.StockSelectionTerms;
import com.fabricmanagement.sales.orderintake.domain.event.SalesStockPlanningChangedEvent;
import com.fabricmanagement.sales.quote.app.QuoteAccessPolicy;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import com.fabricmanagement.sales.salesorder.app.SalesOrderAccessPolicy;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.infra.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Opaque commercial target facts, checked afresh before every production command. */
@Component
@RequiredArgsConstructor
public class StockUnitCutTargetAdapter implements StockUnitCutTargetPort {
  private final SalesOrderRepository orders;
  private final SalesOrderLineRepository lines;
  private final QuoteRepository quotes;
  private final SalesOrderAccessPolicy orderAccess;
  private final QuoteAccessPolicy quoteAccess;
  private final UserQueryService users;
  private final PermissionEvaluator permissions;
  private final ProposalStockQueryService measures;
  private final EntityManager entityManager;
  private final ApplicationEventPublisher events;
  private final com.fabricmanagement.sales.orderintake.app.QuantityEvaluationService evaluation;
  private final com.fabricmanagement.production.core.stockunit.api.PieceAllocationPort allocations;
  private final com.fabricmanagement.common.infrastructure.persistence.SalesOrderLineFulfilmentLock
      fulfilment;

  @Override
  public Context resolve(Target target, UUID actor, boolean lock) {
    if (target.type() == TargetType.DRAFT_LINE)
      throw new NotFoundException("An unsaved source preview is not a commercial command target");
    UUID tenant = TenantContext.requireTenantId();
    var identity =
        users
            .findPermissionIdentity(tenant, actor)
            .orElseThrow(() -> new NotFoundException("Cut target not found"));
    var permission =
        permissions.evaluateFresh(tenant, identity.roleCode(), identity.departmentCodes(), actor);
    boolean productionRead = permission.can("production", "read");
    boolean productionWrite = permission.can("production", "write");
    if (target.type() == TargetType.SALES_ORDER) {
      var order =
          orders
              .findByTenantIdAndId(tenant, target.documentId())
              .orElseThrow(() -> new NotFoundException("Cut target not found"));
      if (lock) entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
      if (lock) fulfilment.lock(tenant, target.lineId());
      boolean read =
          orderAccess
              .readReach(tenant, actor, SalesOrderAccessPolicy.PermissionFreshness.FRESH)
              .permits(order.getTenantId(), order.getCreatedBy());
      boolean operate =
          productionWrite
              && productionScope(
                  permission.scopeOf("production", "write"), actor, identity, order.getCreatedBy());
      if (!read
          && !(productionRead
              && productionScope(
                  permission.scopeOf("production", "read"), actor, identity, order.getCreatedBy()))
          && !operate) throw new NotFoundException("Cut target not found");
      var line =
          lines
              .findByTenantIdAndId(tenant, target.lineId())
              .filter(value -> value.getSalesOrderId().equals(order.getId()))
              .orElseThrow(() -> new NotFoundException("Cut target line not found"));
      boolean exact = line.getQuantityPolicy() == SalesQuantityPolicy.EXACT;
      boolean active =
          Boolean.TRUE.equals(order.getIsActive())
              && Boolean.TRUE.equals(line.getIsActive())
              && !order.getStatus().isTerminal();
      // Source planning does not edit the approved commercial version or its exact quantity.
      boolean released =
          order.getStatus() == OrderStatus.CONFIRMED
              || order.getStatus() == OrderStatus.IN_PROGRESS;
      boolean edit = order.getStatus() == OrderStatus.DRAFT || released;
      boolean write =
          orderAccess.canWrite(
              tenant, actor, order, SalesOrderAccessPolicy.PermissionFreshness.FRESH);
      return new Context(
          target,
          line.getProductId(),
          metres(line.getRequestedQty(), line.getUnit()),
          StockSelectionTerms.fingerprint(order, line),
          active,
          active && exact && edit,
          active && released,
          write || operate,
          operate,
          AppRoutes.salesOrder(order.getId()),
          order.getOrderNumber(),
          order.getVersion());
    }
    var quote =
        quotes
            .findByTenantIdAndIdAndIsActiveTrue(tenant, target.documentId())
            .orElseThrow(() -> new NotFoundException("Cut target not found"));
    if (lock) entityManager.refresh(quote, LockModeType.PESSIMISTIC_WRITE);
    boolean operate =
        productionWrite
            && productionScope(
                permission.scopeOf("production", "write"), actor, identity, quote.getCreatedBy());
    if (!quoteAccess.canRead(tenant, actor, quote, PermissionFreshness.FRESH)
        && !(productionRead
            && productionScope(
                permission.scopeOf("production", "read"), actor, identity, quote.getCreatedBy()))
        && !operate) throw new NotFoundException("Cut target not found");
    var line =
        quote
            .findLine(target.lineId())
            .orElseThrow(() -> new NotFoundException("Cut target line not found"));
    boolean active =
        quote.isActive()
            && line.isActive()
            && !java.util.Set.of(
                    QuoteStatus.REJECTED,
                    QuoteStatus.EXPIRED,
                    QuoteStatus.SUPERSEDED,
                    QuoteStatus.CONVERTED)
                .contains(quote.getStatus());
    boolean exact = line.getQuantityPolicy() == SalesQuantityPolicy.EXACT;
    boolean editable =
        quote.getStatus() == QuoteStatus.DRAFT || quote.getStatus() == QuoteStatus.EVALUATION;
    String fingerprint =
        String.join(
            "|",
            Objects.toString(quote.getCustomerId()),
            Objects.toString(line.getProductId()),
            Objects.toString(line.getColorId()),
            decimal(line.getFinishedWidth()),
            Objects.toString(line.getFinishedWidthUnit(), ""),
            line.getUnit(),
            decimal(line.getRequestedQty()),
            decimal(line.getCustomerRequestQty()),
            Boolean.toString(line.isSingleLotRequired()),
            line.getQuantityPolicy().name());
    return new Context(
        target,
        line.getProductId(),
        metres(line.getRequestedQty(), line.getUnit()),
        fingerprint,
        active,
        active && exact && editable,
        false,
        quoteAccess.canWrite(tenant, actor, quote, PermissionFreshness.FRESH) || operate,
        operate,
        AppRoutes.salesQuote(quote.getId()),
        quote.getQuoteNumber(),
        quote.getVersion());
  }

  private BigDecimal metres(BigDecimal quantity, String unit) {
    return measures.toCanonical(quantity, unit, PrimaryMeasure.LENGTH).orElse(BigDecimal.ZERO);
  }

  private String decimal(BigDecimal value) {
    return value == null ? "" : value.stripTrailingZeros().toPlainString();
  }

  private boolean productionScope(
      com.fabricmanagement.platform.user.domain.DataScope scope,
      UUID actor,
      UserQueryService.PermissionIdentity identity,
      UUID creator) {
    if (scope == null) return false;
    return switch (scope) {
      case GLOBAL, ORGANIZATION -> true;
      case OWN -> actor.equals(creator);
      case DEPARTMENT ->
          actor.equals(creator)
              || users
                  .findActiveUserIdsByDepartmentCodes(
                      TenantContext.requireTenantId(),
                      java.util.Set.copyOf(identity.departmentCodes()))
                  .contains(creator);
    };
  }

  @Override
  public boolean sourcesCompatible(Target target, java.util.List<UUID> sourceIds) {
    var stock = sourceStock(target);
    boolean singleLot;
    if (target.type() == TargetType.SALES_ORDER)
      singleLot =
          lines
              .findByTenantIdAndId(TenantContext.requireTenantId(), target.lineId())
              .orElseThrow()
              .isSingleLotRequired();
    else
      singleLot =
          quotes
              .findByTenantIdAndIdAndIsActiveTrue(
                  TenantContext.requireTenantId(), target.documentId())
              .orElseThrow()
              .findLine(target.lineId())
              .orElseThrow()
              .isSingleLotRequired();
    var matching =
        stock.lots().stream()
            .filter(lot -> lot.widthEvidence() != ProposalStockQueryService.WidthEvidence.UNKNOWN)
            .filter(
                lot ->
                    lot.pieces().stream()
                        .anyMatch(piece -> sourceIds.contains(piece.stockUnitId())))
            .toList();
    var ids =
        matching.stream()
            .flatMap(lot -> lot.pieces().stream())
            .filter(
                piece ->
                    piece.state() == ProposalStockQueryService.PieceState.ELIGIBLE
                        || "ALLOCATED".equals(piece.reason()))
            .map(ProposalStockQueryService.ProposalPiece::stockUnitId)
            .collect(java.util.stream.Collectors.toSet());
    var lots =
        matching.stream()
            .map(ProposalStockQueryService.ProposalLot::batchId)
            .collect(java.util.stream.Collectors.toSet());
    return ids.containsAll(sourceIds) && (!singleLot || lots.size() == 1);
  }

  @Override
  public void assertFreeSources(Target target, java.util.List<UUID> ids, UUID session) {
    var request = evaluationRequest(target, ids, session);
    var owned = owned(target);
    var run = evaluation.runForCut(request, owned);
    if (run.evaluation().result().options().stream()
        .noneMatch(
            option ->
                option.applicable()
                    && java.util.Set.copyOf(option.pieceIds()).equals(java.util.Set.copyOf(ids))))
      throw com.fabricmanagement.sales.common.exception.OrderIntakeException.conflict(
          "CUT_PLAN_SOURCE_CHANGED",
          "The source selection exceeds free stock or no longer meets the line conditions");
  }

  @Override
  public java.util.Map<UUID, BigDecimal> sourceBudgets(Target target, UUID session) {
    return evaluation.freeCapacitiesForCut(
        evaluationRequest(target, java.util.List.of(), session), owned(target));
  }

  private java.util.Set<UUID> owned(Target target) {
    return allocations.activeForLine(TenantContext.requireTenantId(), target.lineId()).stream()
        .map(
            com.fabricmanagement.production.core.stockunit.api.PieceAllocationPort
                    .PieceAllocationView
                ::stockUnitId)
        .collect(java.util.stream.Collectors.toSet());
  }

  private com.fabricmanagement.sales.orderintake.app.QuantityEvaluationService.EvaluationRequest
      evaluationRequest(Target target, java.util.List<UUID> ids, UUID session) {
    com.fabricmanagement.sales.orderintake.app.QuantityEvaluationService.EvaluationRequest request;
    var tenant = TenantContext.requireTenantId();
    if (target.type() == TargetType.SALES_ORDER) {
      var order = orders.findByTenantIdAndId(tenant, target.documentId()).orElseThrow();
      var line = lines.findByTenantIdAndId(tenant, target.lineId()).orElseThrow();
      request =
          new com.fabricmanagement.sales.orderintake.app.QuantityEvaluationService
              .EvaluationRequest(
              line.getId(),
              line.getProductId(),
              line.getColorId(),
              line.getFinishedWidth(),
              line.getFinishedWidthUnit(),
              order.getTradingPartnerId(),
              line.getRequestedQty(),
              line.getUnit(),
              line.isSingleLotRequired(),
              line.getToleranceUpPct(),
              line.getToleranceDownPct(),
              java.util.List.of(),
              ids,
              session);
    } else {
      var quote =
          quotes.findByTenantIdAndIdAndIsActiveTrue(tenant, target.documentId()).orElseThrow();
      var line = quote.findLine(target.lineId()).orElseThrow();
      request =
          new com.fabricmanagement.sales.orderintake.app.QuantityEvaluationService
              .EvaluationRequest(
              line.getId(),
              line.getProductId(),
              line.getColorId(),
              line.getFinishedWidth(),
              line.getFinishedWidthUnit(),
              quote.getCustomerId(),
              line.getRequestedQty(),
              line.getUnit(),
              line.isSingleLotRequired(),
              line.getToleranceUpPct(),
              line.getToleranceDownPct(),
              java.util.List.of(),
              ids,
              session);
    }
    return request;
  }

  @Override
  public java.util.List<UUID> candidateIds(Target target) {
    return sourceStock(target).lots().stream()
        .filter(lot -> lot.widthEvidence() != ProposalStockQueryService.WidthEvidence.UNKNOWN)
        .flatMap(lot -> lot.pieces().stream())
        .filter(
            piece ->
                piece.state() == ProposalStockQueryService.PieceState.ELIGIBLE
                    || "ALLOCATED".equals(piece.reason()))
        .map(ProposalStockQueryService.ProposalPiece::stockUnitId)
        .toList();
  }

  private ProposalStockQueryService.ProposalStock sourceStock(Target target) {
    var tenant = TenantContext.requireTenantId();
    ProposalStockQueryService.ProposalStockQuery query;
    if (target.type() == TargetType.SALES_ORDER) {
      var order = orders.findByTenantIdAndId(tenant, target.documentId()).orElseThrow();
      var line = lines.findByTenantIdAndId(tenant, target.lineId()).orElseThrow();
      query =
          new ProposalStockQueryService.ProposalStockQuery(
              tenant,
              line.getProductId(),
              line.getColorId(),
              line.getFinishedWidth(),
              line.getFinishedWidthUnit(),
              order.getTradingPartnerId());
    } else {
      var quote =
          quotes.findByTenantIdAndIdAndIsActiveTrue(tenant, target.documentId()).orElseThrow();
      var line = quote.findLine(target.lineId()).orElseThrow();
      query =
          new ProposalStockQueryService.ProposalStockQuery(
              tenant,
              line.getProductId(),
              line.getColorId(),
              line.getFinishedWidth(),
              line.getFinishedWidthUnit(),
              quote.getCustomerId());
    }
    return measures.find(query);
  }

  @Override
  public void changed(Context context) {
    events.publishEvent(
        new SalesStockPlanningChangedEvent(
            TenantContext.requireTenantId(),
            context.target().documentId(),
            context.target().type() == TargetType.QUOTE));
  }
}
