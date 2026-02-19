package com.fabricmanagement.common.platform.subscription.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.organization.domain.Department;
import com.fabricmanagement.common.platform.organization.domain.DepartmentCategory;
import com.fabricmanagement.common.platform.organization.domain.Position;
import com.fabricmanagement.common.platform.organization.infra.repository.DepartmentCategoryRepository;
import com.fabricmanagement.common.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.common.platform.organization.infra.repository.PositionRepository;
import com.fabricmanagement.common.platform.user.domain.Role;
import com.fabricmanagement.common.platform.user.infra.repository.RoleRepository;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for seeding default departments and positions for new tenants.
 *
 * <p><b>Purpose:</b> Automatically creates standard departments and positions when a new tenant is
 * created, ensuring they have a complete organizational structure.
 *
 * <p><b>Tenant Isolation:</b> All seeded data is tenant-specific and isolated.
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
   * <p><b>Process:</b>
   *
   * <ol>
   *   <li>Get system-wide department categories (Production, Administrative, etc.)
   *   <li>Get system-wide roles (ADMIN, MANAGER, etc.)
   *   <li>Create departments for each category
   *   <li>Create positions for each department
   * </ol>
   *
   * <p><b>Idempotent:</b> If departments/positions already exist, they are skipped.
   *
   * @param tenantId Tenant ID
   * @param companyId Company ID
   */
  @Transactional
  public void seedDepartmentsAndPositions(UUID tenantId, UUID companyId) {
    log.info(
        "Seeding departments and positions for tenant: tenantId={}, companyId={}",
        tenantId,
        companyId);

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
      seedSupportDepartments(companyId, categories, roles);

      log.info("✅ Seeded departments and positions for tenant: tenantId={}", tenantId);
    } finally {
      TenantContext.setCurrentTenantId(originalTenantId);
    }
  }

  /**
   * Check if tenant has been seeded (has departments).
   *
   * @param tenantId Tenant ID
   * @param companyId Organization ID
   * @return true if tenant has departments
   */
  @Transactional(readOnly = true)
  public boolean isTenantSeeded(UUID tenantId, UUID companyId) {
    UUID originalTenantId = TenantContext.getCurrentTenantId();
    try {
      TenantContext.setCurrentTenantId(tenantId);
      long departmentCount =
          departmentRepository
              .findByTenantIdAndOrganizationIdAndIsActiveTrue(tenantId, companyId)
              .size();
      return departmentCount > 0;
    } finally {
      TenantContext.setCurrentTenantId(originalTenantId);
    }
  }

  private Map<String, DepartmentCategory> getSystemCategories() {
    UUID systemTenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");
    List<DepartmentCategory> categories =
        departmentCategoryRepository.findByTenantIdAndIsActiveTrueOrderByDisplayOrderAsc(
            systemTenantId);
    return categories.stream()
        .collect(
            Collectors.toMap(
                DepartmentCategory::getCategoryName,
                cat -> cat,
                (existing, replacement) -> existing));
  }

  private Map<String, Role> getSystemRoles() {
    UUID systemTenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");
    List<Role> roles = roleRepository.findByTenantIdAndIsActiveTrue(systemTenantId);
    return roles.stream()
        .collect(
            Collectors.toMap(Role::getRoleCode, role -> role, (existing, replacement) -> existing));
  }

  private void seedProductionDepartments(
      UUID companyId, Map<String, DepartmentCategory> categories, Map<String, Role> roles) {
    DepartmentCategory productionCategory = categories.get("Production");
    if (productionCategory == null) {
      log.warn("Production category not found, skipping production departments");
      return;
    }

    // Fiber & Raw Material Department
    Department fiberDept =
        createDepartment(
            companyId,
            "Fiber & Raw Material",
            "Fiber procurement and raw material management",
            productionCategory.getId());
    createFiberPositions(fiberDept.getId(), roles);

    // Yarn Production Department
    Department yarnDept =
        createDepartment(
            companyId,
            "Yarn Production",
            "Yarn manufacturing operations",
            productionCategory.getId());
    createYarnPositions(yarnDept.getId(), roles);

    // Weaving & Knitting Department
    Department weavingDept =
        createDepartment(
            companyId,
            "Weaving & Knitting",
            "Fabric weaving and knitting operations",
            productionCategory.getId());
    createWeavingPositions(weavingDept.getId(), roles);

    // Dyeing & Finishing Department
    Department dyeingDept =
        createDepartment(
            companyId,
            "Dyeing & Finishing",
            "Fabric dyeing and finishing operations",
            productionCategory.getId());
    createDyeingPositions(dyeingDept.getId(), roles);

    // Quality Control Department
    Department qualityDept =
        createDepartment(
            companyId,
            "Quality Control",
            "Quality assurance and laboratory testing",
            productionCategory.getId());
    createQualityPositions(qualityDept.getId(), roles);
  }

  private void seedAdministrativeDepartments(
      UUID companyId, Map<String, DepartmentCategory> categories, Map<String, Role> roles) {
    DepartmentCategory adminCategory = categories.get("Administration");
    if (adminCategory == null) {
      log.warn("Administration category not found, skipping administrative departments");
      return;
    }

    // Human Resources Department
    Department hrDept =
        createDepartment(
            companyId, "Human Resources", "Human resources management", adminCategory.getId());
    createHRPositions(hrDept.getId(), roles);

    // Finance & Accounting Department
    Department financeDept =
        createDepartment(
            companyId,
            "Finance & Accounting",
            "Financial management and accounting",
            adminCategory.getId());
    createFinancePositions(financeDept.getId(), roles);

    // Administration Office Department
    Department adminOfficeDept =
        createDepartment(
            companyId,
            "Administration Office",
            "General administration and office management",
            adminCategory.getId());
    createAdminOfficePositions(adminOfficeDept.getId(), roles);

    // Management & Planning Department
    Department mgmtDept =
        createDepartment(
            companyId,
            "Management & Planning",
            "Executive management and strategic planning",
            adminCategory.getId());
    createManagementPositions(mgmtDept.getId(), roles);
  }

  private void seedLogisticsDepartments(
      UUID companyId, Map<String, DepartmentCategory> categories, Map<String, Role> roles) {
    DepartmentCategory logisticsCategory = categories.get("Logistics");
    if (logisticsCategory == null) {
      log.warn("Logistics category not found, skipping logistics departments");
      return;
    }

    // Warehouse Department
    Department warehouseDept =
        createDepartment(
            companyId, "Warehouse", "Warehouse management and storage", logisticsCategory.getId());
    createWarehousePositions(warehouseDept.getId(), roles);

    // Procurement & Supply Department
    Department procurementDept =
        createDepartment(
            companyId,
            "Procurement & Supply",
            "Procurement and supply chain management",
            logisticsCategory.getId());
    createProcurementPositions(procurementDept.getId(), roles);

    // Shipping & Transport Department
    Department shippingDept =
        createDepartment(
            companyId,
            "Shipping & Transport",
            "Shipping and transportation management",
            logisticsCategory.getId());
    createShippingPositions(shippingDept.getId(), roles);
  }

  private void seedUtilityDepartments(
      UUID companyId, Map<String, DepartmentCategory> categories, Map<String, Role> roles) {
    DepartmentCategory utilityCategory = categories.get("Utility");
    if (utilityCategory == null) {
      log.warn("Utility category not found, skipping utility departments");
      return;
    }

    // Maintenance Department
    Department maintenanceDept =
        createDepartment(
            companyId, "Maintenance", "Equipment maintenance and repair", utilityCategory.getId());
    createMaintenancePositions(maintenanceDept.getId(), roles);

    // Energy & Facilities Department
    Department energyDept =
        createDepartment(
            companyId,
            "Energy & Facilities",
            "Energy generation and facility operations",
            utilityCategory.getId());
    createEnergyPositions(energyDept.getId(), roles);

    // Kitchen & Catering Department
    Department kitchenDept =
        createDepartment(
            companyId,
            "Kitchen & Catering",
            "Kitchen and cafeteria services",
            utilityCategory.getId());
    createKitchenPositions(kitchenDept.getId(), roles);
  }

  private Department createDepartment(
      UUID companyId, String name, String description, UUID categoryId) {
    if (departmentRepository.existsByTenantIdAndOrganizationIdAndDepartmentName(
        TenantContext.getCurrentTenantId(), companyId, name)) {
      log.debug("Department already exists: {}", name);
      return departmentRepository
          .findByTenantIdAndOrganizationIdAndDepartmentName(
              TenantContext.getCurrentTenantId(), companyId, name)
          .orElseThrow();
    }

    String departmentCode = generateDepartmentCode(name);
    Department department = Department.create(companyId, name, departmentCode, description);
    department.setDepartmentCategory(
        departmentCategoryRepository.findById(categoryId).orElse(null));
    return departmentRepository.save(department);
  }

  private String generateDepartmentCode(String departmentName) {
    String code = departmentName.toUpperCase().replaceAll("[^A-Z0-9]", "");
    return code.substring(0, Math.min(50, code.length()));
  }

  private Position createPosition(
      UUID departmentId,
      String name,
      String code,
      String description,
      Role defaultRole,
      Integer displayOrder) {
    if (positionRepository.existsByTenantIdAndPositionCode(
        TenantContext.getCurrentTenantId(), code)) {
      log.debug("Position already exists: {}", code);
      return positionRepository
          .findByTenantIdAndPositionCode(TenantContext.getCurrentTenantId(), code)
          .orElse(null);
    }

    Position position = Position.create(departmentId, name, code, description);
    if (defaultRole != null) {
      position.setDefaultRole(defaultRole);
    }
    position.setDisplayOrder(displayOrder);
    return positionRepository.save(position);
  }

  // ========== Position Creation Methods ==========

  private void createYarnPositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "Yarn Production Manager",
        "YARN-MGR",
        "Manages yarn production department",
        roles.get("PROD_MANAGER"),
        order++);
    createPosition(
        departmentId,
        "Spinning Operator",
        "YARN-SPIN",
        "Operates spinning machines",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Bobbin Winder",
        "YARN-WIND",
        "Winds yarn onto bobbins",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Yarn Quality Controller",
        "YARN-QC",
        "Performs quality control on yarn",
        roles.get("QC"),
        order++);
  }

  private void createWeavingPositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "Weaving Manager",
        "WEAV-MGR",
        "Manages weaving and knitting department",
        roles.get("PROD_MANAGER"),
        order++);
    createPosition(
        departmentId,
        "Loom Operator",
        "WEAV-LOOM",
        "Operates loom machines",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Knitting Machine Operator",
        "WEAV-KNIT",
        "Operates knitting machines",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Weaving Maintenance Technician",
        "WEAV-MAINT",
        "Maintains weaving equipment",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Weaving Quality Controller",
        "WEAV-QC",
        "Performs quality control on woven fabrics",
        roles.get("QC"),
        order++);
  }

  private void createDyeingPositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "Dyeing Manager",
        "DYE-MGR",
        "Manages dyeing operations",
        roles.get("PROD_MANAGER"),
        order++);
    createPosition(
        departmentId,
        "Dyeing Supervisor",
        "DYE-SUPV",
        "Supervises dyeing operations",
        roles.get("PROD_MANAGER"),
        order++);
    createPosition(
        departmentId,
        "Dyeing Operator",
        "DYE-OP",
        "Operates dyeing machines",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Chemical Preparation Supervisor",
        "DYE-CHEM",
        "Manages chemical preparation",
        roles.get("PROD_MANAGER"),
        order++);
    createPosition(
        departmentId,
        "Color Lab Technician",
        "DYE-LAB",
        "Performs color matching and testing",
        roles.get("QC"),
        order++);
    createPosition(
        departmentId,
        "Finishing Manager",
        "FIN-MGR",
        "Manages finishing operations",
        roles.get("PROD_MANAGER"),
        order++);
    createPosition(
        departmentId,
        "Finishing Operator",
        "FIN-OP",
        "Operates finishing machines",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Finishing Quality Controller",
        "FIN-QC",
        "Performs quality control on finished fabrics",
        roles.get("QC"),
        order++);
  }

  private void createQualityPositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "Quality Manager",
        "QC-MGR",
        "Manages quality assurance department",
        roles.get("PROD_MANAGER"),
        order++);
    createPosition(
        departmentId,
        "Laboratory Supervisor",
        "QC-LAB",
        "Supervises laboratory operations",
        roles.get("QC"),
        order++);
    createPosition(
        departmentId,
        "Quality Assurance Specialist",
        "QC-QA",
        "Performs quality assurance activities",
        roles.get("QC"),
        order++);
    createPosition(
        departmentId,
        "Test Specialist",
        "QC-TEST",
        "Performs material testing",
        roles.get("QC"),
        order++);
    createPosition(
        departmentId,
        "Measurement & Reporting Specialist",
        "QC-REPORT",
        "Handles measurements and reporting",
        roles.get("QC"),
        order++);
  }

  private void createWarehousePositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "Warehouse Manager",
        "WH-MGR",
        "Manages warehouse operations",
        roles.get("LOG_MANAGER"),
        order++);
    createPosition(
        departmentId,
        "Warehouse Supervisor",
        "WH-SUPV",
        "Supervises warehouse operations",
        roles.get("LOG_MANAGER"),
        order++);
    createPosition(
        departmentId,
        "Warehouse Worker",
        "WH-WORKER",
        "Performs warehouse tasks",
        roles.get("WAREHOUSE_WORKER"),
        order++);
  }

  private void createMaintenancePositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "Maintenance Manager",
        "MAINT-MGR",
        "Manages maintenance operations",
        roles.get("PROD_MANAGER"),
        order++);
    createPosition(
        departmentId,
        "Electrical Technician",
        "MAINT-ELEC",
        "Performs electrical maintenance",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Mechanical Technician",
        "MAINT-MECH",
        "Performs mechanical maintenance",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Automation Technician",
        "MAINT-AUTO",
        "Maintains automation systems",
        roles.get("PROD_WORKER"),
        order++);
  }

  private void createEnergyPositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "Energy Facility Operator",
        "ENERGY-OP",
        "Operates energy facilities",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Steam Boiler Operator",
        "ENERGY-STEAM",
        "Operates steam boilers",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Generator Operator",
        "ENERGY-GEN",
        "Operates generators",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "HVAC Technician",
        "ENERGY-HVAC",
        "Maintains HVAC systems",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Water Treatment Operator",
        "ENERGY-WATER",
        "Operates water treatment systems",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Wastewater Treatment Operator",
        "ENERGY-WASTE",
        "Operates wastewater treatment systems",
        roles.get("PROD_WORKER"),
        order++);
  }

  private void createKitchenPositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "Kitchen Manager",
        "KIT-MGR",
        "Manages kitchen operations",
        roles.get("PROD_MANAGER"),
        order++);
    createPosition(
        departmentId,
        "Head Chef",
        "KIT-CHEF",
        "Leads kitchen operations",
        roles.get("PROD_MANAGER"),
        order++);
    createPosition(
        departmentId, "Cook", "KIT-COOK", "Prepares meals", roles.get("PROD_WORKER"), order++);
    createPosition(
        departmentId,
        "Assistant Cook",
        "KIT-ASST",
        "Assists in meal preparation",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Baker",
        "KIT-BAKE",
        "Prepares baked goods",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Grill Cook",
        "KIT-GRILL",
        "Operates grill equipment",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Salad & Cold Meze Specialist",
        "KIT-SALAD",
        "Prepares salads and cold meze",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Pastry Chef",
        "KIT-PAST",
        "Prepares desserts and pastries",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Dishwasher",
        "KIT-WASH",
        "Cleans dishes and utensils",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Service Staff",
        "KIT-SVC",
        "Serves meals",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Cafeteria Supervisor",
        "KIT-CAFE",
        "Supervises cafeteria operations",
        roles.get("PROD_MANAGER"),
        order++);
    createPosition(
        departmentId,
        "Cafeteria Clerk",
        "KIT-CAFE-CLK",
        "Manages cafeteria",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Food Hygiene Controller",
        "KIT-HYGIENE",
        "Controls food hygiene",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Food Distribution Staff",
        "KIT-DIST",
        "Distributes meals",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Tea & Beverage Server",
        "KIT-TEA",
        "Serves tea and beverages",
        roles.get("PROD_WORKER"),
        order++);
  }

  private void seedSupportDepartments(
      UUID companyId, Map<String, DepartmentCategory> categories, Map<String, Role> roles) {
    DepartmentCategory supportCategory = categories.get("Support");
    if (supportCategory == null) {
      log.warn("Support category not found, skipping support departments");
      return;
    }

    // IT Services Department
    Department itDept =
        createDepartment(
            companyId,
            "IT Services",
            "IT support and system administration",
            supportCategory.getId());
    createITPositions(itDept.getId(), roles);

    // Security Department
    Department securityDept =
        createDepartment(
            companyId, "Security", "Security and access control", supportCategory.getId());
    createSecurityPositions(securityDept.getId(), roles);

    // Cleaning Services Department
    Department cleaningDept =
        createDepartment(
            companyId,
            "Cleaning Services",
            "Cleaning and janitorial services",
            supportCategory.getId());
    createCleaningPositions(cleaningDept.getId(), roles);
  }

  private void createHRPositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "HR Manager",
        "HR-MGR",
        "Manages human resources department",
        roles.get("HR_MANAGER"),
        order++);
    createPosition(
        departmentId,
        "HR Specialist",
        "HR-SPEC",
        "Handles HR operations",
        roles.get("ADMIN"),
        order++);
    createPosition(
        departmentId,
        "Payroll Clerk",
        "HR-PAYROLL",
        "Manages payroll processing",
        roles.get("ADMIN"),
        order++);
  }

  private void createFinancePositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "Finance Manager",
        "FIN-MGR",
        "Manages finance department",
        roles.get("ADMIN"),
        order++);
    createPosition(
        departmentId,
        "Accountant",
        "FIN-ACC",
        "Handles accounting operations",
        roles.get("ADMIN"),
        order++);
    createPosition(
        departmentId,
        "Accounting Clerk",
        "FIN-CLERK",
        "Performs accounting tasks",
        roles.get("ADMIN"),
        order++);
    createPosition(
        departmentId,
        "Cashier",
        "FIN-CASH",
        "Handles cash transactions",
        roles.get("ADMIN"),
        order++);
  }

  private void createAdminOfficePositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "Administrative Manager",
        "ADMIN-MGR",
        "Manages administrative operations",
        roles.get("ADMIN"),
        order++);
    createPosition(
        departmentId,
        "Office Clerk",
        "ADMIN-CLERK",
        "Handles office tasks",
        roles.get("ADMIN"),
        order++);
    createPosition(
        departmentId,
        "Receptionist",
        "ADMIN-RECEPT",
        "Manages reception and front desk",
        roles.get("ADMIN"),
        order++);
  }

  private void createManagementPositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "General Manager",
        "MGT-GM",
        "Manages overall operations",
        roles.get("ADMIN"),
        order++);
    createPosition(
        departmentId,
        "Production Planner",
        "MGT-PLANNER",
        "Plans production schedules",
        roles.get("ADMIN"),
        order++);
    createPosition(
        departmentId,
        "Project Coordinator",
        "MGT-COORD",
        "Coordinates projects",
        roles.get("ADMIN"),
        order++);
  }

  private void createProcurementPositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "Procurement Manager",
        "PROC-MGR",
        "Manages procurement operations",
        roles.get("LOG_MANAGER"),
        order++);
    createPosition(
        departmentId,
        "Purchasing Officer",
        "PROC-PURCH",
        "Handles purchasing operations",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Inventory Controller",
        "PROC-INV",
        "Controls inventory levels",
        roles.get("PROD_WORKER"),
        order++);
  }

  private void createShippingPositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "Shipping Supervisor",
        "SHIP-SUPV",
        "Supervises shipping operations",
        roles.get("LOG_MANAGER"),
        order++);
    createPosition(
        departmentId,
        "Forklift Operator",
        "SHIP-FORK",
        "Operates forklift equipment",
        roles.get("WAREHOUSE_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Driver",
        "SHIP-DRIVER",
        "Drives delivery vehicles",
        roles.get("WAREHOUSE_WORKER"),
        order++);
  }

  private void createITPositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId, "IT Manager", "IT-MGR", "Manages IT department", roles.get("ADMIN"), order++);
    createPosition(
        departmentId,
        "Network Administrator",
        "IT-NET",
        "Manages network infrastructure",
        roles.get("ADMIN"),
        order++);
    createPosition(
        departmentId,
        "System Support Specialist",
        "IT-SUPPORT",
        "Provides system support",
        roles.get("ADMIN"),
        order++);
  }

  private void createSecurityPositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "Security Chief",
        "SEC-CHIEF",
        "Manages security operations",
        roles.get("ADMIN"),
        order++);
    createPosition(
        departmentId,
        "Security Guard",
        "SEC-GUARD",
        "Provides security services",
        roles.get("PROD_WORKER"),
        order++);
  }

  private void createCleaningPositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "Cleaning Supervisor",
        "CLEAN-SUPV",
        "Supervises cleaning operations",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Cleaner",
        "CLEAN-WORKER",
        "Performs cleaning tasks",
        roles.get("PROD_WORKER"),
        order++);
  }

  private void createFiberPositions(UUID departmentId, Map<String, Role> roles) {
    int order = 1;
    createPosition(
        departmentId,
        "Raw Material Manager",
        "FIBER-MGR",
        "Manages raw material operations",
        roles.get("PROD_MANAGER"),
        order++);
    createPosition(
        departmentId,
        "Fiber Quality Controller",
        "FIBER-QC",
        "Performs quality control on fibers",
        roles.get("QC"),
        order++);
    createPosition(
        departmentId,
        "Raw Material Clerk",
        "FIBER-CLERK",
        "Handles raw material documentation",
        roles.get("PROD_WORKER"),
        order++);
    createPosition(
        departmentId,
        "Forklift Operator",
        "FIBER-FORK",
        "Operates forklift equipment",
        roles.get("PROD_WORKER"),
        order++);
  }
}
