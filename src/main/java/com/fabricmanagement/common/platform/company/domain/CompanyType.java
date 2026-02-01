package com.fabricmanagement.common.platform.company.domain;

import java.util.Arrays;
import java.util.List;

/**
 * Company type classification in the fabric management ecosystem.
 *
 * <p>Each type has a {@link CompanyCategory}. Use {@link #getByCategory(CompanyCategory)} for
 * category-based filtering.
 *
 * <h2>Usage:</h2>
 *
 * <ul>
 *   <li>Multi-tenant isolation (tenant companies)
 *   <li>Business relationship management (suppliers, partners)
 *   <li>Access control policies
 *   <li>Reporting and analytics
 * </ul>
 */
public enum CompanyType {

  // ========================================
  // TENANT COMPANIES (Platform Users)
  // ========================================
  SPINNER(CompanyCategory.TENANT),
  WEAVER(CompanyCategory.TENANT),
  KNITTER(CompanyCategory.TENANT),
  DYER_FINISHER(CompanyCategory.TENANT),
  VERTICAL_MILL(CompanyCategory.TENANT),
  GARMENT_MANUFACTURER(CompanyCategory.TENANT),

  // ========================================
  // SUPPLIER COMPANIES (Material Suppliers)
  // ========================================
  FIBER_SUPPLIER(CompanyCategory.SUPPLIER),
  YARN_SUPPLIER(CompanyCategory.SUPPLIER),
  CHEMICAL_SUPPLIER(CompanyCategory.SUPPLIER),
  CONSUMABLE_SUPPLIER(CompanyCategory.SUPPLIER),
  PACKAGING_SUPPLIER(CompanyCategory.SUPPLIER),
  MACHINE_SUPPLIER(CompanyCategory.SUPPLIER),

  // ========================================
  // SERVICE PROVIDER COMPANIES
  // ========================================
  LOGISTICS_PROVIDER(CompanyCategory.SERVICE_PROVIDER),
  MAINTENANCE_SERVICE(CompanyCategory.SERVICE_PROVIDER),
  IT_SERVICE_PROVIDER(CompanyCategory.SERVICE_PROVIDER),
  KITCHEN_SUPPLIER(CompanyCategory.SERVICE_PROVIDER),
  HR_SERVICE_PROVIDER(CompanyCategory.SERVICE_PROVIDER),
  LAB(CompanyCategory.SERVICE_PROVIDER),
  UTILITY_PROVIDER(CompanyCategory.SERVICE_PROVIDER),

  // ========================================
  // PARTNER COMPANIES (Business Partners)
  // ========================================
  FASON(CompanyCategory.PARTNER),
  AGENT(CompanyCategory.PARTNER),
  TRADER(CompanyCategory.PARTNER),
  FINANCE_PARTNER(CompanyCategory.PARTNER),

  // ========================================
  // CUSTOMER COMPANIES
  // ========================================
  CUSTOMER(CompanyCategory.CUSTOMER);

  private final CompanyCategory category;

  CompanyType(CompanyCategory category) {
    this.category = category;
  }

  public CompanyCategory getCategory() {
    return category;
  }

  /**
   * Returns all types in the given category.
   *
   * @param category company category
   * @return list of company types in that category
   */
  public static List<CompanyType> getByCategory(CompanyCategory category) {
    return Arrays.stream(values()).filter(t -> t.category == category).toList();
  }

  /**
   * Check if this company type can be a platform tenant.
   *
   * @return true if can use platform as tenant
   */
  public boolean isTenant() {
    return category == CompanyCategory.TENANT;
  }

  /**
   * Get suggested OS codes for tenant companies.
   *
   * @return list of suggested OS codes
   */
  public String[] getSuggestedOS() {
    return switch (this) {
      case SPINNER -> new String[] {"SpinnerOS", "YarnOS"};
      case WEAVER -> new String[] {"WeaverOS", "LoomOS"};
      case KNITTER -> new String[] {"KnitterOS", "KnitOS"};
      case DYER_FINISHER -> new String[] {"DyeOS", "FinishOS"};
      case VERTICAL_MILL -> new String[] {"FabricOS"}; // Complete package
      case GARMENT_MANUFACTURER -> new String[] {"GarmentOS"};
      default -> new String[] {};
    };
  }
}
