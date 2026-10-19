package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.sales.orderintake.domain.proposal.ProposalReason;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.OptionPlacement;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.StockSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Each option's facts in criterion order (STOCK-PROPOSAL-2): why held stock is shown, the remnant a
 * manual pick leaves, lots closed and the oldest stock. The client words them; it never derives
 * them. Never an age claim without known dates.
 */
final class ProposalReasons {

  private ProposalReasons() {}

  static List<QuantityOption> withReasons(
      EvaluationContext context, boolean limitExceeded, List<QuantityOption> options) {
    boolean anyRemnant = options.stream().anyMatch(QuantityOption::leavesRemnant);
    Instant oldest =
        context.aged()
            ? context.lots().stream()
                .filter(QuantityEvaluator::hasEligible)
                .map(ProposalLot::productionDate)
                .min(Comparator.naturalOrder())
                .orElse(null)
            : null;
    List<QuantityOption> result = new ArrayList<>();
    for (QuantityOption option : options) {
      List<ProposalReason> reasons = new ArrayList<>();
      if (option.source() == StockSource.HELD && option.placement() != OptionPlacement.MANUAL) {
        // Without a complete search the absence of a free option is only what was found.
        reasons.add(
            ProposalReason.of(
                limitExceeded
                        || context.free().values().stream().anyMatch(free -> !free.known())
                        || context.lots().stream()
                            .anyMatch(
                                lot ->
                                    !lot.inventoryComplete()
                                        || lot.pieces().stream()
                                            .anyMatch(
                                                piece ->
                                                    piece.state()
                                                            == com.fabricmanagement.production.core
                                                                .batch.api.query
                                                                .ProposalStockQueryService
                                                                .PieceState.UNKNOWN
                                                        || "UNSUPPORTED_PACKAGE"
                                                            .equals(piece.reason())))
                    ? ProposalReason.Code.FREE_STOCK_NOT_FOUND
                    : ProposalReason.Code.FREE_STOCK_INSUFFICIENT));
      }
      if (option.leavesRemnant()) {
        // Only a manual pick: how many pieces stay behind and how much they hold.
        List<QuantityOption.LotPart> remnantLots =
            option.lots().stream().filter(QuantityOption.LotPart::remnant).toList();
        BigDecimal remnant =
            remnantLots.stream()
                .map(QuantityOption.LotPart::remnantQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int pieces = remnantLots.stream().mapToInt(part -> part.remnantPieceIds().size()).sum();
        reasons.add(new ProposalReason(ProposalReason.Code.LEAVES_REMNANT, remnant, pieces, null));
      } else if (anyRemnant) {
        reasons.add(ProposalReason.of(ProposalReason.Code.NO_REMNANT));
      }
      int closed =
          (int)
              option.lots().stream().filter(part -> Boolean.TRUE.equals(part.closesLot())).count();
      if (closed > 0) {
        reasons.add(new ProposalReason(ProposalReason.Code.CLOSES_LOTS, null, closed, null));
      }
      if (oldest != null
          && option.lots().stream().anyMatch(part -> oldest.equals(part.productionDate()))) {
        reasons.add(new ProposalReason(ProposalReason.Code.OLDER_STOCK, null, null, oldest));
      }
      result.add(option.withReasons(reasons));
    }
    return result;
  }
}
