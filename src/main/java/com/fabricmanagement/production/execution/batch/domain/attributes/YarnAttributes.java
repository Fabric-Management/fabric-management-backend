package com.fabricmanagement.production.execution.batch.domain.attributes;

import static com.fabricmanagement.production.execution.batch.domain.attributes.AttributeConversions.asString;
import static com.fabricmanagement.production.execution.batch.domain.attributes.AttributeConversions.toDouble;
import static com.fabricmanagement.production.execution.batch.domain.attributes.AttributeConversions.toInteger;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashMap;
import java.util.Map;

/**
 * Type-safe representation of yarn batch JSONB attributes.
 *
 * <p>Keys match the snake_case convention used in {@code Batch.attributes} and the architectural
 * documentation. Use {@link #from(Map)} and {@link #toMap()} to convert between {@code Map<String,
 * Object>} and this record.
 */
@Schema(description = "Detailed specifications for YARN batches")
public record YarnAttributes(
    @Schema(description = "Yarn count/thickness (e.g., 30/1, 20/2)", example = "30/1")
        @JsonProperty("yarn_count")
        String yarnCount,
    @Schema(description = "Twist direction (Z or S)", example = "Z") @JsonProperty("yarn_twist")
        String twistDirection,
    @Schema(
            description = "Yarn construction method (e.g., RING, OPEN_END, COMPACT)",
            example = "RING")
        @JsonProperty("yarn_construction")
        String construction,
    @Schema(description = "Number of plies", example = "1") @JsonProperty("yarn_ply") Integer ply,
    @Schema(description = "Yarn composition string", example = "100% Cotton")
        @JsonProperty("yarn_composition")
        String composition,
    @Schema(description = "Turns per inch (TPI)", example = "18.5") @JsonProperty("yarn_tpi")
        Double tpi,
    @Schema(description = "Count Strength Product (CSP)", example = "2800")
        @JsonProperty("yarn_csp")
        Double csp) {

  /**
   * Convert a raw attributes map to typed YarnAttributes.
   *
   * @param attrs the batch attributes map (snake_case keys); may be null
   * @return typed YarnAttributes; never null
   */
  public static YarnAttributes from(Map<String, Object> attrs) {
    if (attrs == null) {
      attrs = Map.of();
    }
    return new YarnAttributes(
        asString(attrs.get("yarn_count")),
        asString(attrs.get("yarn_twist")),
        asString(attrs.get("yarn_construction")),
        toInteger(attrs.get("yarn_ply")),
        asString(attrs.get("yarn_composition")),
        toDouble(attrs.get("yarn_tpi")),
        toDouble(attrs.get("yarn_csp")));
  }

  /**
   * Convert this record to a map with snake_case keys.
   *
   * @return map suitable for Batch.attributes; never null
   */
  public Map<String, Object> toMap() {
    Map<String, Object> m = new HashMap<>();
    if (yarnCount != null) {
      m.put("yarn_count", yarnCount);
    }
    if (twistDirection != null) {
      m.put("yarn_twist", twistDirection);
    }
    if (construction != null) {
      m.put("yarn_construction", construction);
    }
    if (ply != null) {
      m.put("yarn_ply", ply);
    }
    if (composition != null) {
      m.put("yarn_composition", composition);
    }
    if (tpi != null) {
      m.put("yarn_tpi", tpi);
    }
    if (csp != null) {
      m.put("yarn_csp", csp);
    }
    return m;
  }
}
