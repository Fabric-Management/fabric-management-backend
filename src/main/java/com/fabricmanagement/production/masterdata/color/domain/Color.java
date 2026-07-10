package com.fabricmanagement.production.masterdata.color.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.masterdata.color.domain.exception.ColorDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Tenant-owned color-card master. A color card is a color identity, not a dye lot.
 *
 * <p>It holds the <em>target</em> shade standard. Measured values, dye recipes and customer lab-dip
 * approvals vary per batch, per substrate and per customer, so none of them live here.
 *
 * <p>Once {@link ColorStandardStatus#APPROVED}, the standard-defining fields are frozen: an
 * approval that does not stop anything from changing is not an approval. Editing them again
 * requires an explicit {@link #revertToDraft()}.
 */
@Entity
@Table(
    name = "color",
    schema = "production",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_color_tenant_code",
          columnNames = {"tenant_id", "code"})
    },
    indexes = {
      @Index(name = "idx_color_tenant_active", columnList = "tenant_id, is_active"),
      @Index(name = "idx_color_tenant_code", columnList = "tenant_id, code"),
      @Index(name = "idx_color_tenant_family", columnList = "tenant_id, color_family")
    })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Color extends BaseEntity {

  @Column(name = "code", nullable = false, length = 50)
  private String code;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  /** Screen approximation only. Never a colour standard — use the target Lab for that. */
  @Column(name = "color_hex", length = 7)
  private String colorHex;

  @Enumerated(EnumType.STRING)
  @Column(name = "color_type", nullable = false, length = 20)
  private ColorType colorType;

  @Enumerated(EnumType.STRING)
  @Column(name = "color_family", nullable = false, length = 20)
  private ColorFamily colorFamily;

  @Column(name = "pantone_code", length = 20)
  private String pantoneCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "pantone_system", length = 10)
  private PantoneSystem pantoneSystem;

  @Enumerated(EnumType.STRING)
  @Column(name = "standard_status", nullable = false, length = 20)
  private ColorStandardStatus standardStatus;

  @Column(name = "target_lab_l", precision = 6, scale = 2)
  private BigDecimal targetLabL;

  @Column(name = "target_lab_a", precision = 6, scale = 2)
  private BigDecimal targetLabA;

  @Column(name = "target_lab_b", precision = 6, scale = 2)
  private BigDecimal targetLabB;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_lab_illuminant", length = 10)
  private LabIlluminant targetLabIlluminant;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_lab_observer", length = 10)
  private LabObserver targetLabObserver;

  @Column(name = "delta_e_tolerance", precision = 4, scale = 2)
  private BigDecimal deltaETolerance;

  @Enumerated(EnumType.STRING)
  @Column(name = "delta_e_formula", length = 20)
  private DeltaEFormula deltaEFormula;

  @Column(name = "notes", length = 1000)
  private String notes;

  public static Color create(java.util.UUID tenantId, ColorCardSpec spec) {
    Color color = new Color();
    color.standardStatus = ColorStandardStatus.DRAFT;
    color.apply(spec);
    color.setTenantId(tenantId);
    color.onCreate();
    return color;
  }

  /** Minimal card, no shade standard. */
  public static Color create(java.util.UUID tenantId, String code, String name, String colorHex) {
    return create(tenantId, ColorCardSpec.basic(code, name, colorHex));
  }

  public void update(ColorCardSpec spec) {
    apply(spec);
  }

  /**
   * Freezes the standard. Refuses cards that have nothing to be measured against — an approved card
   * with neither a target Lab nor a Pantone reference would sign off on nothing. Idempotent.
   */
  public void approve() {
    if (!colorType.isUndyed() && !hasTarget()) {
      throw ColorDomainException.invalid(
          "Cannot approve a colour standard with no target Lab and no Pantone reference");
    }
    this.standardStatus = ColorStandardStatus.APPROVED;
  }

  /** Reopens the standard for editing. Idempotent. */
  public void revertToDraft() {
    this.standardStatus = ColorStandardStatus.DRAFT;
  }

  public boolean hasTarget() {
    return targetLabL != null || pantoneCode != null;
  }

  /** Normalises every field, then enforces the cross-field rules. */
  private void apply(ColorCardSpec spec) {
    ColorType type = spec.colorType() == null ? ColorType.DYED : spec.colorType();
    ColorFamily family = spec.colorFamily() == null ? ColorFamily.UNDEFINED : spec.colorFamily();
    String hex = normalizeHex(spec.colorHex());
    String pantone = normalizeText(spec.pantoneCode(), true);
    PantoneSystem carrier = spec.pantoneSystem();
    BigDecimal tolerance = spec.deltaETolerance();
    DeltaEFormula formula = spec.deltaEFormula();

    if (pantone == null && carrier != null) {
      throw ColorDomainException.invalid("Pantone system requires a Pantone code");
    }
    if (pantone != null && carrier == null) {
      carrier = PantoneSystem.TCX;
    }

    int labParts = countNonNull(spec.targetLabL(), spec.targetLabA(), spec.targetLabB());
    int labContext = countNonNull(spec.targetLabIlluminant(), spec.targetLabObserver());
    if (labParts + labContext != 0 && labParts + labContext != 5) {
      throw ColorDomainException.invalid(
          "Target Lab needs L, a, b, illuminant and observer together, or none of them");
    }
    requireRange(spec.targetLabL(), 0, 100, "Target Lab L");
    requireRange(spec.targetLabA(), -128, 127, "Target Lab a");
    requireRange(spec.targetLabB(), -128, 127, "Target Lab b");

    if (tolerance != null) {
      if (tolerance.signum() <= 0) {
        throw ColorDomainException.invalid("Delta-E tolerance must be greater than zero");
      }
      if (labParts == 0 && pantone == null) {
        throw ColorDomainException.invalid(
            "Delta-E tolerance needs something to measure against: "
                + "a complete target Lab or a Pantone reference");
      }
      if (formula == null) {
        formula = DeltaEFormula.CMC_2_1;
      }
    } else if (formula != null) {
      throw ColorDomainException.invalid("Delta-E formula requires a tolerance");
    }

    if (type.isUndyed() && (hex != null || pantone != null || labParts > 0 || tolerance != null)) {
      throw ColorDomainException.invalid(
          "Undyed colour cards (" + type + ") cannot carry a shade standard");
    }

    if (standardStatus == ColorStandardStatus.APPROVED
        && standardWouldChange(
            type,
            pantone,
            carrier,
            spec.targetLabL(),
            spec.targetLabA(),
            spec.targetLabB(),
            spec.targetLabIlluminant(),
            spec.targetLabObserver(),
            tolerance,
            formula)) {
      throw ColorDomainException.approvedStandardIsImmutable(code);
    }

    this.code = normalizeCode(spec.code());
    this.name = normalizeName(spec.name());
    this.colorHex = hex;
    this.colorType = type;
    this.colorFamily = family;
    this.pantoneCode = pantone;
    this.pantoneSystem = carrier;
    this.targetLabL = spec.targetLabL();
    this.targetLabA = spec.targetLabA();
    this.targetLabB = spec.targetLabB();
    this.targetLabIlluminant = spec.targetLabIlluminant();
    this.targetLabObserver = spec.targetLabObserver();
    this.deltaETolerance = tolerance;
    this.deltaEFormula = formula;
    this.notes = normalizeText(spec.notes(), false);
  }

  /**
   * Which fields the approval actually covers. Name, notes, family and hex stay editable: hex is a
   * screen approximation, not part of the standard.
   */
  private boolean standardWouldChange(
      ColorType type,
      String pantone,
      PantoneSystem carrier,
      BigDecimal labL,
      BigDecimal labA,
      BigDecimal labB,
      LabIlluminant illuminant,
      LabObserver observer,
      BigDecimal tolerance,
      DeltaEFormula formula) {
    return this.colorType != type
        || !Objects.equals(this.pantoneCode, pantone)
        || this.pantoneSystem != carrier
        || differs(this.targetLabL, labL)
        || differs(this.targetLabA, labA)
        || differs(this.targetLabB, labB)
        || this.targetLabIlluminant != illuminant
        || this.targetLabObserver != observer
        || differs(this.deltaETolerance, tolerance)
        || this.deltaEFormula != formula;
  }

  /**
   * {@link BigDecimal#equals} compares scale; {@code 1.2} and {@code 1.20} are the same tolerance.
   */
  private static boolean differs(BigDecimal left, BigDecimal right) {
    if (left == null || right == null) {
      return left != right;
    }
    return left.compareTo(right) != 0;
  }

  private static int countNonNull(Object... values) {
    int count = 0;
    for (Object value : values) {
      if (value != null) {
        count++;
      }
    }
    return count;
  }

  private static void requireRange(BigDecimal value, int min, int max, String label) {
    if (value == null) {
      return;
    }
    if (value.compareTo(BigDecimal.valueOf(min)) < 0
        || value.compareTo(BigDecimal.valueOf(max)) > 0) {
      throw ColorDomainException.invalid(label + " must be between " + min + " and " + max);
    }
  }

  private static String normalizeCode(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("Color code must not be blank");
    }
    return code.trim().toUpperCase(Locale.ROOT);
  }

  private static String normalizeName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Color name must not be blank");
    }
    return name.trim();
  }

  private static String normalizeText(String value, boolean upperCase) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    return upperCase ? trimmed.toUpperCase(Locale.ROOT) : trimmed;
  }

  private static String normalizeHex(String colorHex) {
    if (colorHex == null || colorHex.isBlank()) {
      return null;
    }
    String normalized = colorHex.trim().toUpperCase(Locale.ROOT);
    if (!normalized.matches("^#[0-9A-F]{6}$")) {
      throw new IllegalArgumentException("Color hex must match #RRGGBB");
    }
    return normalized;
  }

  @Override
  protected String getModuleCode() {
    return "COL";
  }
}
