package com.fabricmanagement.shared.domain.policy;

/**
 * Department Type Enum
 * 
 * Defines functional departments within a company.
 * Used only for INTERNAL users (external users don't have departments).
 * 
 * Usage:
 * - Determines user's functional area
 * - Affects dashboard routing (e.g., PRODUCTION → /production/weaving)
 * - Influences default permissions
 * 
 * Department Categories:
 * - Production: Manufacturing operations (PRODUCTION)
 * - Support: Quality, Warehouse (QUALITY, WAREHOUSE)
 * - Business: Finance, Sales, Purchasing (FINANCE, SALES, PURCHASING)
 * - Corporate: HR, IT, Management (HR, IT, MANAGEMENT)
 * 
 * Example:
 * - User: "Ahmet" (Dokumacı)
 * - UserContext: INTERNAL
 * - DepartmentType: PRODUCTION
 * - Dashboard: /production/weaving/machines
 * 
 * @see com.fabricmanagement.shared.domain.policy.UserContext
 */
public enum DepartmentType {
    
    /**
     * Production department
     * - Manufacturing operations (weaving, dyeing, cutting, sewing)
     * - Operates machines and production lines
     * - Dashboard: Production floor views
     */
    PRODUCTION("Production", "Üretim"),
    
    /**
     * Quality control department
     * - Inspection and quality assurance
     * - Product testing and certification
     * - Dashboard: Quality inspection views
     */
    QUALITY("Quality Control", "Kalite Kontrol"),
    
    /**
     * Warehouse department
     * - Inventory management
     * - Material receiving and shipping
     * - Dashboard: Warehouse inventory views
     */
    WAREHOUSE("Warehouse", "Depo"),
    
    /**
     * Finance/Accounting department
     * - Financial operations and accounting
     * - Invoicing and payment tracking
     * - Dashboard: Financial reports and accounting
     */
    FINANCE("Finance", "Muhasebe"),
    
    /**
     * Sales department
     * - Customer relations and sales
     * - Order management
     * - Dashboard: Sales pipeline and orders
     */
    SALES("Sales", "Satış"),
    
    /**
     * Purchasing department
     * - Supplier management and procurement
     * - Purchase order creation
     * - Dashboard: Procurement and supplier management
     */
    PURCHASING("Purchasing", "Satın Alma"),
    
    /**
     * Human Resources department
     * - Employee management
     * - Recruitment and training
     * - Dashboard: HR management views
     */
    HR("Human Resources", "İnsan Kaynakları"),
    
    /**
     * IT department
     * - System administration
     * - Technical support
     * - Dashboard: System administration views
     */
    IT("Information Technology", "Bilgi İşlem"),
    
    /**
     * Management/Executive
     * - Executive leadership
     * - Strategic planning
     * - Dashboard: Executive reports and analytics
     */
    MANAGEMENT("Management", "Yönetim");
    
    private final String displayLabel;
    private final String displayLabelTr;
    
    DepartmentType(String displayLabel, String displayLabelTr) {
        this.displayLabel = displayLabel;
        this.displayLabelTr = displayLabelTr;
    }
    
    /**
     * Gets the display label in English
     * 
     * @return English display label
     */
    public String getDisplayLabel() {
        return displayLabel;
    }
    
    /**
     * Gets the display label in Turkish
     * 
     * @return Turkish display label
     */
    public String getDisplayLabelTr() {
        return displayLabelTr;
    }
    
    /**
     * Gets the display label for current locale
     * 
     * @param locale "en" or "tr"
     * @return localized display label
     */
    public String getDisplayLabel(String locale) {
        return "tr".equalsIgnoreCase(locale) ? displayLabelTr : displayLabel;
    }
    
    /**
     * Checks if this is a production-related department
     * 
     * @return true if PRODUCTION type
     */
    public boolean isProduction() {
        return this == PRODUCTION;
    }
    
    /**
     * Checks if this is a business/office department
     * 
     * @return true if FINANCE, SALES, PURCHASING, HR, IT, or MANAGEMENT
     */
    public boolean isOffice() {
        return switch (this) {
            case FINANCE, SALES, PURCHASING, HR, IT, MANAGEMENT -> true;
            default -> false;
        };
    }
    
    /**
     * Checks if this is a support department
     * 
     * @return true if QUALITY or WAREHOUSE
     */
    public boolean isSupport() {
        return this == QUALITY || this == WAREHOUSE;
    }
    
    /**
     * Gets suggested dashboard path for this department
     * 
     * @return default dashboard route
     */
    public String getDefaultDashboardPath() {
        return switch (this) {
            case PRODUCTION -> "/production/dashboard";
            case QUALITY -> "/quality/inspections";
            case WAREHOUSE -> "/warehouse/inventory";
            case FINANCE -> "/finance/dashboard";
            case SALES -> "/sales/orders";
            case PURCHASING -> "/purchasing/suppliers";
            case HR -> "/hr/employees";
            case IT -> "/it/systems";
            case MANAGEMENT -> "/management/reports";
        };
    }
}

