package com.fabricmanagement.production.execution.batch.domain.attributes;

import static com.fabricmanagement.production.execution.batch.domain.attributes.AttributeConversions.asString;
import static com.fabricmanagement.production.execution.batch.domain.attributes.AttributeConversions.toDouble;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashMap;
import java.util.Map;

/**
 * Type-safe representation of fiber batch JSONB attributes.
 *
 * <p>Keys match the snake_case convention used in {@code Batch.attributes} and {@code
 * fiber-to-yarn.json}. Use {@link #from(Map)} and {@link #toMap()} to convert between {@code
 * Map&lt;String, Object&gt;} and this record.
 *
 * <p><b>BatchAttributeInheritanceEngine</b> and <b>fiber-to-yarn.json</b> are unchanged; they
 * continue to use raw string keys.
 */
@Schema(description = "Detailed specifications for FIBER batches")
public record FiberAttributes(
    @Schema(description = "Fiber micronaire (fineness/thickness)", example = "4.2")
        @JsonProperty("fiber_micronaire")
        Double micronaire,
    @Schema(description = "Fiber staple length in millimeters", example = "28.5")
        @JsonProperty("fiber_staple_length")
        Double stapleLength,
    @Schema(description = "Fiber quality grade", example = "A_GRADE") @JsonProperty("fiber_grade")
        String grade,
    @Schema(description = "Fiber shade/color", example = "OPTICAL_WHITE")
        @JsonProperty("fiber_shade")
        String shade,
    @Schema(
            description = "Organic certification number (e.g., GOTS TC number)",
            example = "PRJ809982/1049294")
        @JsonProperty("fiber_organic_cert_no")
        String organicCertNo,
    @Schema(description = "Bale moisture percentage", example = "7.5")
        @JsonProperty("bale_moisture")
        Double baleMoisture) {

  /**
   * Convert a raw attributes map to typed FiberAttributes.
   *
   * <p>Null-safe: missing keys yield null. Handles numeric values stored as Number (Integer,
   * Double, BigDecimal).
   *
   * @param attrs the batch attributes map (snake_case keys); may be null
   * @return typed FiberAttributes; never null
   */
  public static FiberAttributes from(Map<String, Object> attrs) {
    if (attrs == null) {
      attrs = Map.of();
    }
    return new FiberAttributes(
        toDouble(attrs.get("fiber_micronaire")),
        toDouble(attrs.get("fiber_staple_length")),
        asString(attrs.get("fiber_grade")),
        asString(attrs.get("fiber_shade")),
        asString(attrs.get("fiber_organic_cert_no")),
        toDouble(attrs.get("bale_moisture")));
  }

  /**
   * Convert this record to a map with snake_case keys.
   *
   * <p>Only non-null values are included, matching the convention used by
   * BatchService.resolveAttributes.
   *
   * @return map suitable for Batch.attributes; never null
   */
  public Map<String, Object> toMap() {
    Map<String, Object> m = new HashMap<>();
    if (micronaire != null) {
      m.put("fiber_micronaire", micronaire);
    }
    if (stapleLength != null) {
      m.put("fiber_staple_length", stapleLength);
    }
    if (grade != null) {
      m.put("fiber_grade", grade);
    }
    if (shade != null) {
      m.put("fiber_shade", shade);
    }
    if (organicCertNo != null) {
      m.put("fiber_organic_cert_no", organicCertNo);
    }
    if (baleMoisture != null) {
      m.put("bale_moisture", baleMoisture);
    }
    return m;
  }
}
