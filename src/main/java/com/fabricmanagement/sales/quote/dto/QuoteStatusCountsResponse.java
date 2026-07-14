package com.fabricmanagement.sales.quote.dto;

import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(
    name = "QuoteStatusCountsResponse",
    description = "Active quote totals by status for the current tenant")
public record QuoteStatusCountsResponse(
    @Schema(description = "DRAFT quote count", requiredMode = Schema.RequiredMode.REQUIRED)
        long draft,
    @Schema(description = "EVALUATION quote count", requiredMode = Schema.RequiredMode.REQUIRED)
        long evaluation,
    @Schema(
            description = "PENDING_APPROVAL quote count",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long pendingApproval,
    @Schema(description = "APPROVED quote count", requiredMode = Schema.RequiredMode.REQUIRED)
        long approved,
    @Schema(description = "REJECTED quote count", requiredMode = Schema.RequiredMode.REQUIRED)
        long rejected,
    @Schema(description = "EXPIRED quote count", requiredMode = Schema.RequiredMode.REQUIRED)
        long expired,
    @Schema(description = "CONVERTED quote count", requiredMode = Schema.RequiredMode.REQUIRED)
        long converted,
    @Schema(description = "SUPERSEDED quote count", requiredMode = Schema.RequiredMode.REQUIRED)
        long superseded) {

  public static QuoteStatusCountsResponse from(Map<QuoteStatus, Long> counts) {
    return new QuoteStatusCountsResponse(
        count(counts, QuoteStatus.DRAFT),
        count(counts, QuoteStatus.EVALUATION),
        count(counts, QuoteStatus.PENDING_APPROVAL),
        count(counts, QuoteStatus.APPROVED),
        count(counts, QuoteStatus.REJECTED),
        count(counts, QuoteStatus.EXPIRED),
        count(counts, QuoteStatus.CONVERTED),
        count(counts, QuoteStatus.SUPERSEDED));
  }

  public long countFor(QuoteStatus status) {
    return switch (status) {
      case DRAFT -> draft;
      case EVALUATION -> evaluation;
      case PENDING_APPROVAL -> pendingApproval;
      case APPROVED -> approved;
      case REJECTED -> rejected;
      case EXPIRED -> expired;
      case CONVERTED -> converted;
      case SUPERSEDED -> superseded;
    };
  }

  private static long count(Map<QuoteStatus, Long> counts, QuoteStatus status) {
    return counts.getOrDefault(status, 0L);
  }
}
