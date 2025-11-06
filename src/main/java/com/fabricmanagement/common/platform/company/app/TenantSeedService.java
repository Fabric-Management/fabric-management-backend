package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.company.domain.Department;
import com.fabricmanagement.common.platform.company.domain.DepartmentCategory;
import com.fabricmanagement.common.platform.company.domain.Position;
import com.fabricmanagement.common.platform.company.infra.repository.DepartmentCategoryRepository;
import com.fabricmanagement.common.platform.company.infra.repository.DepartmentRepository;
import com.fabricmanagement.common.platform.company.infra.repository.PositionRepository;
import com.fabricmanagement.common.platform.user.domain.Role;
import com.fabricmanagement.common.platform.user.infra.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for seeding default departments and positions for new tenants.
 *
 * <p><b>Purpose:</b> Automatically creates standard departments and positions
 * when a new tenant is created, ensuring they have a complete organizational structure.</p>
 *
 * <p><b>Tenant Isolation:</b> All seeded data is tenant-specific and isolated.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSeedService {

    private final DepartmentCategoryRepository departmentCategoryRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final RoleRepository roleRepository;

    /**
     * Seed default departments and positions for a new tenant.
     *
     * <p><b>Process:</b></p>
     * <ol>
     *   <li>Get system-wide department categories (Production, Administrative, etc.)</li>
     *   <li>Get system-wide roles (ADMIN, MANAGER, etc.)</li>
     *   <li>Create departments for each category</li>
     *   <li>Create positions for each department</li>
     * </ol>
     *
     * <p><b>Idempotent:</b> If departments/positions already exist, they are skipped.</p>
     *
     * @param tenantId Tenant ID
     * @param companyId Company ID
     */
    @Transactional
    public void seedDepartmentsAndPositions(UUID tenantId, UUID companyId) {
        log.info("Seeding departments and positions for tenant: tenantId={}, companyId={}", tenantId, companyId);

        UUID originalTenantId = TenantContext.getCurrentTenantId();
        try {
            TenantContext.setCurrentTenantId(tenantId);

            // Get system-wide categories (from SYSTEM_TENANT_ID)
            Map<String, DepartmentCategory> categories = getSystemCategories();
            if (categories.isEmpty()) {
                log.warn("No system categories found. Cannot seed departments.");
                return;
            }

            // Get system-wide roles (from SYSTEM_TENANT_ID)
            Map<String, Role> roles = getSystemRoles();
            if (roles.isEmpty()) {
                log.warn("No system roles found. Cannot seed positions.");
                return;
            }

            // Seed departments and positions (idempotent - skips if already exists)
            seedProductionDepartments(companyId, categories, roles);
            seedAdministrativeDepartments(companyId, categories, roles);
            seedLogisticsDepartments(companyId, categories, roles);
            seedUtilityDepartments(companyId, categories, roles);

            log.info("✅ Seeded departments and positions for tenant: tenantId={}", tenantId);
        } finally {
            TenantContext.setCurrentTenantId(originalTenantId);
        }
    }

    /**
     * Check if tenant has been seeded (has departments).
     * 
     * @param tenantId Tenant ID
     * @param companyId Company ID
     * @return true if tenant has departments
     */
    @Transactional(readOnly = true)
    public boolean isTenantSeeded(UUID tenantId, UUID companyId) {
        UUID originalTenantId = TenantContext.getCurrentTenantId();
        try {
            TenantContext.setCurrentTenantId(tenantId);
            long departmentCount = departmentRepository.findByTenantIdAndCompanyIdAndIsActiveTrue(tenantId, companyId).size();
            return departmentCount > 0;
        } finally {
            TenantContext.setCurrentTenantId(originalTenantId);
        }
    }

    private Map<String, DepartmentCategory> getSystemCategories() {
        UUID systemTenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        List<DepartmentCategory> categories = departmentCategoryRepository.findByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(systemTenantId);
        return categories.stream()
            .collect(Collectors.toMap(
                DepartmentCategory::getCategoryName,
                cat -> cat,
                (existing, replacement) -> existing
            ));
    }

    private Map<String, Role> getSystemRoles() {
        UUID systemTenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        List<Role> roles = roleRepository.findByTenantIdAndIsActiveTrue(systemTenantId);
        return roles.stream()
            .collect(Collectors.toMap(
                Role::getRoleCode,
                role -> role,
                (existing, replacement) -> existing
            ));
    }

    private void seedProductionDepartments(UUID companyId, Map<String, DepartmentCategory> categories, Map<String, Role> roles) {
        DepartmentCategory productionCategory = categories.get("Production");
        if (productionCategory == null) {
            log.warn("Production category not found, skipping production departments");
            return;
        }

        // Fiber & Raw Material Department
        Department fiberDept = createDepartment(companyId, "Fiber & Raw Material",
            "Fiber procurement and raw material management", productionCategory.getId());
        createFiberPositions(fiberDept.getId(), roles);

        // Yarn Production Department
        Department yarnDept = createDepartment(companyId, "Yarn Production",
            "Yarn manufacturing operations", productionCategory.getId());
        createYarnPositions(yarnDept.getId(), roles);

        // Weaving & Knitting Department
        Department weavingDept = createDepartment(companyId, "Weaving & Knitting",
            "Fabric weaving and knitting operations", productionCategory.getId());
        createWeavingPositions(weavingDept.getId(), roles);

        // Dyeing & Finishing Department
        Department dyeingDept = createDepartment(companyId, "Dyeing & Finishing",
            "Fabric dyeing and finishing operations", productionCategory.getId());
        createDyeingPositions(dyeingDept.getId(), roles);

        // Quality Control Department
        Department qualityDept = createDepartment(companyId, "Quality Control",
            "Quality assurance and laboratory testing", productionCategory.getId());
        createQualityPositions(qualityDept.getId(), roles);
    }

    private void seedAdministrativeDepartments(UUID companyId, Map<String, DepartmentCategory> categories, Map<String, Role> roles) {
        DepartmentCategory adminCategory = categories.get("Administrative");
        if (adminCategory == null) {
            log.warn("Administrative category not found, skipping administrative departments");
            return;
        }

        // Management Department
        Department managementDept = createDepartment(companyId, "Management",
            "Executive management and planning", adminCategory.getId());
        createManagementPositions(managementDept.getId(), roles);

        // Sales Department
        Department salesDept = createDepartment(companyId, "Sales",
            "Sales and customer relations", adminCategory.getId());
        createSalesPositions(salesDept.getId(), roles);
    }

    private void seedLogisticsDepartments(UUID companyId, Map<String, DepartmentCategory> categories, Map<String, Role> roles) {
        DepartmentCategory logisticsCategory = categories.get("Logistics & Warehouse");
        if (logisticsCategory == null) {
            log.warn("Logistics & Warehouse category not found, skipping logistics departments");
            return;
        }

        // Warehouse & Logistics Department
        Department warehouseDept = createDepartment(companyId, "Warehouse & Logistics",
            "Warehouse management and logistics operations", logisticsCategory.getId());
        createWarehousePositions(warehouseDept.getId(), roles);
    }

    private void seedUtilityDepartments(UUID companyId, Map<String, DepartmentCategory> categories, Map<String, Role> roles) {
        DepartmentCategory utilityCategory = categories.get("Utility");
        if (utilityCategory == null) {
            log.warn("Utility category not found, skipping utility departments");
            return;
        }

        // Maintenance Department
        Department maintenanceDept = createDepartment(companyId, "Maintenance",
            "Equipment maintenance and repair", utilityCategory.getId());
        createMaintenancePositions(maintenanceDept.getId(), roles);

        // Energy & Facilities Department
        Department energyDept = createDepartment(companyId, "Energy & Facilities",
            "Energy generation and facility operations", utilityCategory.getId());
        createEnergyPositions(energyDept.getId(), roles);

        // Kitchen & Catering Department
        Department kitchenDept = createDepartment(companyId, "Kitchen & Catering",
            "Kitchen and cafeteria services", utilityCategory.getId());
        createKitchenPositions(kitchenDept.getId(), roles);
    }

    private Department createDepartment(UUID companyId, String name, String description, UUID categoryId) {
        if (departmentRepository.existsByTenantIdAndCompanyIdAndDepartmentName(
                TenantContext.getCurrentTenantId(), companyId, name)) {
            log.debug("Department already exists: {}", name);
            return departmentRepository.findByTenantIdAndCompanyIdAndDepartmentName(
                TenantContext.getCurrentTenantId(), companyId, name).orElseThrow();
        }

        String departmentCode = generateDepartmentCode(name);
        Department department = Department.create(companyId, name, departmentCode, description);
        department.setDepartmentCategory(
            departmentCategoryRepository.findById(categoryId).orElse(null));
        return departmentRepository.save(department);
    }

    private String generateDepartmentCode(String departmentName) {
        return departmentName.toUpperCase()
            .replaceAll("[^A-Z0-9]", "")
            .substring(0, Math.min(50, departmentName.replaceAll("[^A-Z0-9]", "").length()));
    }

    private Position createPosition(UUID departmentId, String name, String code, String description,
                                   Role defaultRole, Integer displayOrder) {
        if (positionRepository.existsByTenantIdAndPositionCode(
                TenantContext.getCurrentTenantId(), code)) {
            log.debug("Position already exists: {}", code);
            return positionRepository.findByTenantIdAndPositionCode(
                TenantContext.getCurrentTenantId(), code).orElse(null);
        }

        Position position = Position.create(departmentId, name, code, description);
        if (defaultRole != null) {
            position.setDefaultRole(defaultRole);
        }
        position.setDisplayOrder(displayOrder);
        return positionRepository.save(position);
    }

    // ========== Position Creation Methods ==========

    private void createFiberPositions(UUID departmentId, Map<String, Role> roles) {
        int order = 1;
        createPosition(departmentId, "Fiber Procurement Supervisor", "FIBER-SUPV",
            "Manages fiber procurement and raw material sourcing", roles.get("SUPERVISOR"), order++);
        createPosition(departmentId, "Raw Material Procurement Specialist", "RAW-PROC",
            "Handles raw material purchasing", roles.get("USER"), order++);
        createPosition(departmentId, "Fiber Warehouse Clerk", "FIBER-WHC",
            "Manages fiber warehouse operations", roles.get("USER"), order++);
        createPosition(departmentId, "Fiber Quality Controller", "FIBER-QC",
            "Performs quality control on fibers", roles.get("USER"), order++);
        createPosition(departmentId, "Fiber Preparation Operator", "FIBER-PREP",
            "Prepares fibers for processing", roles.get("USER"), order++);
        createPosition(departmentId, "Blending Operator", "FIBER-BLEND",
            "Operates fiber blending equipment", roles.get("USER"), order++);
    }

    private void createYarnPositions(UUID departmentId, Map<String, Role> roles) {
        int order = 1;
        createPosition(departmentId, "Yarn Production Manager", "YARN-MGR",
            "Manages yarn production department", roles.get("MANAGER"), order++);
        createPosition(departmentId, "Yarn Production Supervisor", "YARN-SUPV",
            "Supervises yarn production operations", roles.get("SUPERVISOR"), order++);
        createPosition(departmentId, "Ring Operator", "YARN-RING",
            "Operates ring spinning machines", roles.get("USER"), order++);
        createPosition(departmentId, "Open-End Operator", "YARN-OE",
            "Operates open-end spinning machines", roles.get("USER"), order++);
        createPosition(departmentId, "Compact Operator", "YARN-COMP",
            "Operates compact spinning machines", roles.get("USER"), order++);
        createPosition(departmentId, "Machine Setter", "YARN-SET",
            "Sets up and adjusts spinning machines", roles.get("USER"), order++);
        createPosition(departmentId, "Yarn Quality Controller", "YARN-QC",
            "Performs quality control on yarn", roles.get("USER"), order++);
        createPosition(departmentId, "Yarn Shift Supervisor", "YARN-SHIFT",
            "Supervises yarn production shifts", roles.get("SUPERVISOR"), order++);
        createPosition(departmentId, "Yarn Maintenance Technician", "YARN-MAINT",
            "Maintains yarn production equipment", roles.get("USER"), order++);
    }

    private void createWeavingPositions(UUID departmentId, Map<String, Role> roles) {
        int order = 1;
        createPosition(departmentId, "Weaving Manager", "WEAV-MGR",
            "Manages weaving and knitting department", roles.get("MANAGER"), order++);
        createPosition(departmentId, "Weaving Supervisor", "WEAV-SUPV",
            "Supervises weaving operations", roles.get("SUPERVISOR"), order++);
        createPosition(departmentId, "Weaving Planning Specialist", "WEAV-PLAN",
            "Plans weaving operations", roles.get("USER"), order++);
        createPosition(departmentId, "Weaving Machine Operator", "WEAV-OP",
            "Operates weaving machines", roles.get("USER"), order++);
        createPosition(departmentId, "Warping Operator", "WEAV-WARP",
            "Operates warping machines", roles.get("USER"), order++);
        createPosition(departmentId, "Sizing Operator", "WEAV-SIZE",
            "Operates sizing machines", roles.get("USER"), order++);
        createPosition(departmentId, "Knitting Operator", "WEAV-KNIT",
            "Operates knitting machines", roles.get("USER"), order++);
        createPosition(departmentId, "Weaving Quality Controller", "WEAV-QC",
            "Performs quality control on woven fabrics", roles.get("USER"), order++);
        createPosition(departmentId, "Weaving Shift Supervisor", "WEAV-SHIFT",
            "Supervises weaving shifts", roles.get("SUPERVISOR"), order++);
        createPosition(departmentId, "Weaving Maintenance Technician", "WEAV-MAINT",
            "Maintains weaving equipment", roles.get("USER"), order++);
    }

    private void createDyeingPositions(UUID departmentId, Map<String, Role> roles) {
        int order = 1;
        createPosition(departmentId, "Dyeing Manager", "DYE-MGR",
            "Manages dyeing operations", roles.get("MANAGER"), order++);
        createPosition(departmentId, "Dyeing Supervisor", "DYE-SUPV",
            "Supervises dyeing operations", roles.get("SUPERVISOR"), order++);
        createPosition(departmentId, "Dyeing Operator", "DYE-OP",
            "Operates dyeing machines", roles.get("USER"), order++);
        createPosition(departmentId, "Chemical Preparation Supervisor", "DYE-CHEM",
            "Manages chemical preparation", roles.get("SUPERVISOR"), order++);
        createPosition(departmentId, "Color Lab Technician", "DYE-LAB",
            "Performs color matching and testing", roles.get("USER"), order++);
        createPosition(departmentId, "Dyeing Quality Controller", "DYE-QC",
            "Performs quality control on dyed fabrics", roles.get("USER"), order++);
        createPosition(departmentId, "Finishing Manager", "FIN-MGR",
            "Manages finishing operations", roles.get("MANAGER"), order++);
        createPosition(departmentId, "Finishing Operator", "FIN-OP",
            "Operates finishing machines", roles.get("USER"), order++);
        createPosition(departmentId, "Drying Machine Operator", "FIN-DRY",
            "Operates drying machines", roles.get("USER"), order++);
        createPosition(departmentId, "Finishing Quality Controller", "FIN-QC",
            "Performs quality control on finished fabrics", roles.get("USER"), order++);
        createPosition(departmentId, "Energy Operator", "DYE-ENERGY",
            "Manages energy systems (steam/water/electricity)", roles.get("USER"), order++);
        createPosition(departmentId, "Dyeing Maintenance Technician", "DYE-MAINT",
            "Maintains dyeing and finishing equipment", roles.get("USER"), order++);
    }

    private void createQualityPositions(UUID departmentId, Map<String, Role> roles) {
        int order = 1;
        createPosition(departmentId, "Quality Manager", "QC-MGR",
            "Manages quality assurance department", roles.get("MANAGER"), order++);
        createPosition(departmentId, "Quality Assurance Specialist", "QC-QA",
            "Performs quality assurance activities", roles.get("USER"), order++);
        createPosition(departmentId, "Laboratory Supervisor", "QC-LAB",
            "Supervises laboratory operations", roles.get("SUPERVISOR"), order++);
        createPosition(departmentId, "Test Specialist", "QC-TEST",
            "Performs material testing", roles.get("USER"), order++);
        createPosition(departmentId, "Sample Analysis Specialist", "QC-SAMPLE",
            "Analyzes product samples", roles.get("USER"), order++);
        createPosition(departmentId, "Measurement & Reporting Specialist", "QC-REPORT",
            "Handles measurements and reporting", roles.get("USER"), order++);
    }

    private void createManagementPositions(UUID departmentId, Map<String, Role> roles) {
        int order = 1;
        createPosition(departmentId, "Production Director", "MGT-PROD-DIR",
            "Directs production operations", roles.get("DIRECTOR"), order++);
        createPosition(departmentId, "Production Planning Director", "MGT-PLAN-DIR",
            "Directs production planning", roles.get("DIRECTOR"), order++);
        createPosition(departmentId, "Factory Director", "MGT-FACT-DIR",
            "Directs factory operations", roles.get("DIRECTOR"), order++);
        createPosition(departmentId, "Shift Manager", "MGT-SHIFT-MGR",
            "Manages production shifts", roles.get("MANAGER"), order++);
        createPosition(departmentId, "Production Planning Specialist", "MGT-PLAN",
            "Performs production planning", roles.get("USER"), order++);
        createPosition(departmentId, "Process Improvement Specialist", "MGT-IMPROVE",
            "Improves production processes", roles.get("USER"), order++);
    }

    private void createSalesPositions(UUID departmentId, Map<String, Role> roles) {
        int order = 1;
        createPosition(departmentId, "Sales Manager", "SALES-MGR",
            "Manages sales department", roles.get("MANAGER"), order++);
        createPosition(departmentId, "Sales Representative", "SALES-REP",
            "Handles customer sales", roles.get("USER"), order++);
        createPosition(departmentId, "Customer Relations Specialist", "SALES-CRM",
            "Manages customer relationships", roles.get("USER"), order++);
        createPosition(departmentId, "Sample Tracking Supervisor", "SALES-SAMPLE",
            "Tracks customer samples", roles.get("USER"), order++);
        createPosition(departmentId, "International Trade Specialist", "SALES-INTL",
            "Handles international trade", roles.get("USER"), order++);
        createPosition(departmentId, "Order Tracking Specialist", "SALES-ORDER",
            "Tracks customer orders", roles.get("USER"), order++);
    }

    private void createWarehousePositions(UUID departmentId, Map<String, Role> roles) {
        int order = 1;
        createPosition(departmentId, "Warehouse Manager", "WH-MGR",
            "Manages warehouse operations", roles.get("MANAGER"), order++);
        createPosition(departmentId, "Warehouse Supervisor", "WH-SUPV",
            "Supervises warehouse operations", roles.get("SUPERVISOR"), order++);
        createPosition(departmentId, "Forklift Operator", "WH-FORK",
            "Operates forklift equipment", roles.get("USER"), order++);
        createPosition(departmentId, "Receiving Clerk", "WH-REC",
            "Handles material receiving", roles.get("USER"), order++);
        createPosition(departmentId, "Inventory Specialist", "WH-INV",
            "Manages inventory tracking", roles.get("USER"), order++);
        createPosition(departmentId, "Shipping Supervisor", "WH-SHIP",
            "Supervises shipping operations", roles.get("SUPERVISOR"), order++);
        createPosition(departmentId, "Logistics Planning Specialist", "WH-LOG",
            "Plans logistics operations", roles.get("USER"), order++);
        createPosition(departmentId, "Delivery Driver", "WH-DRIVER",
            "Drives delivery vehicles", roles.get("USER"), order++);
    }

    private void createMaintenancePositions(UUID departmentId, Map<String, Role> roles) {
        int order = 1;
        createPosition(departmentId, "Maintenance Manager", "MAINT-MGR",
            "Manages maintenance operations", roles.get("MANAGER"), order++);
        createPosition(departmentId, "Mechanical Technician", "MAINT-MECH",
            "Performs mechanical maintenance", roles.get("USER"), order++);
        createPosition(departmentId, "Electrical Technician", "MAINT-ELEC",
            "Performs electrical maintenance", roles.get("USER"), order++);
        createPosition(departmentId, "Automation Technician", "MAINT-AUTO",
            "Maintains automation systems", roles.get("USER"), order++);
        createPosition(departmentId, "Technical Service Technician", "MAINT-SVC",
            "Provides technical service", roles.get("USER"), order++);
    }

    private void createEnergyPositions(UUID departmentId, Map<String, Role> roles) {
        int order = 1;
        createPosition(departmentId, "Energy Facility Operator", "ENERGY-OP",
            "Operates energy facilities", roles.get("USER"), order++);
        createPosition(departmentId, "Steam Boiler Operator", "ENERGY-STEAM",
            "Operates steam boilers", roles.get("USER"), order++);
        createPosition(departmentId, "Water Treatment Operator", "ENERGY-WATER",
            "Operates water treatment systems", roles.get("USER"), order++);
        createPosition(departmentId, "Compressor Operator", "ENERGY-COMP",
            "Operates compressors", roles.get("USER"), order++);
        createPosition(departmentId, "HVAC Technician", "ENERGY-HVAC",
            "Maintains HVAC systems", roles.get("USER"), order++);
        createPosition(departmentId, "Generator Operator", "ENERGY-GEN",
            "Operates generators", roles.get("USER"), order++);
        createPosition(departmentId, "Wastewater Treatment Operator", "ENERGY-WASTE",
            "Operates wastewater treatment systems", roles.get("USER"), order++);
    }

    private void createKitchenPositions(UUID departmentId, Map<String, Role> roles) {
        int order = 1;
        createPosition(departmentId, "Kitchen Manager", "KIT-MGR",
            "Manages kitchen operations", roles.get("MANAGER"), order++);
        createPosition(departmentId, "Head Chef", "KIT-CHEF",
            "Leads kitchen operations", roles.get("SUPERVISOR"), order++);
        createPosition(departmentId, "Cook", "KIT-COOK",
            "Prepares meals", roles.get("USER"), order++);
        createPosition(departmentId, "Assistant Cook", "KIT-ASST",
            "Assists in meal preparation", roles.get("USER"), order++);
        createPosition(departmentId, "Breakfast Preparation Clerk", "KIT-BREAK",
            "Prepares breakfast items", roles.get("USER"), order++);
        createPosition(departmentId, "Baker", "KIT-BAKE",
            "Prepares baked goods", roles.get("USER"), order++);
        createPosition(departmentId, "Grill Cook", "KIT-GRILL",
            "Operates grill equipment", roles.get("USER"), order++);
        createPosition(departmentId, "Salad & Cold Meze Specialist", "KIT-SALAD",
            "Prepares salads and cold meze", roles.get("USER"), order++);
        createPosition(departmentId, "Pastry Chef", "KIT-PAST",
            "Prepares desserts and pastries", roles.get("USER"), order++);
        createPosition(departmentId, "Dishwasher", "KIT-WASH",
            "Cleans dishes and utensils", roles.get("USER"), order++);
        createPosition(departmentId, "Service Staff", "KIT-SVC",
            "Serves meals", roles.get("USER"), order++);
        createPosition(departmentId, "Cafeteria Supervisor", "KIT-CAFE",
            "Supervises cafeteria operations", roles.get("SUPERVISOR"), order++);
        createPosition(departmentId, "Cafeteria Clerk", "KIT-CAFE-CLK",
            "Manages cafeteria", roles.get("USER"), order++);
        createPosition(departmentId, "Food Warehouse Supervisor", "KIT-WH",
            "Manages food warehouse", roles.get("SUPERVISOR"), order++);
        createPosition(departmentId, "Food Hygiene Controller", "KIT-HYGIENE",
            "Controls food hygiene", roles.get("USER"), order++);
        createPosition(departmentId, "Food Distribution Staff", "KIT-DIST",
            "Distributes meals", roles.get("USER"), order++);
        createPosition(departmentId, "Tea & Beverage Server", "KIT-TEA",
            "Serves tea and beverages", roles.get("USER"), order++);
    }
}

