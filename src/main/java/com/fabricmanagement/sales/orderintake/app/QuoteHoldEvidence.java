package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.production.core.batch.api.query.QuoteHoldQueryService.HeldPieceView;
import com.fabricmanagement.production.core.batch.api.query.QuoteHoldQueryService.LotIntentView;
import com.fabricmanagement.production.core.batch.api.query.QuoteHoldQueryService.QuoteHolds;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * States what quotes hold in the evaluator's terms (STOCK-PREVIEW-1 A2, STOCK-PROPOSAL-2). A piece
 * hold is verified only when its quote line has an active intent on the same lot; otherwise it is
 * an unverified record that names no quote. A lot's intent total is null when any intent cannot be
 * stated in the canonical unit.
 */
final class QuoteHoldEvidence {

  private QuoteHoldEvidence() {}

  static QuantityEvaluator.StockHolds of(QuoteHolds holds) {
    Map<UUID, List<LotIntentView>> intentsByLot =
        holds.intents().stream().collect(Collectors.groupingBy(LotIntentView::batchId));
    Map<UUID, QuantityEvaluator.Holder> pieceHolds = new LinkedHashMap<>();
    for (HeldPieceView hold : holds.heldPieces()) {
      Optional<LotIntentView> backing =
          intentsByLot.getOrDefault(hold.batchId(), List.of()).stream()
              .filter(intent -> Objects.equals(intent.quoteLineId(), hold.quoteLineId()))
              .findFirst();
      QuantityEvaluator.Holder holder =
          backing.map(QuoteHoldEvidence::holder).orElseGet(QuantityEvaluator.Holder::unverified);
      if (hold.provisionalSessionId() != null && holder.verified())
        holder =
            new QuantityEvaluator.Holder(
                null,
                hold.quoteLineId(),
                null,
                null,
                null,
                true,
                com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.StockOwnerType
                    .DRAFT);
      // Conflicting owners invalidate the evidence; duplicate rows for the same owner do not.
      pieceHolds.merge(
          hold.stockUnitId(),
          holder,
          (left, right) -> {
            if (left.equals(right)) return left;
            if (left.verified()
                && right.verified()
                && left.quoteLineId() != null
                && left.quoteLineId().equals(right.quoteLineId()))
              return left.ownerType()
                      == com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption
                          .StockOwnerType.DRAFT
                  ? right
                  : left;
            return QuantityEvaluator.Holder.unverified();
          });
    }
    Map<UUID, QuantityEvaluator.LotIntents> lotIntents = new LinkedHashMap<>();
    intentsByLot.forEach(
        (lot, intents) -> {
          boolean convertible = intents.stream().allMatch(i -> i.canonicalQuantity() != null);
          BigDecimal total =
              convertible
                  ? intents.stream()
                      .map(LotIntentView::canonicalQuantity)
                      .reduce(BigDecimal.ZERO, BigDecimal::add)
                  : null;
          lotIntents.put(
              lot,
              new QuantityEvaluator.LotIntents(
                  intents.stream()
                      .map(
                          intent ->
                              new QuantityEvaluator.Intent(
                                  holder(intent),
                                  intent.quantity(),
                                  intent.unit(),
                                  intent.canonicalQuantity()))
                      .toList(),
                  total));
        });
    return new QuantityEvaluator.StockHolds(pieceHolds, lotIntents);
  }

  private static QuantityEvaluator.Holder holder(LotIntentView intent) {
    return new QuantityEvaluator.Holder(
        intent.quoteId(),
        intent.quoteLineId(),
        intent.quoteNumber(),
        intent.marketerName(),
        intent.expiresAt(),
        true,
        com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption.StockOwnerType
            .valueOf(intent.ownerType()));
  }
}
