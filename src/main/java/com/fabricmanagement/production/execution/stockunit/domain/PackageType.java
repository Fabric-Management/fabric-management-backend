package com.fabricmanagement.production.execution.stockunit.domain;

import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Physical packaging types for stock units in textile production.
 *
 * <p>Each package type is associated with one or more compatible {@link MaterialType}s. The
 * compatibility matrix is enforced at the domain level — a FIBER material cannot be packaged in a
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
 * @see MaterialType
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

  /** Carton — general-purpose cardboard box for various materials. */
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
   * Pre-computed compatibility matrix: MaterialType → allowed PackageTypes.
   *
   * <p>Stored as an unmodifiable {@code EnumMap} for O(1) lookup. Built once at class loading time.
   */
  private static final Map<MaterialType, Set<PackageType>> COMPATIBILITY_MATRIX;

  static {
    var matrix = new EnumMap<MaterialType, Set<PackageType>>(MaterialType.class);
    matrix.put(MaterialType.FIBER, Set.of(BALE, SACK));
    matrix.put(MaterialType.YARN, Set.of(BOBBIN, CONE, HANK, CARTON));
    matrix.put(MaterialType.FABRIC, Set.of(ROLL, CARTON, PALLET));
    matrix.put(MaterialType.CHEMICAL, Set.of(DRUM, BAG, SACK, CARTON));
    matrix.put(MaterialType.CONSUMABLE, Set.of(BAG, CARTON, PALLET));
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
   * Checks whether this package type is compatible with the given material type.
   *
   * <p>Example: {@code PackageType.BALE.isCompatibleWith(MaterialType.FIBER)} → {@code true}
   *
   * @param materialType the material type to check compatibility against
   * @return true if this package type can be used for the given material type
   * @throws IllegalArgumentException if materialType is null
   */
  public boolean isCompatibleWith(MaterialType materialType) {
    if (materialType == null) {
      throw new IllegalArgumentException("MaterialType must not be null");
    }
    return COMPATIBILITY_MATRIX.getOrDefault(materialType, Set.of()).contains(this);
  }

  /**
   * Returns the set of package types that are compatible with the given material type.
   *
   * <p>Used by the UI to filter available package type options when a material type is selected.
   *
   * <p>Example: {@code PackageType.allowedFor(MaterialType.YARN)} → {@code [BOBBIN, CONE, HANK,
   * CARTON]}
   *
   * @param materialType the material type to get compatible package types for
   * @return unmodifiable set of compatible package types (empty if materialType is unknown)
   * @throws IllegalArgumentException if materialType is null
   */
  public static Set<PackageType> allowedFor(MaterialType materialType) {
    if (materialType == null) {
      throw new IllegalArgumentException("MaterialType must not be null");
    }
    return COMPATIBILITY_MATRIX.getOrDefault(materialType, Set.of());
  }

  /**
   * Validates that this package type is compatible with the given material type.
   *
   * <p>Convenience method that throws a descriptive exception on incompatibility. Preferred over
   * manual {@code if (!isCompatibleWith(...))} checks in entity factory methods and service layers.
   *
   * @param materialType the material type to validate against
   * @throws IllegalStateException if this package type is not compatible with the material type
   */
  public void validateCompatibility(MaterialType materialType) {
    if (!isCompatibleWith(materialType)) {
      throw new IllegalStateException(
          String.format(
              "PackageType %s is not compatible with MaterialType %s. Allowed types: %s",
              this, materialType, allowedFor(materialType)));
    }
  }
}
