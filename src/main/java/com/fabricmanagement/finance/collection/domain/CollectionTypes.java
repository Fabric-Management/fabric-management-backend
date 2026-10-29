package com.fabricmanagement.finance.collection.domain;

import com.fabricmanagement.finance.collection.domain.exception.CollectionRuleException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

public final class CollectionTypes {
  private CollectionTypes() {}

  @Schema(name = "FinanceCollectionKind", enumAsRef = true)
  public enum Kind {
    DOWN_PAYMENT,
    INSTALMENT
  }

  @Schema(name = "FinanceCollectionMethod", enumAsRef = true)
  public enum Method {
    CASH,
    BANK_TRANSFER,
    CHEQUE,
    CREDIT_CARD,
    OTHER
  }

  @Schema(name = "FinanceCollectionChequeState", enumAsRef = true)
  public enum ChequeState {
    RECEIVED,
    PRESENTED,
    CLEARED,
    DISHONOURED,
    RETURNED,
    CANCELLED;

    public boolean countsAsReceived() {
      return Set.of(RECEIVED, PRESENTED, CLEARED).contains(this);
    }

    public boolean permits(ChequeAction action) {
      return switch (action) {
        case PRESENT -> this == RECEIVED || this == DISHONOURED;
        case CLEAR -> this == RECEIVED || this == PRESENTED;
        case DISHONOUR -> this == PRESENTED || this == CLEARED;
        case RETURN ->
            this == RECEIVED || this == PRESENTED || this == CLEARED || this == DISHONOURED;
        case CANCEL -> this == RECEIVED || this == PRESENTED;
        case CORRECT -> this == RECEIVED || this == PRESENTED;
      };
    }

    /**
     * Dishonouring or returning a cleared instrument cancels the money its clearing recorded, so it
     * is a reversal and takes the reverse grant, not merely the write grant.
     */
    public boolean reversesClearing(ChequeAction action) {
      return this == CLEARED && (action == ChequeAction.DISHONOUR || action == ChequeAction.RETURN);
    }

    public ChequeState next(ChequeAction action) {
      if (!permits(action))
        throw CollectionRuleException.conflict(
            "CHEQUE_TRANSITION_NOT_ALLOWED",
            "The cheque cannot perform this action in its current state");
      return switch (action) {
        case PRESENT -> PRESENTED;
        case CLEAR -> CLEARED;
        case DISHONOUR -> DISHONOURED;
        case RETURN -> RETURNED;
        case CANCEL -> CANCELLED;
        case CORRECT -> this;
      };
    }
  }

  @Schema(name = "FinanceCollectionChequeAction", enumAsRef = true)
  public enum ChequeAction {
    PRESENT,
    CLEAR,
    DISHONOUR,
    RETURN,
    CANCEL,
    CORRECT
  }

  @Schema(name = "FinanceCollectionAction", enumAsRef = true)
  public enum Action {
    MATCH_PAYMENT,
    RECORD_RECEIPT,
    RECEIVE_CHEQUE,
    REVERSE_MATCH,
    RESOLVE_OVER_SETTLEMENT,
    /** Deciding that a surplus is owed back to the customer; it needs the reverse grant. */
    REFUND_OVER_SETTLEMENT
  }

  /** What finance decides the cash above the agreed amount of an obligation is. */
  @Schema(name = "FinanceCollectionOverSettlementDecision", enumAsRef = true)
  public enum OverSettlementDecision {
    /** Move it to another instalment of the same order that still has an amount to collect. */
    REAPPLY,
    /** Keep it as the customer's unmatched money on account; nothing is owed back. */
    ADVANCE,
    /** Owe it back: opens a refund that an outbound payment later settles. */
    REFUND
  }

  @Schema(name = "FinanceCollectionRefundAction", enumAsRef = true)
  public enum RefundAction {
    /** An outbound payment pays the refund. */
    SETTLE,
    /** The settling payment was voided or reversed, so the refund is owed again. */
    REOPEN,
    /** The money is not owed back after all; it stays on account. */
    CANCEL
  }

  @Schema(name = "FinanceCollectionRefundState", enumAsRef = true)
  public enum RefundState {
    OPEN,
    SETTLED,
    CANCELLED;

    public boolean permits(RefundAction action) {
      return switch (action) {
        case SETTLE, CANCEL -> this == OPEN;
        case REOPEN -> this == SETTLED;
      };
    }

    public RefundState next(RefundAction action) {
      if (!permits(action))
        throw CollectionRuleException.conflict(
            "REFUND_TRANSITION_NOT_ALLOWED", "The refund cannot do this in its current state");
      return switch (action) {
        case SETTLE -> SETTLED;
        case REOPEN -> OPEN;
        case CANCEL -> CANCELLED;
      };
    }
  }
}
