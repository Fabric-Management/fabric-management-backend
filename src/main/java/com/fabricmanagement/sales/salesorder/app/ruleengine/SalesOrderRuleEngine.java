package com.fabricmanagement.sales.salesorder.app.ruleengine;

import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.domain.port.DraftProductionOrderCommand;
import com.fabricmanagement.sales.salesorder.domain.port.ProductionOrderPort;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule-based engine that processes each SalesOrderLine after SalesOrder confirmation.
 *
 * <h2>4-Step Recipe Matching Cascade</h2>
 *
 * <ol>
 *   <li><b>Step 1 — Material default recipe:</b> If line has a materialId and there is an active
 *       ACTIVE recipe associated to it (queried by materialId), use it.
 *   <li><b>Step 2 — Customer history:</b> Query for the most recently used recipe for this
 *       (customer, material) combination from previous orders.
 *   <li><b>Step 3 — Constraint filter:</b> Find ACTIVE recipes for the material and pick the most
 *       frequently used (by WorkOrder count).
 *   <li><b>Step 4 — Fallback:</b> No match found — WorkOrder created with no recipe; FlowBoard task
 *       will be generated for manual recipe assignment.
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SalesOrderRuleEngine {

  private final SalesOrderLineRepository lineRepository;
  private final ProductionOrderPort productionOrderPort;
  private final WorkOrderRecipeHistoryQuery historyQuery;

  /**
   * Processes all PENDING lines of a confirmed SalesOrder: runs recipe matching and creates a
   * WorkOrder (DRAFT) per line.
   */
  @Transactional
  public void processConfirmedOrder(SalesOrder order) {
    List<SalesOrderLine> pendingLines =
        lineRepository.findBySalesOrderIdAndLineStatusAndIsActiveTrue(
            order.getId(), SalesOrderLineStatus.PENDING);

    if (pendingLines.isEmpty()) {
      log.info("RuleEngine: no PENDING lines for SalesOrder {}", order.getOrderNumber());
      return;
    }

    for (SalesOrderLine line : pendingLines) {
      processLine(order, line);
    }
  }

  // ── Private Processing ────────────────────────────────────────────────────

  private void processLine(SalesOrder order, SalesOrderLine line) {
    Optional<UUID> recipeId = findRecipe(order, line);

    if (recipeId.isPresent()) {
      line.assignRecipe(recipeId.get());
      lineRepository.save(line);
      log.info(
          "RuleEngine: recipe {} assigned to SalesOrderLine {} (order {})",
          recipeId.get(),
          line.getId(),
          order.getOrderNumber());
    } else {
      log.warn(
          "RuleEngine: no recipe found for SalesOrderLine {} (material={}). "
              + "WorkOrder will be created without recipe — FlowBoard task pending.",
          line.getId(),
          line.getMaterialId());
    }

    // Create WorkOrder (DRAFT) regardless of recipe availability
    createDraftWorkOrder(order, line, recipeId.orElse(null));
  }

  /** 4-step cascade recipe matching. */
  private Optional<UUID> findRecipe(SalesOrder order, SalesOrderLine line) {
    if (line.getMaterialId() == null) {
      // No materialId — skip steps 1-3, go to fallback
      return Optional.empty();
    }

    // Step 1 — material default recipe (most recent ACTIVE recipe for this material)
    Optional<UUID> step1 = historyQuery.findDefaultRecipeForMaterial(line.getMaterialId());
    if (step1.isPresent()) {
      log.debug(
          "RuleEngine step1 match: material={} → recipe={}", line.getMaterialId(), step1.get());
      return step1;
    }

    // Step 2 — customer history (same customer + material combination)
    Optional<UUID> step2 =
        historyQuery.findMostRecentRecipeForCustomerAndMaterial(
            order.getTradingPartnerId(), line.getMaterialId());
    if (step2.isPresent()) {
      log.debug("RuleEngine step2 match (customer history): → recipe={}", step2.get());
      return step2;
    }

    // Step 3 — most frequently used ACTIVE recipe for this material
    Optional<UUID> step3 = historyQuery.findMostUsedRecipeForMaterial(line.getMaterialId());
    if (step3.isPresent()) {
      log.debug("RuleEngine step3 match (frequency): → recipe={}", step3.get());
      return step3;
    }

    // Step 4 — fallback: no match
    return Optional.empty();
  }

  /** Creates a DRAFT WorkOrder linked to the SalesOrderLine. */
  private void createDraftWorkOrder(SalesOrder order, SalesOrderLine line, UUID recipeId) {
    DraftProductionOrderCommand cmd =
        new DraftProductionOrderCommand(
            recipeId,
            order.getTradingPartnerId(),
            line.getId(),
            line.getRequestedQty(),
            line.getUnit(),
            line.getCurrency(),
            order.getDeadline());

    productionOrderPort.requestDraftProductionOrder(cmd);
    log.info(
        "RuleEngine: WorkOrder (DRAFT) created for SalesOrderLine {} (recipe={})",
        line.getId(),
        recipeId);
  }
}
