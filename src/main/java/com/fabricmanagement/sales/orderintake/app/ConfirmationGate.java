package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.stockunit.api.PieceAllocationPort;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.AcceptanceTerms;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptance;
import com.fabricmanagement.sales.orderintake.domain.QuantityAcceptanceStatus;
import com.fabricmanagement.sales.orderintake.infra.repository.QuantityAcceptanceRepository;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The order-intake conditions of confirming a sales order (SOI D4, A05, A10, R18, R22). Checking
 * never changes anything; allocating re-checks inside the confirmation transaction and holds the
 * accepted pieces, or refuses the whole confirmation.
 */
@Component
@RequiredArgsConstructor
public class ConfirmationGate {

  private final QuantityAcceptanceRepository acceptances;
  private final QuantityEvaluationService evaluation;
  private final PieceAllocationPort allocation;
  private final CustomRequestGate customRequests;

  /** A reason a line (or, with a null line, the order) cannot be confirmed now. */
  public record Block(UUID lineId, String code, String message, List<UUID> pieceIds) {}

  record LinePlan(
      SalesOrderLine line, QuantityAcceptance acceptance, AcceptedStockCheck.Result check) {}

  public List<Block> blocks(SalesOrder order, List<SalesOrderLine> lines) {
    List<Block> blocks = new ArrayList<>(customRequests.blocks(order, lines));
    plan(order, lines, blocks);
    return List.copyOf(blocks);
  }

  /**
   * Re-checks and holds the accepted pieces of every line. Throws with the first block; the
   * surrounding confirmation transaction then rolls back and nothing is held.
   */
  public void allocate(SalesOrder order, List<SalesOrderLine> lines, UUID actor) {
    List<Block> blocks = new ArrayList<>(customRequests.blocks(order, lines));
    List<LinePlan> plans = plan(order, lines, blocks);
    if (!blocks.isEmpty()) {
      throw toException(blocks.getFirst());
    }
    UUID tenantId = TenantContext.requireTenantId();
    // One sorted lock pass over every lot of the order (IK-25); per-line calls then re-lock held
    // rows.
    allocation.lockLots(
        tenantId,
        plans.stream()
            .flatMap(plan -> plan.check().pieceRefs().stream())
            .map(PieceAllocationPort.PieceRef::batchId)
            .collect(java.util.stream.Collectors.toSet()));
    for (LinePlan plan : plans) {
      PieceAllocationPort.AllocationOutcome outcome =
          allocation.allocate(
              new PieceAllocationPort.AllocationRequest(
                  tenantId, order.getId(), plan.line().getId(), actor, plan.check().pieceRefs()));
      if (!outcome.allocated()) {
        throw toException(
            new Block(
                plan.line().getId(),
                "STOCK_JUST_TAKEN",
                "Pieces were taken or changed while confirming (" + outcome.failureCode() + ")",
                outcome.unavailablePieceIds()));
      }
    }
  }

  public void release(Collection<UUID> lineIds, UUID actor, String reason) {
    UUID tenantId = TenantContext.requireTenantId();
    lineIds.forEach(lineId -> allocation.releaseForLine(tenantId, lineId, actor, reason));
  }

  private List<LinePlan> plan(SalesOrder order, List<SalesOrderLine> lines, List<Block> blocks) {
    if (lines.isEmpty()) {
      return List.of();
    }
    UUID tenantId = TenantContext.requireTenantId();
    Map<UUID, QuantityAcceptance> active =
        acceptances
            .findByTenantIdAndSalesOrderLineIdInAndStatus(
                tenantId,
                lines.stream().map(SalesOrderLine::getId).toList(),
                QuantityAcceptanceStatus.ACTIVE)
            .stream()
            .collect(
                Collectors.toMap(QuantityAcceptance::getSalesOrderLineId, Function.identity()));
    List<LinePlan> plans = new ArrayList<>();
    for (SalesOrderLine line : lines) {
      QuantityAcceptance acceptance = active.get(line.getId());
      if (acceptance == null) {
        evaluation
            .currentStock(order, line)
            .ifPresent(
                stock -> {
                  boolean available =
                      stock.lots().stream()
                          .flatMap(lot -> lot.pieces().stream())
                          .anyMatch(
                              piece ->
                                  piece.state()
                                      == com.fabricmanagement.production.core.batch.api.query
                                          .ProposalStockQueryService.PieceState.ELIGIBLE);
                  if (available) {
                    blocks.add(
                        new Block(
                            line.getId(),
                            "STOCK_CHOICE_MISSING",
                            "Available finished stock needs an explicit stock choice before confirmation",
                            List.of()));
                  }
                });
        continue;
      }
      if (!acceptance.covers(AcceptanceTerms.fingerprint(line))) {
        blocks.add(
            new Block(
                line.getId(),
                "ACCEPTANCE_STALE",
                "The line changed after the customer's acceptance; record it again",
                List.of()));
        continue;
      }
      Optional<QuantityEvaluationService.LineStock> stock = evaluation.currentStock(order, line);
      if (stock.isEmpty()) {
        blocks.add(
            new Block(
                line.getId(), "EVIDENCE_UNAVAILABLE", "Current stock cannot be read", List.of()));
        continue;
      }
      AcceptedStockCheck.Result check =
          AcceptedStockCheck.check(stock.get(), acceptance.getPieceIds(), acceptance.getBatchIds());
      if (!check.piecesAvailable()
          || check.canonicalQuantity().compareTo(acceptance.getCanonicalQty()) != 0) {
        blocks.add(
            new Block(
                line.getId(),
                "STOCK_CHANGED",
                "Accepted pieces are no longer available; evaluate the line again",
                check.unavailablePieces()));
        continue;
      }
      if (!check.compatibilityResolved()) {
        blocks.add(
            new Block(
                line.getId(),
                "COMPATIBILITY_REQUIRED",
                "Several lots need a technical compatibility confirmation or the customer's"
                    + " acceptance of the concrete tone difference",
                List.of()));
        continue;
      }
      plans.add(new LinePlan(line, acceptance, check));
    }
    return plans;
  }

  public static OrderIntakeException toException(Block block) {
    String where = block.lineId() == null ? "" : " (line " + block.lineId() + ")";
    return OrderIntakeException.conflict(block.code(), block.message() + where);
  }
}
