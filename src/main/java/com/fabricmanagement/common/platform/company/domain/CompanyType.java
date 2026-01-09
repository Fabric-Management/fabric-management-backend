package com.fabricmanagement.common.platform.company.domain;

/**
 * Company type classification in the fabric management ecosystem.
 *
 * <p>Comprehensive classification covering all company types in textile industry. Categories:
 * TENANT (platform users), SUPPLIER, SERVICE_PROVIDER, PARTNER, CUSTOMER
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

  /**
   * Yarn producer (İplikçi)
   *
   * <p>TENANT - Converts fiber to yarn
   *
   * <p>OS: SpinnerOS, YarnOS
   */
  SPINNER,

  /**
   * Weaving producer (Dokumacı)
   *
   * <p>TENANT - Produces woven fabric (warp & weft)
   *
   * <p>OS: WeaverOS, LoomOS
   */
  WEAVER,

  /**
   * Knitting producer (Örücü)
   *
   * <p>TENANT - Produces knitted fabric (jersey, rib, etc.)
   *
   * <p>OS: KnitterOS
   */
  KNITTER,

  /**
   * Dyeing & Finishing plant (Boyahane/Terbiye)
   *
   * <p>TENANT - Dyeing and finishing processes
   *
   * <p>OS: DyeOS, FinishOS
   */
  DYER_FINISHER,

  /**
   * Vertical integrated mill (Entegre Tesis)
   *
   * <p>TENANT - Complete production: fiber → yarn → fabric → dye
   *
   * <p>OS: FabricOS (all modules)
   */
  VERTICAL_MILL,

  /**
   * Garment manufacturer (Konfeksiyon)
   *
   * <p>TENANT - Produces finished garments from fabric
   *
   * <p>OS: GarmentOS
   */
  GARMENT_MANUFACTURER,

  // ========================================
  // SUPPLIER COMPANIES (Material Suppliers)
  // ========================================

  /**
   * Fiber supplier (Elyaf Tedarikçisi)
   *
   * <p>SUPPLIER - Supplies cotton, polyester, wool, etc.
   */
  FIBER_SUPPLIER,

  /**
   * Yarn supplier (İplik Tedarikçisi)
   *
   * <p>SUPPLIER - Supplies various yarn types
   */
  YARN_SUPPLIER,

  /**
   * Chemical supplier (Kimyasal Tedarikçisi)
   *
   * <p>SUPPLIER - Dyes, auxiliaries, chemicals
   */
  CHEMICAL_SUPPLIER,

  /**
   * Consumable supplier (Sarf Malzeme Tedarikçisi)
   *
   * <p>SUPPLIER - Oil, needles, machine parts, cleaning products
   */
  CONSUMABLE_SUPPLIER,

  /**
   * Packaging supplier (Ambalaj Tedarikçisi)
   *
   * <p>SUPPLIER - Boxes, bags, labels, packaging materials
   */
  PACKAGING_SUPPLIER,

  /**
   * Machine supplier (Makine Tedarikçisi)
   *
   * <p>SUPPLIER - Weaving, knitting, dyeing machines
   *
   * <p>Examples: Dornier, Monforts, Mayer
   */
  MACHINE_SUPPLIER,

  // ========================================
  // SERVICE PROVIDER COMPANIES
  // ========================================

  /**
   * Logistics provider (Lojistik Sağlayıcı)
   *
   * <p>SERVICE - Shipping, warehousing, customs clearance
   */
  LOGISTICS_PROVIDER,

  /**
   * Maintenance service (Bakım Servisi)
   *
   * <p>SERVICE - Machine maintenance, technical support, spare parts
   */
  MAINTENANCE_SERVICE,

  /**
   * IT service provider (IT Hizmet Sağlayıcı)
   *
   * <p>SERVICE - ERP, automation, software, network infrastructure
   */
  IT_SERVICE_PROVIDER,

  /**
   * Kitchen/Canteen supplier (Mutfak/Kantin Tedarikçisi)
   *
   * <p>SERVICE - Industrial kitchen, hygiene products, catering
   */
  KITCHEN_SUPPLIER,

  /**
   * HR service provider (İK Hizmet Sağlayıcı)
   *
   * <p>SERVICE - Recruitment, payroll, training services
   */
  HR_SERVICE_PROVIDER,

  /**
   * Laboratory (Laboratuvar)
   *
   * <p>SERVICE - Testing, quality control, R&D services
   */
  LAB,

  /**
   * Utility provider (Altyapı Hizmet Sağlayıcı)
   *
   * <p>SERVICE - Electricity, water, natural gas
   */
  UTILITY_PROVIDER,

  // ========================================
  // PARTNER COMPANIES (Business Partners)
  // ========================================

  /**
   * Fason - Contract manufacturing (Fason Üretici)
   *
   * <p>PARTNER - Manufactures on behalf of other companies
   */
  FASON,

  /**
   * Agent (Aracı/Komisyoncu)
   *
   * <p>PARTNER - Representative, commission-based sales
   */
  AGENT,

  /**
   * Trader (Tüccar)
   *
   * <p>PARTNER - Buy-sell operations, no production
   */
  TRADER,

  /**
   * Finance partner (Finans Ortağı)
   *
   * <p>PARTNER - Bank, leasing company, insurance
   */
  FINANCE_PARTNER,

  // ========================================
  // CUSTOMER COMPANIES
  // ========================================

  /**
   * Customer (Müşteri)
   *
   * <p>CUSTOMER - Purchases finished products
   */
  CUSTOMER;

  /**
   * Check if this company type can be a platform tenant.
   *
   * @return true if can use platform as tenant
   */
  public boolean isTenant() {
    return switch (this) {
      case SPINNER, WEAVER, KNITTER, DYER_FINISHER, VERTICAL_MILL, GARMENT_MANUFACTURER -> true;
      default -> false;
    };
  }

  /**
   * Get company category for grouping.
   *
   * @return company category
   */
  public CompanyCategory getCategory() {
    return switch (this) {
      case SPINNER, WEAVER, KNITTER, DYER_FINISHER, VERTICAL_MILL, GARMENT_MANUFACTURER ->
          CompanyCategory.TENANT;
      case FIBER_SUPPLIER,
              YARN_SUPPLIER,
              CHEMICAL_SUPPLIER,
              CONSUMABLE_SUPPLIER,
              PACKAGING_SUPPLIER,
              MACHINE_SUPPLIER ->
          CompanyCategory.SUPPLIER;
      case LOGISTICS_PROVIDER,
              MAINTENANCE_SERVICE,
              IT_SERVICE_PROVIDER,
              KITCHEN_SUPPLIER,
              HR_SERVICE_PROVIDER,
              LAB,
              UTILITY_PROVIDER ->
          CompanyCategory.SERVICE_PROVIDER;
      case FASON, AGENT, TRADER, FINANCE_PARTNER -> CompanyCategory.PARTNER;
      case CUSTOMER -> CompanyCategory.CUSTOMER;
    };
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
