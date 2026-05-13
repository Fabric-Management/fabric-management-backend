package com.fabricmanagement.production.execution.stockunit.domain;

import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Physical packaging types for stock units in textile production.
 *
 * <p>Each package type is associated with one or more compatible {@link ProductType}s. The
 * compatibility matrix is enforced at the domain level — a FIBER product cannot be packaged in a
 * BOBBIN, and a YARN cannot be packaged in a BALE.
 *
 * <h2>Compatibility Matrix</h2>
 *
 * <pre>
 * FIBER      → [BALE, SACK]
 * YARN       → [BOBBIN, CONE, HANK, CARTON]
 * FABRIC     → [ROLL, CARTON, PALLET]
 * CHEMICAL   → [DRUM, BAG, SACK, CARTON]
 * CONSUMABLE → [BAG, CARTON, PALLET]
 * </pre>
 *
 * <h2>Why Enum and Not a Database Table?</h2>
 *
 * <p>Package types are universal industry standards that do not vary by tenant. The compatibility
 * matrix encodes business rules that must be validated at compile time. Adding a new package type
 * requires a deployment — which is intentional: business rules belong in code, not in the database.
 *
 * @see ProductType
 */
public enum PackageType {

  /** Fiber bale — compressed raw fiber (cotton, polyester, wool). */
  BALE("Bale"),

  /** Fabric roll — wound fabric bolt on a tube/core. */
  ROLL("Roll"),

  /** Yarn bobbin — small yarn spool for weaving/knitting machines. */
  BOBBIN("Bobbin"),

  /** Sack — woven polypropylene or jute bag for bulk fibers/chemicals. */
  SACK("Sack"),

  /** Carton — general-purpose cardboard box for various products. */
  CARTON("Carton"),

  /** Drum — cylindrical container for liquid/paste chemicals. */
  DRUM("Drum"),

  /** Bag — small flexible container for consumables/chemicals. */
  BAG("Bag"),

  /** Pallet — multi-unit transport platform. */
  PALLET("Pallet"),

  /** Cone — tapered yarn package for winding/warping machines. */
  CONE("Cone"),

  /** Hank — looped yarn skein, traditional packaging for hand-dyed/specialty yarns. */
  HANK("Hank");

  private final String displayName;

  /**
   * Pre-computed compatibility matrix: ProductType → allowed PackageTypes.
   *
   * <p>Stored as an unmodifiable {@code EnumMap} for O(1) lookup. Built once at class loading time.
   */
  private static final Map<ProductType, Set<PackageType>> COMPATIBILITY_MATRIX;

  static {
    var matrix = new EnumMap<ProductType, Set<PackageType>>(ProductType.class);
    matrix.put(ProductType.FIBER, Set.of(BALE, SACK));
    matrix.put(ProductType.YARN, Set.of(BOBBIN, CONE, HANK, CARTON));
    matrix.put(ProductType.FABRIC, Set.of(ROLL, CARTON, PALLET));
    matrix.put(ProductType.CHEMICAL, Set.of(DRUM, BAG, SACK, CARTON));
    matrix.put(ProductType.CONSUMABLE, Set.of(BAG, CARTON, PALLET));
    COMPATIBILITY_MATRIX = Map.copyOf(matrix);
  }

  PackageType(String displayName) {
    this.displayName = displayName;
  }

  /** Human-readable display name for UI presentation. */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Checks whether this package type is compatible with the given product type.
   *
   * <p>Example: {@code PackageType.BALE.isCompatibleWith(ProductType.FIBER)} → {@code true}
   *
   * @param productType the product type to check compatibility against
   * @return true if this package type can be used for the given product type
   * @throws IllegalArgumentException if productType is null
   */
  public boolean isCompatibleWith(ProductType productType) {
    if (productType == null) {
      throw new IllegalArgumentException("ProductType must not be null");
    }
    return COMPATIBILITY_MATRIX.getOrDefault(productType, Set.of()).contains(this);
  }

  /**
   * Returns the set of package types that are compatible with the given product type.
   *
   * <p>Used by the UI to filter available package type options when a product type is selected.
   *
   * <p>Example: {@code PackageType.allowedFor(ProductType.YARN)} → {@code [BOBBIN, CONE, HANK,
   * CARTON]}
   *
   * @param productType the product type to get compatible package types for
   * @return unmodifiable set of compatible package types (empty if productType is unknown)
   * @throws IllegalArgumentException if productType is null
   */
  public static Set<PackageType> allowedFor(ProductType productType) {
    if (productType == null) {
      throw new IllegalArgumentException("ProductType must not be null");
    }
    return COMPATIBILITY_MATRIX.getOrDefault(productType, Set.of());
  }

  /**
   * Validates that this package type is compatible with the given product type.
   *
   * <p>Convenience method that throws a descriptive exception on incompatibility. Preferred over
   * manual {@code if (!isCompatibleWith(...))} checks in entity factory methods and service layers.
   *
   * @param productType the product type to validate against
   * @throws IllegalStateException if this package type is not compatible with the product type
   */
  public void validateCompatibility(ProductType productType) {
    if (!isCompatibleWith(productType)) {
      throw new IllegalStateException(
          String.format(
              "PackageType %s is not compatible with ProductType %s. Allowed types: %s",
              this, productType, allowedFor(productType)));
    }
  }
}
