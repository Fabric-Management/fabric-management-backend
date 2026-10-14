package com.fabricmanagement.sales.orderintake.domain.proposal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A fact explaining an option's place in the tenant-first ranking (STOCK-PROPOSAL-2). Reasons are
 * listed in criterion order: free stock, no remnant (one or two pieces) left, lot closure, older
 * stock. The client words them; it never derives them.
 *
 * @param quantity the quantity the reason speaks of, in the line unit (a remnant)
 * @param count how many lots (or, for a remnant, pieces) the reason speaks of
 * @param since the oldest production date the option uses; only for {@link Code#OLDER_STOCK}
 */
public record ProposalReason(Code code, BigDecimal quantity, Integer count, Instant since) {

  public static ProposalReason of(Code code) {
    return new ProposalReason(code, null, null, null);
  }

  @Schema(name = "StockProposalReasonCode", enumAsRef = true)
  public enum Code {
    /**
     * Free stock cannot reach the lower tolerance limit, so held stock is shown. Only from a
     * complete search.
     */
    FREE_STOCK_INSUFFICIENT,
    /**
     * A search limit was reached and no free option reaching the lower tolerance limit was found,
     * so held stock is shown; one may exist.
     */
    FREE_STOCK_NOT_FOUND,
    /** A listed option (a manual pick) leaves one or two pieces in a lot; this one does not. */
    NO_REMNANT,
    /** {@code count} pieces (one or two) of {@code quantity} stay in a lot; a manual pick only. */
    LEAVES_REMNANT,
    /** {@code count} lots are taken whole. */
    CLOSES_LOTS,
    /** The oldest stock of the line, produced {@code since}; every lot has a known date. */
    OLDER_STOCK
  }
}
