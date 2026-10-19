package com.fabricmanagement.sales.orderintake.domain.proposal;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Feasibility proof, independent of customer acceptance and display-unit rounding. */
@Schema(name = "StockProposalCoverage")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record QuantityCoverage(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Status status,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "Key of an applicable free option reaching the request; may be manual")
        String coveringOptionKey,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Reason> reasons) {
  public QuantityCoverage {
    reasons = reasons == null ? List.of() : List.copyOf(reasons);
  }

  public static QuantityCoverage unknown() {
    return new QuantityCoverage(Status.UNKNOWN, null, List.of(Reason.EVIDENCE_INCOMPLETE));
  }

  @Schema(name = "StockProposalCoverageStatus", enumAsRef = true)
  public enum Status {
    COVERABLE,
    BELOW_REQUEST_ONLY,
    NO_APPLICABLE_OPTION,
    UNKNOWN
  }

  @Schema(name = "StockProposalCoverageReason", enumAsRef = true)
  public enum Reason {
    SINGLE_LOT_REQUIRED,
    HEALTHY_REMAINDER_REQUIRED,
    MEASUREMENT_OR_ELIGIBILITY_UNKNOWN,
    UNSUPPORTED_PACKAGE,
    SEARCH_LIMIT,
    HELD_STOCK_ONLY,
    HOLD_EVIDENCE_UNKNOWN,
    EVIDENCE_INCOMPLETE,
    QUANTITY_RESOLUTION_UNSUPPORTED
  }
}
