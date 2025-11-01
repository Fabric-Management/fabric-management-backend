package com.fabricmanagement.common.platform.company.dto;

import com.fabricmanagement.common.platform.company.domain.CompanyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Company Type DTO - For API responses.
 *
 * <p>Used to provide company type information to frontend,
 * especially for self-service signup forms.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyTypeDto {

    private String value; // Enum name (e.g., "SPINNER")
    private String label; // Display name in English (e.g., "Spinner")
    private String description; // Short description explaining what this type does
    private String category; // TENANT, SUPPLIER, etc.
    private boolean isTenant; // Whether this type can be a tenant
    private String[] suggestedOS; // Recommended OS for this type

    public static CompanyTypeDto from(CompanyType companyType) {
        return CompanyTypeDto.builder()
            .value(companyType.name())
            .label(getLabel(companyType))
            .description(getDescription(companyType))
            .category(companyType.getCategory().name())
            .isTenant(companyType.isTenant())
            .suggestedOS(companyType.getSuggestedOS())
            .build();
    }

    /**
     * Get English label for company type.
     */
    private static String getLabel(CompanyType type) {
        return switch (type) {
            case SPINNER -> "Spinner";
            case WEAVER -> "Weaver";
            case KNITTER -> "Knitter";
            case DYER_FINISHER -> "Dyer & Finisher";
            case VERTICAL_MILL -> "Vertical Mill";
            case GARMENT_MANUFACTURER -> "Garment Manufacturer";
            case FIBER_SUPPLIER -> "Fiber Supplier";
            case YARN_SUPPLIER -> "Yarn Supplier";
            case CHEMICAL_SUPPLIER -> "Chemical Supplier";
            case CONSUMABLE_SUPPLIER -> "Consumable Supplier";
            case PACKAGING_SUPPLIER -> "Packaging Supplier";
            case MACHINE_SUPPLIER -> "Machine Supplier";
            case LOGISTICS_PROVIDER -> "Logistics Provider";
            case MAINTENANCE_SERVICE -> "Maintenance Service";
            case IT_SERVICE_PROVIDER -> "IT Service Provider";
            case KITCHEN_SUPPLIER -> "Kitchen Supplier";
            case HR_SERVICE_PROVIDER -> "HR Service Provider";
            case LAB -> "Laboratory";
            case UTILITY_PROVIDER -> "Utility Provider";
            case FASON -> "Contract Manufacturer";
            case AGENT -> "Agent";
            case TRADER -> "Trader";
            case FINANCE_PARTNER -> "Finance Partner";
            case CUSTOMER -> "Customer";
        };
    }

    /**
     * Get short description explaining what this company type does.
     */
    private static String getDescription(CompanyType type) {
        return switch (type) {
            case SPINNER -> "Converts fiber into yarn (thread production)";
            case WEAVER -> "Produces woven fabric using warp and weft yarns";
            case KNITTER -> "Produces knitted fabric (jersey, rib, etc.)";
            case DYER_FINISHER -> "Dyeing and finishing processes for fabric";
            case VERTICAL_MILL -> "Complete production facility (fiber → yarn → fabric → dye)";
            case GARMENT_MANUFACTURER -> "Produces finished garments from fabric";
            case FIBER_SUPPLIER -> "Supplies raw materials (cotton, polyester, wool, etc.)";
            case YARN_SUPPLIER -> "Supplies various types of yarn";
            case CHEMICAL_SUPPLIER -> "Supplies dyes, auxiliaries, and chemicals";
            case CONSUMABLE_SUPPLIER -> "Supplies consumables (oil, needles, machine parts, etc.)";
            case PACKAGING_SUPPLIER -> "Supplies packaging materials (boxes, bags, labels)";
            case MACHINE_SUPPLIER -> "Supplies production machinery";
            case LOGISTICS_PROVIDER -> "Shipping, warehousing, and customs clearance services";
            case MAINTENANCE_SERVICE -> "Machine maintenance and technical support";
            case IT_SERVICE_PROVIDER -> "ERP, automation, software, and IT infrastructure services";
            case KITCHEN_SUPPLIER -> "Industrial kitchen, hygiene products, and catering services";
            case HR_SERVICE_PROVIDER -> "Recruitment, payroll, and training services";
            case LAB -> "Testing, quality control, and R&D services";
            case UTILITY_PROVIDER -> "Electricity, water, and natural gas services";
            case FASON -> "Contract manufacturing on behalf of other companies";
            case AGENT -> "Representative and commission-based sales";
            case TRADER -> "Buy-sell operations without production";
            case FINANCE_PARTNER -> "Banking, leasing, and insurance services";
            case CUSTOMER -> "Purchases finished textile products";
        };
    }
}

