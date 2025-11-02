package com.fabricmanagement.common.platform.ai.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.logistics.inventory.api.facade.InventoryFacade;
import com.fabricmanagement.logistics.inventory.api.facade.InventoryFacade.StockInfo;
import com.fabricmanagement.production.masterdata.fiber.api.facade.FiberFacade;
import com.fabricmanagement.production.masterdata.fiber.domain.reference.FiberCategory;
import com.fabricmanagement.production.masterdata.fiber.dto.CreateFiberRequest;
import com.fabricmanagement.production.masterdata.fiber.dto.FiberDto;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberCategoryRepository;
import com.fabricmanagement.production.masterdata.material.api.facade.MaterialFacade;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
import com.fabricmanagement.production.masterdata.material.dto.CreateMaterialRequest;
import com.fabricmanagement.production.masterdata.material.dto.MaterialDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI Function Caller - Executes backend actions for FabricAI.
 *
 * <p>Handles function calls from AI assistant and executes corresponding backend operations.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AIFunctionCaller {

    private final MaterialFacade materialFacade;
    private final FiberFacade fiberFacade;
    private final InventoryFacade inventoryFacade;
    private final com.fabricmanagement.logistics.inventory.app.MaterialMatcher materialMatcher;
    private final FiberCategoryRepository fiberCategoryRepository;

    /**
     * Execute function call from AI.
     *
     * @param functionName function name (e.g., "check_material_stock")
     * @param parameters function parameters
     * @return function result as string (will be sent back to AI)
     */
    public String executeFunction(String functionName, Map<String, Object> parameters) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Executing AI function: functionName={}, parameters={}, tenantId={}", 
            functionName, parameters, tenantId);

        return switch (functionName) {
            case "check_material_stock" -> checkMaterialStock(tenantId, parameters);
            case "search_materials" -> searchMaterials(tenantId, parameters);
            case "get_production_status" -> getProductionStatus(tenantId);
            case "get_fiber_info" -> getFiberInfo(tenantId, parameters);
            case "search_fibers" -> searchFibers(tenantId, parameters);
            case "list_fiber_categories" -> listFiberCategories(tenantId);
            case "create_material" -> createMaterial(tenantId, parameters);
            case "create_fiber" -> createFiber(tenantId, parameters);
            default -> "Unknown function: " + functionName;
        };
    }

    /**
     * Check material stock by name using InventoryService.
     */
    private String checkMaterialStock(UUID tenantId, Map<String, Object> parameters) {
        String materialName = (String) parameters.get("materialName");
        if (materialName == null || materialName.isBlank()) {
            return "Material name is required for stock check.";
        }

        // Query real inventory data via InventoryFacade
        Optional<StockInfo> stockInfo = inventoryFacade.getStockByMaterialName(tenantId, materialName);

        if (stockInfo.isEmpty()) {
            // Material not found - use intelligent matching to find similar materials
            List<MaterialDto> materials = materialFacade.findByTenant(tenantId);
            List<MaterialDto> matching = materialMatcher.findMatches(materialName, materials);

            // Material doesn't exist in system at all
            if (matching.isEmpty()) {
                return String.format(
                    "Material '%s' bulunamadı. Sistemde böyle bir material/ürün tanımı yok.\n\n" +
                    "Bu material'ı sisteme eklemek için Material Management modülünü kullanmanız gerekiyor.",
                    materialName
                );
            }

            // Multiple matches found - user needs to specify
            if (matching.size() > 1) {
                StringBuilder result = new StringBuilder(String.format(
                    "'%s' için %d farklı material bulundu:\n\n", 
                    materialName, matching.size()));
                for (MaterialDto m : matching) {
                    result.append(String.format("- %s (%s)\n", m.getUid(), m.getMaterialType()));
                }
                result.append("\nLütfen hangi material'ı kontrol etmek istediğinizi belirtin.");
                return result.toString();
            }

            // Single match found but no stock data available
            MaterialDto material = matching.get(0);
            return String.format(
                "Material '%s' sistemde tanımlı ancak stok kaydı bulunamadı.\n\n" +
                "Material Bilgileri:\n" +
                "- Tip: %s\n" +
                "- Birim: %s\n" +
                "- Durum: %s\n\n" +
                "Stok bilgisi için lütfen Inventory Management modülünü kontrol edin.",
                material.getUid(),
                material.getMaterialType() != null ? material.getMaterialType().toString() : "N/A",
                material.getUnit() != null ? material.getUnit() : "N/A",
                material.getIsActive() != null && material.getIsActive() ? "Aktif" : "Pasif"
            );
        }

        // Stock data found - format user-friendly response
        StockInfo stock = stockInfo.get();
        
        if (!stock.available()) {
            return String.format(
                "Material '%s' exists in system but is currently inactive or unavailable.",
                stock.materialName()
            );
        }

        // Format quantity with proper unit
        String quantityStr = formatQuantity(stock.quantity(), stock.unit());
        
        return String.format(
            "Material: %s\nStock Quantity: %s\nLocation: %s\nStatus: Available",
            stock.materialName(),
            quantityStr,
            stock.location()
        );
    }

    /**
     * Format quantity with unit for user-friendly display.
     */
    private String formatQuantity(BigDecimal quantity, String unit) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) == 0) {
            return "0 " + (unit != null ? unit : "units");
        }
        
        // Format large numbers with commas
        String formatted = quantity.stripTrailingZeros().toPlainString();
        if (formatted.contains(".")) {
            // For decimal values, show up to 2 decimal places
            double value = quantity.doubleValue();
            formatted = String.format("%.2f", value);
        }
        
        return formatted + " " + (unit != null ? unit : "units");
    }

    /**
     * Search materials by query.
     */
    private String searchMaterials(UUID tenantId, Map<String, Object> parameters) {
        String query = (String) parameters.getOrDefault("query", "");
        
        List<MaterialDto> materials = materialFacade.findByTenant(tenantId);
        
        if (query.isBlank()) {
            return String.format("Total materials in system: %d", materials.size());
        }

        List<MaterialDto> matching = materials.stream()
            .filter(m -> m.getUid() != null && 
                        m.getUid().toLowerCase().contains(query.toLowerCase()))
            .collect(Collectors.toList());

        if (matching.isEmpty()) {
            return String.format("No materials found matching '%s'.", query);
        }

        // Summarize if too many results (reduce token usage)
        // Aggressive summarization: limit to 5 results to save tokens
        if (matching.size() > 5) {
            StringBuilder result = new StringBuilder(String.format(
                "Found %d materials, showing first 5:\n\n", matching.size()));
            for (MaterialDto m : matching.subList(0, 5)) {
                result.append(String.format("- %s (%s)\n", m.getUid(), m.getMaterialType()));
            }
            result.append("\n(Please refine your search for more specific results.)");
            return result.toString();
        }

        StringBuilder result = new StringBuilder(String.format("Found %d materials:\n\n", matching.size()));
        for (MaterialDto m : matching) {
            result.append(String.format("- %s (%s)\n", m.getUid(), m.getMaterialType()));
        }
        return result.toString();
    }

    /**
     * Get production status summary.
     *
     * <p>Provides overview of production-related entities (materials, fibers).
     * When operations module is available, will include job/work order statistics.</p>
     */
    private String getProductionStatus(UUID tenantId) {
        List<MaterialDto> materials = materialFacade.findByTenant(tenantId);
        long activeMaterials = materials.stream()
            .filter(m -> m.getIsActive() != null && m.getIsActive())
            .count();

        Map<String, Long> materialsByType = materials.stream()
            .filter(m -> m.getIsActive() != null && m.getIsActive())
            .collect(Collectors.groupingBy(
                m -> m.getMaterialType() != null ? m.getMaterialType().toString() : "UNKNOWN",
                Collectors.counting()
            ));

        StringBuilder status = new StringBuilder();
        status.append("📊 Production Status Summary\n\n");
        status.append(String.format("Active Materials: %d\n", activeMaterials));
        
        if (!materialsByType.isEmpty()) {
            status.append("\nMaterials by Type:\n");
            materialsByType.forEach((type, count) -> 
                status.append(String.format("  - %s: %d\n", type, count))
            );
        }

        status.append("\n");
        status.append("ℹ️ Operations module (jobs, work orders) not yet implemented.\n");
        status.append("For detailed production tracking, please use the production dashboard.");

        return status.toString();
    }

    /**
     * Get detailed fiber information including composition and technical specs.
     */
    private String getFiberInfo(UUID tenantId, Map<String, Object> parameters) {
        String fiberName = (String) parameters.get("fiberName");
        UUID fiberId = parameters.get("fiberId") != null 
            ? UUID.fromString(parameters.get("fiberId").toString())
            : null;

        if (fiberId == null && (fiberName == null || fiberName.isBlank())) {
            return "Fiber ID or name is required. Please provide either 'fiberId' or 'fiberName'.";
        }

        Optional<FiberDto> fiber;
        if (fiberId != null) {
            fiber = fiberFacade.findById(fiberId);
        } else {
            // Search by name
            List<FiberDto> fibers = fiberFacade.findAll().stream()
                .filter(f -> f.getFiberName() != null && 
                            f.getFiberName().toLowerCase().contains(fiberName.toLowerCase()))
                .toList();
            
            if (fibers.isEmpty()) {
                return String.format("Fiber '%s' bulunamadı. Sistemde böyle bir fiber tanımı yok.", fiberName);
            }
            
            if (fibers.size() > 1) {
                StringBuilder result = new StringBuilder(String.format(
                    "'%s' için %d farklı fiber bulundu:\n\n", fiberName, fibers.size()));
                for (FiberDto f : fibers) {
                    result.append(String.format("- %s (%s)\n", f.getFiberName(), f.getStatus()));
                }
                result.append("\nLütfen hangi fiber'ı kontrol etmek istediğinizi belirtin (UID veya tam ad).");
                return result.toString();
            }
            
            fiber = Optional.of(fibers.get(0));
        }

        if (fiber.isEmpty()) {
            return String.format("Fiber bulunamadı (ID: %s)", fiberId);
        }

        FiberDto f = fiber.get();
        StringBuilder info = new StringBuilder();
        info.append(String.format("📊 Fiber Bilgileri: %s\n\n", f.getFiberName()));
        info.append(String.format("UID: %s\n", f.getUid()));
        info.append(String.format("Durum: %s\n", f.getStatus() != null ? f.getStatus() : "N/A"));
        info.append(String.format("Grade: %s\n", f.getFiberGrade() != null ? f.getFiberGrade() : "N/A"));
        
        if (f.getFineness() != null || f.getLengthMm() != null || 
            f.getStrengthCndTex() != null || f.getElongationPercent() != null) {
            info.append("\nTeknik Özellikler:\n");
            if (f.getFineness() != null) {
                info.append(String.format("  - İncelik: %.2f\n", f.getFineness()));
            }
            if (f.getLengthMm() != null) {
                info.append(String.format("  - Uzunluk: %.2f mm\n", f.getLengthMm()));
            }
            if (f.getStrengthCndTex() != null) {
                info.append(String.format("  - Dayanıklılık: %.2f cN/dtex\n", f.getStrengthCndTex()));
            }
            if (f.getElongationPercent() != null) {
                info.append(String.format("  - Uzama: %.2f%%\n", f.getElongationPercent()));
            }
        }

        if (f.getComposition() != null && !f.getComposition().isEmpty()) {
            info.append("\nBileşim (Blended Fiber):\n");
            f.getComposition().forEach((baseFiberId, percentage) -> {
                Optional<FiberDto> baseFiber = fiberFacade.findById(baseFiberId);
                String baseName = baseFiber.map(FiberDto::getFiberName).orElse("Unknown");
                info.append(String.format("  - %s: %.2f%%\n", baseName, percentage));
            });
        }

        if (f.getRemarks() != null && !f.getRemarks().isBlank()) {
            info.append(String.format("\nNotlar: %s\n", f.getRemarks()));
        }

        return info.toString();
    }

    /**
     * Search fibers by name or other criteria.
     */
    private String searchFibers(UUID tenantId, Map<String, Object> parameters) {
        String query = (String) parameters.getOrDefault("query", "");
        
        List<FiberDto> fibers = fiberFacade.findAll();
        
        if (query.isBlank()) {
            return String.format("Toplam fiber sayısı: %d", fibers.size());
        }

        List<FiberDto> matching = fibers.stream()
            .filter(f -> f.getFiberName() != null && 
                        f.getFiberName().toLowerCase().contains(query.toLowerCase()))
            .toList();

        if (matching.isEmpty()) {
            return String.format("'%s' için fiber bulunamadı.", query);
        }

        // Summarize if too many results (reduce token usage)
        // Aggressive summarization: limit to 5 results to save tokens
        if (matching.size() > 5) {
            return String.format(
                "%d fiber bulundu, ilk 5 tanesi:\n\n%s\n\n(Devamı için daha spesifik bir arama yapın.)",
                matching.size(),
                formatFiberList(matching.subList(0, 5))
            );
        }

        return String.format("%d fiber bulundu:\n\n%s", matching.size(), formatFiberList(matching));
    }

    /**
     * Format fiber list (reusable, reduces duplication).
     */
    private String formatFiberList(List<FiberDto> fibers) {
        StringBuilder result = new StringBuilder();
        for (FiberDto f : fibers) {
            result.append(String.format("- %s (%s, %s)\n", 
                f.getFiberName(), 
                f.getStatus() != null ? f.getStatus() : "N/A",
                f.getUid()));
        }
        return result.toString();
    }

    /**
     * List all active fiber categories.
     * 
     * <p>Helps AI find the right category when creating fibers.</p>
     */
    private String listFiberCategories(UUID tenantId) {
        List<FiberCategory> categories = fiberCategoryRepository.findByIsActiveTrue();
        
        if (categories.isEmpty()) {
            return "No fiber categories found in the system.";
        }

        StringBuilder result = new StringBuilder("Available Fiber Categories:\n\n");
        for (FiberCategory category : categories) {
            result.append(String.format("- %s (Code: %s, ID: %s)\n", 
                category.getCategoryName(),
                category.getCategoryCode(),
                category.getId()));
            if (category.getDescription() != null && !category.getDescription().isBlank()) {
                result.append(String.format("  Description: %s\n", category.getDescription()));
            }
        }
        
        return result.toString();
    }

    /**
     * Create a new material.
     * 
     * <p>Required: materialType (FIBER, YARN, FABRIC, CHEMICAL, CONSUMABLE), unit (kg, m, piece, etc.)</p>
     */
    private String createMaterial(UUID tenantId, Map<String, Object> parameters) {
        try {
            String materialTypeStr = (String) parameters.get("materialType");
            String unit = (String) parameters.get("unit");

            if (materialTypeStr == null || materialTypeStr.isBlank()) {
                return "❌ Material Type is required. Valid values: FIBER, YARN, FABRIC, CHEMICAL, CONSUMABLE";
            }

            if (unit == null || unit.isBlank()) {
                return "❌ Unit is required. Examples: kg, m, piece, liter";
            }

            MaterialType materialType;
            try {
                materialType = MaterialType.valueOf(materialTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return String.format("❌ Invalid Material Type: %s. Valid values: FIBER, YARN, FABRIC, CHEMICAL, CONSUMABLE", materialTypeStr);
            }

            CreateMaterialRequest request = CreateMaterialRequest.builder()
                .materialType(materialType)
                .unit(unit)
                .build();

            MaterialDto created = materialFacade.createMaterial(request);

            return String.format(
                "✅ Material created successfully!\n\n" +
                "Material ID: %s\n" +
                "UID: %s\n" +
                "Type: %s\n" +
                "Unit: %s\n" +
                "Status: Active",
                created.getId(),
                created.getUid(),
                created.getMaterialType(),
                created.getUnit()
            );
        } catch (Exception e) {
            log.error("Error creating material", e);
            return "❌ Error creating material: " + e.getMessage();
        }
    }

    /**
     * Create a new fiber.
     * 
     * <p><b>USER-FRIENDLY:</b> Material can be auto-created if materialId is not provided.</p>
     * 
     * <p>Required: fiberCategoryId (UUID), fiberName, unit (if materialId is null)</p>
     * <p>Optional: materialId (if provided, existing Material will be used), fiberGrade, fineness, lengthMm, strengthCndTex, elongationPercent, remarks</p>
     */
    private String createFiber(UUID tenantId, Map<String, Object> parameters) {
        try {
            String materialIdStr = (String) parameters.get("materialId");
            String unit = (String) parameters.get("unit");
            String fiberCategoryIdStr = (String) parameters.get("fiberCategoryId");
            String fiberName = (String) parameters.get("fiberName");

            // USER-FRIENDLY: If materialId is not provided, unit is required (Material will be auto-created)
            if ((materialIdStr == null || materialIdStr.isBlank()) && (unit == null || unit.isBlank())) {
                return "❌ Either materialId or unit is required.\n\n" +
                       "Option 1: Provide materialId to use existing Material\n" +
                       "Option 2: Provide unit (e.g., 'kg') and Material will be auto-created with type=FIBER";
            }

            if (fiberCategoryIdStr == null || fiberCategoryIdStr.isBlank()) {
                return "❌ Fiber Category ID is required.";
            }

            if (fiberName == null || fiberName.isBlank()) {
                return "❌ Fiber Name is required.";
            }

            UUID materialId = null;
            UUID fiberCategoryId;
            try {
                if (materialIdStr != null && !materialIdStr.isBlank()) {
                    materialId = UUID.fromString(materialIdStr);
                    
                    // Validate Material exists and belongs to current tenant if provided
                    if (!materialFacade.exists(tenantId, materialId)) {
                        return String.format(
                            "❌ Material not found: %s\n\n" +
                            "Possible reasons:\n" +
                            "- Material ID is incorrect\n" +
                            "- Material belongs to another tenant\n" +
                            "- Material was deleted\n\n" +
                            "Please use search_materials to find the correct Material ID, or provide unit to auto-create Material.",
                            materialId);
                    }
                }
                
                fiberCategoryId = UUID.fromString(fiberCategoryIdStr);
            } catch (IllegalArgumentException e) {
                return "❌ Invalid UUID format for materialId or fiberCategoryId.";
            }

            // Optional fields
            UUID fiberIsoCodeId = parameters.get("fiberIsoCodeId") != null 
                ? UUID.fromString(parameters.get("fiberIsoCodeId").toString())
                : null;
            String fiberGrade = (String) parameters.get("fiberGrade");
            Double fineness = parameters.get("fineness") != null 
                ? Double.parseDouble(parameters.get("fineness").toString())
                : null;
            Double lengthMm = parameters.get("lengthMm") != null 
                ? Double.parseDouble(parameters.get("lengthMm").toString())
                : null;
            Double strengthCndTex = parameters.get("strengthCndTex") != null 
                ? Double.parseDouble(parameters.get("strengthCndTex").toString())
                : null;
            Double elongationPercent = parameters.get("elongationPercent") != null 
                ? Double.parseDouble(parameters.get("elongationPercent").toString())
                : null;
            String remarks = (String) parameters.get("remarks");

            CreateFiberRequest request = CreateFiberRequest.builder()
                .materialId(materialId)  // Optional - if null, Material will be auto-created
                .unit(unit)  // Required if materialId is null
                .fiberCategoryId(fiberCategoryId)
                .fiberIsoCodeId(fiberIsoCodeId)
                .fiberName(fiberName)
                .fiberGrade(fiberGrade)
                .fineness(fineness)
                .lengthMm(lengthMm)
                .strengthCndTex(strengthCndTex)
                .elongationPercent(elongationPercent)
                .remarks(remarks)
                .build();

            FiberDto created = fiberFacade.createFiber(request);

            return String.format(
                "✅ Fiber created successfully!\n\n" +
                "Fiber ID: %s\n" +
                "UID: %s\n" +
                "Name: %s\n" +
                "Grade: %s\n" +
                "Status: %s",
                created.getId(),
                created.getUid(),
                created.getFiberName(),
                created.getFiberGrade() != null ? created.getFiberGrade() : "N/A",
                created.getStatus() != null ? created.getStatus() : "N/A"
            );
        } catch (Exception e) {
            log.error("Error creating fiber", e);
            return "❌ Error creating fiber: " + e.getMessage();
        }
    }
}

