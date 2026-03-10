package com.fabricmanagement.production.masterdata.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberIsoCode;
import com.fabricmanagement.production.masterdata.material.domain.Material;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.Type;

@Entity
@Table(
    name = "prod_fiber",
    schema = "production",
    indexes = {
      @Index(name = "idx_fiber_tenant", columnList = "tenant_id"),
      @Index(name = "idx_fiber_material", columnList = "material_id"),
      @Index(name = "idx_fiber_category", columnList = "fiber_category_id"),
      @Index(name = "idx_fiber_iso", columnList = "fiber_iso_code_id"),
      @Index(name = "idx_fiber_status", columnList = "status")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_fiber_material",
          columnNames = {"material_id"})
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Fiber extends BaseEntity {

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "material_id", nullable = false, unique = true, updatable = false)
  private Material material;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "fiber_category_id")
  private FiberCategory fiberCategory;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "fiber_iso_code_id")
  private FiberIsoCode fiberIsoCode;

  // Helper methods for accessing IDs without loading entities
  public UUID getMaterialId() {
    return material != null ? material.getId() : null;
  }

  public UUID getFiberCategoryId() {
    return fiberCategory != null ? fiberCategory.getId() : null;
  }

  public UUID getFiberIsoCodeId() {
    return fiberIsoCode != null ? fiberIsoCode.getId() : null;
  }

  @Column(name = "fiber_name", nullable = false, length = 255)
  private String fiberName;

  @Column(name = "fiber_grade", length = 50)
  private String fiberGrade;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private FiberStatus status = FiberStatus.ACTIVE;

  @Column(name = "remarks", columnDefinition = "TEXT")
  private String remarks;

  /**
   * Fiber composition as JSONB: Map of baseFiberId → percentage.
   *
   * <p><b>Pure fiber:</b> null or empty Map
   *
   * <p><b>Blended fiber:</b> Map with base fiber IDs and percentages (must sum to 100%)
   *
   * <p>Example: {"uuid1": "60.00", "uuid2": "40.00"} for 60%+40% blend
   */
  @Type(JsonType.class)
  @Column(name = "composition", columnDefinition = "jsonb")
  @Builder.Default
  private Map<UUID, BigDecimal> composition = new HashMap<>();

  /** Get composition map (never null). */
  public Map<UUID, BigDecimal> getComposition() {
    return composition != null ? composition : new HashMap<>();
  }

  /** Check if fiber is blended (has composition). */
  public boolean isBlended() {
    return composition != null && !composition.isEmpty();
  }

  /** Check if fiber is pure (no composition). */
  public boolean isPure() {
    return !isBlended();
  }

  public static Fiber createPureFiber(
      Material material,
      FiberCategory fiberCategory,
      FiberIsoCode fiberIsoCode,
      String fiberName,
      String fiberGrade) {

    return Fiber.builder()
        .material(material)
        .fiberCategory(fiberCategory)
        .fiberIsoCode(fiberIsoCode)
        .fiberName(fiberName)
        .fiberGrade(fiberGrade)
        .composition(new HashMap<>())
        .status(FiberStatus.ACTIVE)
        .build();
  }

  public static Fiber createBlendedFiber(
      Material material,
      FiberCategory fiberCategory,
      FiberIsoCode fiberIsoCode,
      String fiberName,
      String fiberGrade,
      Map<UUID, BigDecimal> composition) {

    return Fiber.builder()
        .material(material)
        .fiberCategory(fiberCategory)
        .fiberIsoCode(fiberIsoCode)
        .fiberName(fiberName)
        .fiberGrade(fiberGrade)
        .composition(composition != null ? composition : new HashMap<>())
        .status(FiberStatus.ACTIVE)
        .build();
  }

  /** Update fiber properties (excluding status - use lifecycle methods instead). */
  public void update(String fiberName, String fiberGrade, String remarks) {
    this.fiberName = fiberName;
    this.fiberGrade = fiberGrade;
    this.remarks = remarks;
  }

  /**
   * Mark fiber as obsolete.
   *
   * <p>Transition: ACTIVE → OBSOLETE
   *
   * <p>Use when fiber is discontinued or no longer valid.
   */
  public void markObsolete() {
    if (this.status == FiberStatus.ACTIVE) {
      this.status = FiberStatus.OBSOLETE;
    } else {
      throw new IllegalStateException(
          String.format(
              "Cannot mark fiber as OBSOLETE from status: %s. Only ACTIVE fibers can be marked OBSOLETE.",
              this.status));
    }
  }

  /**
   * Check if fiber is available for use.
   *
   * @return true if status is ACTIVE
   */
  public boolean isAvailable() {
    return this.status == FiberStatus.ACTIVE;
  }

  @Override
  protected String getModuleCode() {
    return "FIB";
  }
}
