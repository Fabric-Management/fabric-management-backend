package com.fabricmanagement.order.sales.app;

import com.fabricmanagement.order.common.exception.OrderDomainException;
import com.fabricmanagement.order.sales.domain.ModuleType;
import com.fabricmanagement.order.sales.dto.SalesOrderLineRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Validates {@code moduleSpecs} JSONB content against the required fields per {@link ModuleType}.
 *
 * <p>Schema source: architecture doc {@code 03-sales/sales-order.md} — ModuleSpecs JSONB Şemaları.
 *
 * <ul>
 *   <li>FIBER — certificationReq, originReq
 *   <li>YARN — count, twist, construction
 *   <li>FABRIC — weight, width, weaveType
 *   <li>DYE_FINISHING — color
 * </ul>
 *
 * <p>Validation is intentionally non-exhaustive (not all module fields are required) — only the
 * "must-have" fields identified in the arch doc are enforced.
 */
@Component
public class ModuleSpecsValidator {

  /**
   * Validates the given line's {@code moduleSpecs} against its {@code moduleType}.
   *
   * @throws OrderDomainException if a required field is missing or specs are null when needed
   */
  public void validate(SalesOrderLineRequest line) {
    ModuleType type = line.getModuleType();
    if (type == null) {
      return; // moduleType is optional at the line level — no specs to validate
    }

    Map<String, Object> specs = line.getModuleSpecs();

    switch (type) {
      case FIBER -> {
        // FIBER requires certificationReq (GOTS / OEKO-TEX etc.) and originReq
        requireField(specs, "certificationReq", type);
        requireField(specs, "originReq", type);
      }
      case YARN -> {
        // YARN requires count (e.g., "30/1 Ne"), twist (Z/S), construction
        requireField(specs, "count", type);
        requireField(specs, "twist", type);
        requireField(specs, "construction", type);
      }
      case FABRIC -> {
        // FABRIC requires weight (g/m²) and width (cm)
        requireField(specs, "weight", type);
        requireField(specs, "width", type);
      }
      case DYE_FINISHING -> {
        // DYE_FINISHING requires color (Pantone code or name)
        requireField(specs, "color", type);
      }
    }
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private void requireField(Map<String, Object> specs, String field, ModuleType type) {
    if (specs == null || !specs.containsKey(field) || specs.get(field) == null) {
      throw new OrderDomainException(
          String.format("moduleSpecs missing required field '%s' for moduleType %s", field, type));
    }
    Object val = specs.get(field);
    if (val instanceof String s && s.isBlank()) {
      throw new OrderDomainException(
          String.format("moduleSpecs field '%s' must not be blank for moduleType %s", field, type));
    }
  }
}
