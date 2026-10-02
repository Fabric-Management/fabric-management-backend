package com.fabricmanagement.sales.orderintake.dto;

import com.fabricmanagement.sales.orderintake.domain.QuantityProposal;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A recorded quantity proposal for one line (SOI D2). Options are whole-piece selections; the
 * request itself is never changed by a proposal.
 */
@Schema(name = "QuantityProposal")
public record QuantityProposalDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID salesOrderLineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        QuantityEvaluationResult.EvaluationStatus status,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal requestedQuantity,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String unit,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String canonicalUnit,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Option> options,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int eligiblePieces,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unknownPieces,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int excludedPieces,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> unknownReasons,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "True when older production dates took precedence on ties")
        boolean agedByProductionDate,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "Options withheld because they exceed the tolerance agreed with the customer")
        int optionsOutsideAgreedTolerance,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String evidenceFingerprint,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant evaluatedAt) {

  public static QuantityProposalDto from(QuantityProposal proposal) {
    QuantityEvaluationResult result = proposal.getResult();
    return new QuantityProposalDto(
        proposal.getId(),
        proposal.getSalesOrderLineId(),
        result.status(),
        result.requestedQuantity(),
        result.unit(),
        result.canonicalUnit(),
        result.options().stream().map(Option::from).toList(),
        result.eligiblePieces(),
        result.unknownPieces(),
        result.excludedPieces(),
        result.unknownReasons(),
        result.agedByProductionDate(),
        result.optionsOutsideAgreedTolerance(),
        proposal.getEvidenceFingerprint(),
        proposal.getEvaluatedAt());
  }

  @Schema(name = "QuantityProposalOption")
  public record Option(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String optionKey,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuantityOption.OptionKind kind,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          QuantityOption.Compatibility compatibility,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal quantity,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal difference,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal differencePercent,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean outsideAgreedTolerance,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean leavesSingleRemnant,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "Pieces of unknown state may leave a single remnant (SOI 3.1)")
          boolean remnantUnknown,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int lotCount,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Lot> lots) {

    static Option from(QuantityOption option) {
      return new Option(
          option.optionKey(),
          option.kind(),
          option.compatibility(),
          option.quantity(),
          option.difference(),
          option.differencePercent(),
          option.outsideAgreedTolerance(),
          option.leavesSingleRemnant(),
          option.remnantUnknown(),
          option.lotCount(),
          option.lots().stream().map(Lot::from).toList());
    }
  }

  @Schema(name = "QuantityProposalLot")
  public record Lot(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID batchId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String lotNo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int pieceCount,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal quantity,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean exhausted,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int remainingEligiblePieces,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unknownPieces,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean singleRemnant,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean remnantUnknown,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          BigDecimal remnantQuantity) {

    static Lot from(QuantityOption.LotPart part) {
      return new Lot(
          part.batchId(),
          part.lotNo(),
          part.pieceIds().size(),
          part.quantity(),
          part.exhausted(),
          part.remainingEligiblePieces(),
          part.unknownPieces(),
          part.singleRemnant(),
          part.remnantUnknown(),
          part.remnantQuantity());
    }
  }
}
