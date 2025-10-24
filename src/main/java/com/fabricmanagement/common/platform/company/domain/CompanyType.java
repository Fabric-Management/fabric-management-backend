package com.fabricmanagement.common.platform.company.domain;

/**
 * Company type classification in the fabric management ecosystem.
 *
 * <p>Comprehensive classification covering all company types in textile industry.
 * Categories: TENANT (platform users), SUPPLIER, SERVICE_PROVIDER, PARTNER, CUSTOMER</p>
 *
 * <h2>Usage:</h2>
 * <ul>
 *   <li>Multi-tenant isolation (tenant companies)</li>
 *   <li>Business relationship management (suppliers, partners)</li>
 *   <li>Access control policies</li>
 *   <li>Reporting and analytics</li>
 * </ul>
 */
public enum CompanyType {

    // ========================================
    // TENANT COMPANIES (Platform Users)
    // ========================================

    /**
     * Yarn producer (İplikçi)
     * <p>TENANT - Converts fiber to yarn</p>
     * <p>OS: SpinnerOS, YarnOS</p>
     */
    SPINNER,

    /**
     * Weaving producer (Dokumacı)
     * <p>TENANT - Produces woven fabric (warp & weft)</p>
     * <p>OS: WeaverOS, LoomOS</p>
     */
    WEAVER,

    /**
     * Knitting producer (Örücü)
     * <p>TENANT - Produces knitted fabric (jersey, rib, etc.)</p>
     * <p>OS: KnitterOS</p>
     */
    KNITTER,

    /**
     * Dyeing & Finishing plant (Boyahane/Terbiye)
     * <p>TENANT - Dyeing and finishing processes</p>
     * <p>OS: DyeOS, FinishOS</p>
     */
    DYER_FINISHER,

    /**
     * Vertical integrated mill (Entegre Tesis)
     * <p>TENANT - Complete production: fiber → yarn → fabric → dye</p>
     * <p>OS: FabricOS (all modules)</p>
     */
    VERTICAL_MILL,

    /**
     * Garment manufacturer (Konfeksiyon)
     * <p>TENANT - Produces finished garments from fabric</p>
     * <p>OS: GarmentOS</p>
     */
    GARMENT_MANUFACTURER,

    // ========================================
    // SUPPLIER COMPANIES (Material Suppliers)
    // ========================================

    /**
     * Fiber supplier (Elyaf Tedarikçisi)
     * <p>SUPPLIER - Supplies cotton, polyester, wool, etc.</p>
     */
    FIBER_SUPPLIER,

    /**
     * Yarn supplier (İplik Tedarikçisi)
     * <p>SUPPLIER - Supplies various yarn types</p>
     */
    YARN_SUPPLIER,

    /**
     * Chemical supplier (Kimyasal Tedarikçisi)
     * <p>SUPPLIER - Dyes, auxiliaries, chemicals</p>
     */
    CHEMICAL_SUPPLIER,

    /**
     * Consumable supplier (Sarf Malzeme Tedarikçisi)
     * <p>SUPPLIER - Oil, needles, machine parts, cleaning products</p>
     */
    CONSUMABLE_SUPPLIER,

    /**
     * Packaging supplier (Ambalaj Tedarikçisi)
     * <p>SUPPLIER - Boxes, bags, labels, packaging materials</p>
     */
    PACKAGING_SUPPLIER,

    /**
     * Machine supplier (Makine Tedarikçisi)
     * <p>SUPPLIER - Weaving, knitting, dyeing machines</p>
     * <p>Examples: Dornier, Monforts, Mayer</p>
     */
    MACHINE_SUPPLIER,

    // ========================================
    // SERVICE PROVIDER COMPANIES
    // ========================================

    /**
     * Logistics provider (Lojistik Sağlayıcı)
     * <p>SERVICE - Shipping, warehousing, customs clearance</p>
     */
    LOGISTICS_PROVIDER,

    /**
     * Maintenance service (Bakım Servisi)
     * <p>SERVICE - Machine maintenance, technical support, spare parts</p>
     */
    MAINTENANCE_SERVICE,

    /**
     * IT service provider (IT Hizmet Sağlayıcı)
     * <p>SERVICE - ERP, automation, software, network infrastructure</p>
     */
    IT_SERVICE_PROVIDER,

    /**
     * Kitchen/Canteen supplier (Mutfak/Kantin Tedarikçisi)
     * <p>SERVICE - Industrial kitchen, hygiene products, catering</p>
     */
    KITCHEN_SUPPLIER,

    /**
     * HR service provider (İK Hizmet Sağlayıcı)
     * <p>SERVICE - Recruitment, payroll, training services</p>
     */
    HR_SERVICE_PROVIDER,

    /**
     * Laboratory (Laboratuvar)
     * <p>SERVICE - Testing, quality control, R&D services</p>
     */
    LAB,

    /**
     * Utility provider (Altyapı Hizmet Sağlayıcı)
     * <p>SERVICE - Electricity, water, natural gas</p>
     */
    UTILITY_PROVIDER,

    // ========================================
    // PARTNER COMPANIES (Business Partners)
    // ========================================

    /**
     * Fason - Contract manufacturing (Fason Üretici)
     * <p>PARTNER - Manufactures on behalf of other companies</p>
     */
    FASON,

    /**
     * Agent (Aracı/Komisyoncu)
     * <p>PARTNER - Representative, commission-based sales</p>
     */
    AGENT,

    /**
     * Trader (Tüccar)
     * <p>PARTNER - Buy-sell operations, no production</p>
     */
    TRADER,

    /**
     * Finance partner (Finans Ortağı)
     * <p>PARTNER - Bank, leasing company, insurance</p>
     */
    FINANCE_PARTNER,

    // ========================================
    // CUSTOMER COMPANIES
    // ========================================

    /**
     * Customer (Müşteri)
     * <p>CUSTOMER - Purchases finished products</p>
     */
    CUSTOMER;

    /**
     * Check if this company type can be a platform tenant.
     * 
     * @return true if can use platform as tenant
     */
    public boolean isTenant() {
        return switch (this) {
            case SPINNER, WEAVER, KNITTER, DYER_FINISHER, 
                 VERTICAL_MILL, GARMENT_MANUFACTURER -> true;
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
            case SPINNER, WEAVER, KNITTER, DYER_FINISHER, 
                 VERTICAL_MILL, GARMENT_MANUFACTURER -> CompanyCategory.TENANT;
            case FIBER_SUPPLIER, YARN_SUPPLIER, CHEMICAL_SUPPLIER, 
                 CONSUMABLE_SUPPLIER, PACKAGING_SUPPLIER, MACHINE_SUPPLIER -> CompanyCategory.SUPPLIER;
            case LOGISTICS_PROVIDER, MAINTENANCE_SERVICE, IT_SERVICE_PROVIDER, 
                 KITCHEN_SUPPLIER, HR_SERVICE_PROVIDER, LAB, UTILITY_PROVIDER -> CompanyCategory.SERVICE_PROVIDER;
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
            case SPINNER -> new String[]{"SpinnerOS", "YarnOS"};
            case WEAVER -> new String[]{"WeaverOS", "LoomOS"};
            case KNITTER -> new String[]{"KnitterOS", "KnitOS"};
            case DYER_FINISHER -> new String[]{"DyeOS", "FinishOS"};
            case VERTICAL_MILL -> new String[]{"FabricOS"}; // Complete package
            case GARMENT_MANUFACTURER -> new String[]{"GarmentOS"};
            default -> new String[]{};
        };
    }
}


