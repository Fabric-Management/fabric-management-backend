package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Wire contract for immutable evidence; decimals are deliberately strings. */
@Schema(name = "OrderCoverEvidence")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrderCoverEvidenceDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID caseId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") long revision,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long orderVersion,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant computedAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String inputFingerprint,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String ruleVersion,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Line> lines) {
  public OrderCoverEvidenceDto {
    lines = List.copyOf(lines);
  }

  @Schema(name = "OrderCoverLineEvidence")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Line(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID lineId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long lineVersion,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID productId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Quantity requested,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "Suitable free stock before suggestions to other order lines; not capped by demand")
          Quantity suitableFree,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "Suitable stock after earlier line suggestions, before this line's own suggestion")
          Quantity remainingSuitableFree,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Quantity shortfall,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Suitability suitability,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          List<CompetingAllocation> competingAllocations,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> controlReasons,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Source> sources,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> blockingReasons) {
    public Line {
      competingAllocations = List.copyOf(competingAllocations);
      controlReasons = List.copyOf(controlReasons);
      sources = List.copyOf(sources);
      blockingReasons = List.copyOf(blockingReasons);
    }
  }

  @Schema(name = "OrderCoverCompetingAllocation")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record CompetingAllocation(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID lineId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Quantity quantity,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, minimum = "1")
          Integer lineNumber,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String label) {
    public CompetingAllocation(UUID lineId, Quantity quantity) {
      this(lineId, quantity, null, null);
    }

    public CompetingAllocation {
      Objects.requireNonNull(lineId);
      Objects.requireNonNull(quantity);
      if (quantity.state() != Knowledge.KNOWN || new BigDecimal(quantity.value()).signum() <= 0)
        throw new IllegalArgumentException(
            "Competing allocation requires a known positive quantity");
    }
  }

  @Schema(name = "OrderCoverQuantity")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Quantity(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Knowledge state,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true,
              pattern = "^-?[0-9]+(\\.[0-9]+)?$")
          String value,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String unit,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String reasonCode) {
    public Quantity {
      Objects.requireNonNull(state);
      if (state == Knowledge.UNKNOWN) {
        if (value != null || reasonCode == null || reasonCode.isBlank()) {
          throw new IllegalArgumentException("Unknown quantity requires a reason and no value");
        }
      } else if (value == null
          || !value.matches("^-?[0-9]+(\\.[0-9]+)?$")
          || unit == null
          || unit.isBlank()) {
        throw new IllegalArgumentException(
            "Known or estimated quantity requires a decimal and unit");
      }
    }

    public static Quantity known(BigDecimal value, String unit) {
      return new Quantity(Knowledge.KNOWN, value.stripTrailingZeros().toPlainString(), unit, null);
    }

    public static Quantity unknown(String unit, String reason) {
      return new Quantity(Knowledge.UNKNOWN, null, unit, reason);
    }
  }

  @Schema(name = "OrderCoverSource")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Source(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String type,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Long revision,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant observedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant recordedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SourceKnowledge knowledge,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          String accessibleHref) {}

  @Schema(name = "OrderCoverQuantityKnowledge")
  public enum Knowledge {
    KNOWN,
    ESTIMATE,
    UNKNOWN
  }

  @Schema(name = "OrderCoverSuitability")
  public enum Suitability {
    EXACT,
    AMBIGUOUS,
    NO_MATCH,
    UNKNOWN
  }

  @Schema(name = "OrderCoverSourceKnowledge")
  public enum SourceKnowledge {
    VERIFIED,
    UNKNOWN,
    CONTRADICTED
  }
}
