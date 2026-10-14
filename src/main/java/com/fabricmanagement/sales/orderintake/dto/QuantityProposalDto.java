package com.fabricmanagement.sales.orderintake.dto;

import com.fabricmanagement.sales.orderintake.domain.QuantityProposal;
import com.fabricmanagement.sales.orderintake.domain.proposal.ProposalReason;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A recorded quantity proposal for one line (SOI D2, STOCK-PROPOSAL-2). Options are whole-piece
 * selections in ascending quantity; the request itself is never changed by a proposal.
 */
@Schema(name = "QuantityProposal")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record QuantityProposalDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID salesOrderLineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        QuantityEvaluationResult.EvaluationStatus status,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "The customer's original request the options are judged against")
        BigDecimal requestedQuantity,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String unit,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String canonicalUnit,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "Ascending quantity; same quantity in the tenant-first ranking, then a"
                    + " deterministic order. Keep this order")
        List<Option> options,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description =
                "Index of the main proposal in options; it may sit anywhere in the list. Null"
                    + " when no option can be the main one")
        Integer mainOptionIndex,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "True when no search limit was reached: each option is the best of its kind."
                    + " False: the options are the ones found, never claimed to be the best")
        boolean searchComplete,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int eligiblePieces,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unknownPieces,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int excludedPieces,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> unknownReasons,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Every lot with offerable stock has a known production date")
        boolean agedByProductionDate,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String evidenceFingerprint,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant evaluatedAt) {

  /**
   * The proposal as the viewer may see it: quotes in held stock are named only when {@code
   * readableQuotes} holds them.
   */
  public static QuantityProposalDto from(QuantityProposal proposal, Set<UUID> readableQuotes) {
    QuantityEvaluationResult result = proposal.getResult();
    return new QuantityProposalDto(
        proposal.getId(),
        proposal.getSalesOrderLineId(),
        result.status(),
        result.requestedQuantity(),
        result.unit(),
        result.canonicalUnit(),
        options(result, readableQuotes),
        result.mainOptionIndex(),
        result.searchComplete(),
        result.eligiblePieces(),
        result.unknownPieces(),
        result.excludedPieces(),
        result.unknownReasons(),
        result.agedByProductionDate(),
        proposal.getEvidenceFingerprint(),
        proposal.getEvaluatedAt());
  }

  static List<Option> options(QuantityEvaluationResult result, Set<UUID> readableQuotes) {
    return result.options().stream().map(option -> Option.from(option, readableQuotes)).toList();
  }

  /** One option of the proposal. */
  @Schema(name = "QuantityProposalOption")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Option(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String optionKey,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuantityOption.OptionPlacement placement,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuantityOption.OptionKind kind,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          QuantityOption.Compatibility compatibility,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuantityOption.StockSource source,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal quantity,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Quantity minus the original request, in the line unit")
          BigDecimal difference,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal differencePercent,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "Against the tolerance agreed for the line; both limits are required and 0"
                      + " accepts no deviation that way. Options outside it are shown, never"
                      + " withheld")
          QuantityOption.ToleranceStatus toleranceStatus,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "One or two pieces stay in a used lot. Never for a computed option; a manual"
                      + " pick may, with acknowledgement")
          boolean leavesRemnant,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int lotCount,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "Why the option ranks as it does, in the order of the tenant-first criteria."
                      + " Words come from these codes; none are derived by the client")
          List<Reason> reasons,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Lot> lots,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "What other quotes hold that this option would take")
          List<HeldStock> heldStock,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ApplyCapability apply) {

    public static Option from(QuantityOption option, Set<UUID> readableQuotes) {
      return new Option(
          option.optionKey(),
          option.placement(),
          option.kind(),
          option.compatibility(),
          option.source(),
          option.quantity(),
          option.difference(),
          option.differencePercent(),
          option.toleranceStatus(),
          option.leavesRemnant(),
          option.lotCount(),
          option.reasons().stream().map(Reason::from).toList(),
          option.lots().stream().map(Lot::from).toList(),
          option.heldStock().stream().map(held -> HeldStock.from(held, readableQuotes)).toList(),
          ApplyCapability.of(option));
    }
  }

  /** A ranking fact with the values its sentence needs. */
  @Schema(name = "QuantityProposalReason")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Reason(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProposalReason.Code code,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "A quantity in the line unit: the remnant or the difference")
          BigDecimal quantity,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "A number of lots, or of pieces for LEAVES_REMNANT")
          Integer count,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "The oldest production date; only for OLDER_STOCK")
          Instant since) {

    static Reason from(ProposalReason reason) {
      return new Reason(reason.code(), reason.quantity(), reason.count(), reason.since());
    }
  }

  /** The part of one lot an option takes. */
  @Schema(name = "QuantityProposalLot")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Lot(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID batchId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lotNo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant productionDate,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> pieceIds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pieceCount,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal quantity,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "Every piece of the lot is taken. Never claimed while held, unknown or otherwise"
                      + " unsuitable pieces stay")
          boolean closesLot,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Eligible pieces the option leaves in the lot, held or free")
          int remainingPieces,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal remainingQuantity,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean exhausted,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unknownPieces,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "One or two pieces of the pool stay in the lot (a manual pick only)")
          boolean remnant,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "What those pieces hold, in the line unit")
          BigDecimal remnantQuantity) {

    static Lot from(QuantityOption.LotPart part) {
      return new Lot(
          part.batchId(),
          part.lotNo(),
          part.productionDate(),
          part.pieceIds(),
          part.pieceIds().size(),
          part.quantity(),
          part.closesLot(),
          part.remainingEligiblePieces(),
          part.remainingQuantity(),
          part.exhausted(),
          part.unknownPieces(),
          part.remnant(),
          part.remnantQuantity());
    }
  }

  /**
   * Stock another quote holds that the option would take. A verified hold names its quote and
   * marketer only when the viewer may read that quote; an unverified hold record never names one.
   */
  @Schema(name = "QuantityProposalHeldStock")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record HeldStock(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID batchId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lotNo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuantityOption.HeldStockKind kind,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean verified,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String quoteNumber,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String marketerName,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) LocalDate expiresAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> pieceIds,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "A lot intent's quantity in its own unit; null for a piece hold")
          BigDecimal quantity,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String unit) {

    static HeldStock from(QuantityOption.HeldStock held, Set<UUID> readableQuotes) {
      boolean named = held.verified() && readableQuotes.contains(held.quoteId());
      return new HeldStock(
          held.batchId(),
          held.lotNo(),
          held.kind(),
          held.verified(),
          named ? held.quoteNumber() : null,
          named ? held.marketerName() : null,
          held.verified() ? held.expiresAt() : null,
          held.pieceIds(),
          held.quantity(),
          held.unit());
    }
  }

  /** The action an option offers. */
  @Schema(name = "StockProposalAction", enumAsRef = true)
  public enum Action {
    APPLY_STOCK_PROPOSAL
  }

  /**
   * Whether the option can be applied ({@code action, allowed, reason, routesTo, needsApproval}).
   * Clients show it and never derive it; the apply command checks again.
   */
  @Schema(name = "StockProposalApplyCapability")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record ApplyCapability(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Action action,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowed,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description = "Why it cannot be applied; null when allowed")
          QuantityOption.BlockedReason reason,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              description =
                  "The flow that would unblock it. Null: no release or transfer of another quote's"
                      + " hold exists yet, so none is offered")
          String routesTo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean needsApproval,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "Applying asks the salesperson's single confirmation: outside the tolerance"
                      + " and/or a single piece left. A stock choice, never the customer's"
                      + " acceptance")
          boolean needsConfirmation) {

    static ApplyCapability of(QuantityOption option) {
      return new ApplyCapability(
          Action.APPLY_STOCK_PROPOSAL,
          option.applicable(),
          option.blockedReason(),
          null,
          false,
          option.outsideAgreedTolerance() || option.needsRemnantAcknowledgement());
    }
  }
}
