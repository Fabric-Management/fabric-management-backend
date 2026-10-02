package com.fabricmanagement.product.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.fiber.domain.exception.FiberDomainException;
import com.fabricmanagement.product.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.product.fiber.domain.reference.FiberIsoCode;
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
      @Index(name = "idx_fiber_product", columnList = "product_id"),
      @Index(name = "idx_fiber_category", columnList = "fiber_category_id"),
      @Index(name = "idx_fiber_iso", columnList = "fiber_iso_code_id"),
      @Index(name = "idx_fiber_status", columnList = "status")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_fiber_product",
          columnNames = {"product_id"})
    })
@Getter
@Setter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
public class Fiber extends BaseEntity {

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", nullable = false, unique = true, updatable = false)
  private Product product;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "fiber_category_id", nullable = false)
  private FiberCategory fiberCategory;

  /** Shared ISO code of a pure fibre; always null for a blend (FIBER-CATALOG-1). */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "fiber_iso_code_id")
  private FiberIsoCode fiberIsoCode;

  // Helper methods for accessing IDs without loading entities
  public UUID getProductId() {
    return product != null ? product.getId() : null;
  }

  public UUID getFiberCategoryId() {
    return fiberCategory != null ? fiberCategory.getId() : null;
  }

  public UUID getFiberIsoCodeId() {
    return fiberIsoCode != null ? fiberIsoCode.getId() : null;
  }

  @Column(name = "fiber_name", nullable = false, length = 255)
  private String fiberName;

  @Enumerated(EnumType.STRING)
  @Column(name = "material_source", length = 20)
  @Setter(AccessLevel.NONE)
  private MaterialSource materialSource;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private FiberStatus status = FiberStatus.ACTIVE;

  @Column(name = "remarks", columnDefinition = "TEXT")
  private String remarks;

  /**
   * Authoritative composition as JSONB: {@code Fiber.id -> percentage}, never Product IDs.
   *
   * <p><b>Pure fibre:</b> empty map. <b>Blend:</b> two or more pure fibre IDs whose exact decimal
   * percentages sum to 100, stored normalised (insignificant zeroes stripped, never rounded).
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

  /** Canonical shared pure fibre published by the catalogue owner; source stays undeclared. */
  public static Fiber createCanonicalPure(
      Product product, FiberCategory fiberCategory, FiberIsoCode fiberIsoCode, String fiberName) {
    return createPure(product, fiberCategory, fiberIsoCode, fiberName, null);
  }

  /**
   * Tenant-private pure variant of a shared ISO code with a declared material source. The shared
   * ISO and category rows are referenced, never copied.
   */
  public static Fiber createSourceVariant(
      Product product,
      FiberCategory fiberCategory,
      FiberIsoCode fiberIsoCode,
      String fiberName,
      MaterialSource materialSource) {
    if (materialSource == null) {
      throw new FiberDomainException(
          "A private pure variant requires a declared material source",
          "FIBER_MATERIAL_SOURCE_REQUIRED",
          400);
    }
    return createPure(product, fiberCategory, fiberIsoCode, fiberName, materialSource);
  }

  private static Fiber createPure(
      Product product,
      FiberCategory fiberCategory,
      FiberIsoCode fiberIsoCode,
      String fiberName,
      MaterialSource materialSource) {
    if (fiberIsoCode == null) {
      throw new FiberDomainException(
          "A pure fibre requires a shared ISO code", "FIBER_ISO_REQUIRED", 400);
    }
    return Fiber.builder()
        .product(product)
        .fiberCategory(fiberCategory)
        .fiberIsoCode(fiberIsoCode)
        .fiberName(fiberName)
        .materialSource(materialSource)
        .composition(new HashMap<>())
        .status(FiberStatus.ACTIVE)
        .build();
  }

  /**
   * Declares the origin of a legacy pure fiber exactly once.
   *
   * <p>The only supported transition is {@code null -> VIRGIN|RECYCLED}. Changing or clearing an
   * existing declaration would rewrite the identity of every referencing yarn specification.
   */
  public void declareMaterialSource(MaterialSource source) {
    if (source == null) {
      throw new FiberDomainException(
          "Material source declaration requires a value", "FIBER_MATERIAL_SOURCE_REQUIRED", 409);
    }
    if (isBlended()) {
      throw new FiberDomainException(
          "A blended fiber cannot carry one material source",
          "FIBER_BLEND_MATERIAL_SOURCE_FORBIDDEN",
          400);
    }
    if (materialSource != null) {
      throw new FiberDomainException(
          "Material source is immutable once declared",
          "FIBER_MATERIAL_SOURCE_IMMUTABLE",
          409,
          new Object[] {materialSource, source});
    }
    materialSource = source;
  }

  /**
   * Tenant-owned blend. It carries no ISO code and no single material source; the composition is
   * already validated and normalised by the caller.
   */
  public static Fiber createBlend(
      Product product,
      FiberCategory mixedBlendCategory,
      String fiberName,
      Map<UUID, BigDecimal> composition) {
    if (composition == null || composition.size() < 2) {
      throw new FiberDomainException(
          "A blend requires at least two components", "FIBER_BLEND_MIN_COMPONENTS", 400);
    }
    return Fiber.builder()
        .product(product)
        .fiberCategory(mixedBlendCategory)
        .fiberIsoCode(null)
        .fiberName(fiberName)
        .composition(new HashMap<>(FiberComposition.normalize(composition)))
        .status(FiberStatus.ACTIVE)
        .build();
  }

  /** Replaces a blend's validated composition; a pure fibre can never become a blend. */
  public void changeBlendComposition(Map<UUID, BigDecimal> newComposition) {
    if (!isBlended()) {
      throw new FiberDomainException(
          "A pure fibre cannot become a blend", "FIBER_KIND_CHANGE_FORBIDDEN", 400);
    }
    if (newComposition == null || newComposition.size() < 2) {
      throw new FiberDomainException(
          "A blend requires at least two components", "FIBER_BLEND_MIN_COMPONENTS", 400);
    }
    this.composition = new HashMap<>(FiberComposition.normalize(newComposition));
  }

  public FiberKind getKind() {
    return isBlended() ? FiberKind.BLEND : FiberKind.PURE;
  }

  /** True when the platform catalogue owns this record. */
  public boolean isShared() {
    return FiberCatalog.isOwner(getTenantId());
  }

  /** Update fiber properties (excluding status - use lifecycle methods instead). */
  public void update(String fiberName, String remarks) {
    this.fiberName = fiberName;
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
