package com.fabricmanagement.sales.orderintake.domain.proposal;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A fact explaining an option's place in the tenant-first ranking (STOCK-PROPOSAL-2). Reasons are
 * listed in criterion order: free stock, no single piece left, lot closure, older stock, fewer
 * lots, closeness. The client words them; it never derives them.
 *
 * @param quantity the quantity the reason speaks of, in the line unit (a remnant, a difference)
 * @param count how many lots the reason speaks of
 * @param since the oldest production date the option uses; only for {@link Code#OLDER_STOCK}
 */
public record ProposalReason(Code code, BigDecimal quantity, Integer count, Instant since) {

  public static ProposalReason of(Code code) {
    return new ProposalReason(code, null, null, null);
  }

  @Schema(name = "StockProposalReasonCode", enumAsRef = true)
  public enum Code {
    /** Free stock has no option within the agreed tolerance, so held stock is shown. */
    FREE_STOCK_INSUFFICIENT,
    /** Other quotes hold stock of this line; this option takes none of it. */
    FREE_STOCK_ONLY,
    /** A closer option would leave a single piece in a lot; this one does not. */
    NO_SINGLE_PIECE_LEFT,
    /** A single piece of {@code quantity} stays in a lot. */
    LEAVES_SINGLE_PIECE,
    /** Pieces of unknown state decide whether a single piece stays. */
    SINGLE_PIECE_MAY_REMAIN,
    /** {@code count} lots are taken whole. */
    CLOSES_LOTS,
    /** The oldest stock of the line, produced {@code since}; every lot has a known date. */
    OLDER_STOCK,
    /** The fewest lots among the options ({@code count}). */
    FEWEST_LOTS,
    /** Exactly the requested quantity. */
    EXACT_REQUEST,
    /** The closest quantity to the request among the options. */
    CLOSEST_TO_REQUEST
  }
}
