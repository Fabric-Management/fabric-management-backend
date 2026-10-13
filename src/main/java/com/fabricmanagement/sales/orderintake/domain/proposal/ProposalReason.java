package com.fabricmanagement.sales.orderintake.domain.proposal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A fact explaining an option's place in the tenant-first ranking (STOCK-PROPOSAL-2). Reasons are
 * listed in criterion order: free stock, no remnant (one or two pieces) left, lot closure, older
 * stock, fewer lots, closeness. The client words them; it never derives them.
 *
 * @param quantity the quantity the reason speaks of, in the line unit (a remnant, a difference)
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
    /** Other quotes hold stock of this line; this option takes none of it. */
    FREE_STOCK_ONLY,
    /** A listed option (a manual pick) leaves one or two pieces in a lot; this one does not. */
    NO_REMNANT,
    /** {@code count} pieces (one or two) of {@code quantity} stay in a lot; a manual pick only. */
    LEAVES_REMNANT,
    /** {@code count} lots are taken whole. */
    CLOSES_LOTS,
    /** The oldest stock of the line, produced {@code since}; every lot has a known date. */
    OLDER_STOCK,
    /** The fewest lots among the options listed ({@code count}), not among every combination. */
    FEWEST_LOTS,
    /** Exactly the requested quantity. */
    EXACT_REQUEST,
    /** The closest quantity to the request among the options; only from a complete search. */
    CLOSEST_TO_REQUEST,
    /** The closest quantity among the options found; a search limit was reached. */
    CLOSEST_FOUND
  }
}
