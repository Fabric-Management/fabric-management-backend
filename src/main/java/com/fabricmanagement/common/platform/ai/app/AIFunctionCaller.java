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
import java.util.HashMap;
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
            case "smart_search" -> smartSearch(tenantId, parameters);
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
     * Smart search that automatically detects entity type from query and searches accordingly.
     * 
     * <p>Entity Type Detection Rules:</p>
     * <ul>
     *   <li>"pamuk" (without suffix) → FIBER</li>
     *   <li>"pamuk ipliği" / "cotton yarn" → YARN</li>
     *   <li>"gabardin" / "kumaş" → FABRIC</li>
     *   <li>"iplik" / "yarn" → YARN</li>
     *   <li>Technical specs (30/1, GSM) → Usually FABRIC or YARN</li>
     * </ul>
     * 
     * <p>This is more efficient than separate searches - one function call, smart detection.</p>
     */
    private String smartSearch(UUID tenantId, Map<String, Object> parameters) {
        String query = (String) parameters.getOrDefault("query", "");
        
        if (query.isBlank()) {
            return "Arama sorgusu gereklidir. Örnek: 'pamuk', 'pamuk ipliği', 'gabardin'";
        }
        
        // Detect entity type from query
        EntityType entityType = detectEntityType(query);
        
        // Normalize query (Turkish → English)
        String normalizedQuery = normalizeFiberQuery(query);
        
        StringBuilder result = new StringBuilder();
        result.append(String.format("🔍 '%s' için arama yapılıyor...\n", query));
        result.append(String.format("📌 Algılanan tip: %s\n\n", entityType.displayName));
        
        boolean found = false;
        
        // Search based on detected type
        switch (entityType) {
            case FIBER -> {
                List<FiberDto> fibers = fiberFacade.findAll();
                List<FiberDto> matching = fibers.stream()
                    .filter(f -> {
                        if (f.getFiberName() == null) return false;
                        String fiberName = f.getFiberName().toLowerCase();
                        return fiberName.contains(normalizedQuery.toLowerCase()) || 
                               fiberName.contains(query.toLowerCase());
                    })
                    .toList();
                
                if (!matching.isEmpty()) {
                    found = true;
                    result.append("✅ Fiber Bulundu:\n");
                    for (FiberDto f : matching.size() > 5 ? matching.subList(0, 5) : matching) {
                        result.append(String.format("- %s (UID: %s, Status: %s)\n", 
                            f.getFiberName(), f.getUid(), f.getStatus()));
                    }
                    if (matching.size() > 5) {
                        result.append(String.format("\n(%d fiber bulundu, ilk 5 gösteriliyor)\n", matching.size()));
                    }
                }
            }
            case YARN -> {
                // Search Materials with type=YARN
                List<MaterialDto> materials = materialFacade.findByTenant(tenantId);
                List<MaterialDto> matching = materials.stream()
                    .filter(m -> m.getMaterialType() == MaterialType.YARN && 
                                (m.getUid() != null && 
                                 (m.getUid().toLowerCase().contains(normalizedQuery.toLowerCase()) ||
                                  m.getUid().toLowerCase().contains(query.toLowerCase()))))
                    .toList();
                
                if (!matching.isEmpty()) {
                    found = true;
                    result.append("✅ İplik (YARN) Bulundu:\n");
                    for (MaterialDto m : matching.size() > 5 ? matching.subList(0, 5) : matching) {
                        result.append(String.format("- %s (Type: %s, UID: %s)\n", 
                            m.getUid(), m.getMaterialType(), m.getUid()));
                    }
                    if (matching.size() > 5) {
                        result.append(String.format("\n(%d iplik bulundu, ilk 5 gösteriliyor)\n", matching.size()));
                    }
                }
            }
            case FABRIC -> {
                // Search Materials with type=FABRIC
                List<MaterialDto> materials = materialFacade.findByTenant(tenantId);
                List<MaterialDto> matching = materials.stream()
                    .filter(m -> m.getMaterialType() == MaterialType.FABRIC && 
                                (m.getUid() != null && 
                                 (m.getUid().toLowerCase().contains(normalizedQuery.toLowerCase()) ||
                                  m.getUid().toLowerCase().contains(query.toLowerCase()))))
                    .toList();
                
                if (!matching.isEmpty()) {
                    found = true;
                    result.append("✅ Kumaş (FABRIC) Bulundu:\n");
                    for (MaterialDto m : matching.size() > 5 ? matching.subList(0, 5) : matching) {
                        result.append(String.format("- %s (Type: %s, UID: %s)\n", 
                            m.getUid(), m.getMaterialType(), m.getUid()));
                    }
                    if (matching.size() > 5) {
                        result.append(String.format("\n(%d kumaş bulundu, ilk 5 gösteriliyor)\n", matching.size()));
                    }
                }
            }
            case UNKNOWN -> {
                // Try all entity types
                result.append("⚠️ Tip belirsiz, tüm entity tiplerinde aranıyor...\n\n");
                
                // Try FIBER
                List<FiberDto> fibers = fiberFacade.findAll();
                List<FiberDto> matchingFibers = fibers.stream()
                    .filter(f -> f.getFiberName() != null && 
                                (f.getFiberName().toLowerCase().contains(normalizedQuery.toLowerCase()) ||
                                 f.getFiberName().toLowerCase().contains(query.toLowerCase())))
                    .toList();
                
                // Try Materials (YARN, FABRIC)
                List<MaterialDto> materials = materialFacade.findByTenant(tenantId);
                List<MaterialDto> matchingMaterials = materials.stream()
                    .filter(m -> m.getUid() != null && 
                                (m.getUid().toLowerCase().contains(normalizedQuery.toLowerCase()) ||
                                 m.getUid().toLowerCase().contains(query.toLowerCase())))
                    .toList();
                
                if (!matchingFibers.isEmpty()) {
                    found = true;
                    result.append("✅ Fiber Bulundu:\n");
                    for (FiberDto f : matchingFibers.size() > 3 ? matchingFibers.subList(0, 3) : matchingFibers) {
                        result.append(String.format("- Fiber: %s (UID: %s)\n", f.getFiberName(), f.getUid()));
                    }
                    if (matchingFibers.size() > 3) {
                        result.append(String.format("(%d fiber bulundu)\n", matchingFibers.size()));
                    }
                }
                
                if (!matchingMaterials.isEmpty()) {
                    found = true;
                    result.append("\n✅ Material Bulundu:\n");
                    for (MaterialDto m : matchingMaterials.size() > 3 ? matchingMaterials.subList(0, 3) : matchingMaterials) {
                        result.append(String.format("- Material: %s (Type: %s, UID: %s)\n", 
                            m.getUid(), m.getMaterialType(), m.getUid()));
                    }
                    if (matchingMaterials.size() > 3) {
                        result.append(String.format("(%d material bulundu)\n", matchingMaterials.size()));
                    }
                }
            }
        }
        
        if (!found) {
            result.append(String.format("\n❌ '%s' için sonuç bulunamadı.\n", query));
            result.append("💡 İpuçları:\n");
            result.append("- 'pamuk' → Fiber arayın\n");
            result.append("- 'pamuk ipliği' → İplik (YARN) arayın\n");
            result.append("- 'gabardin' → Kumaş (FABRIC) arayın\n");
        }
        
        return result.toString();
    }

    /**
     * Detect entity type from query string using intelligent pattern matching.
     */
    private EntityType detectEntityType(String query) {
        if (query == null || query.isBlank()) {
            return EntityType.UNKNOWN;
        }
        
        String lowerQuery = query.toLowerCase().trim();
        
        // YARN indicators
        if (lowerQuery.contains("iplik") || lowerQuery.contains("yarn") || 
            lowerQuery.contains("ipliği") || lowerQuery.contains("ipliğin")) {
            return EntityType.YARN;
        }
        
        // FABRIC indicators
        if (lowerQuery.contains("kumaş") || lowerQuery.contains("fabric") ||
            lowerQuery.contains("gabardin") || lowerQuery.contains("poplin") ||
            lowerQuery.contains("denim") || lowerQuery.contains("twill") ||
            lowerQuery.contains("jersey") || lowerQuery.contains("rib") ||
            // Technical specs often indicate fabric
            lowerQuery.matches(".*\\d+/\\d+.*") || lowerQuery.contains("gsm")) {
            return EntityType.FABRIC;
        }
        
        // FIBER indicators (base materials, but not yarn/fabric)
        if (lowerQuery.matches(".*\\b(pamuk|cotton|polyester|poliester|yün|wool|keten|linen|ipek|silk|akrilik|acrylic|naylon|nylon|viscose|viskoz|elastan|elastane)\\b.*") &&
            !lowerQuery.contains("iplik") && !lowerQuery.contains("yarn") &&
            !lowerQuery.contains("kumaş") && !lowerQuery.contains("fabric")) {
            return EntityType.FIBER;
        }
        
        // Default to unknown - will search all
        return EntityType.UNKNOWN;
    }

    /**
     * Entity type enum for smart search.
     */
    private enum EntityType {
        FIBER("Fiber (Pamuk, Polyester, Yün, vb.)"),
        YARN("İplik (Yarn)"),
        FABRIC("Kumaş (Fabric)"),
        UNKNOWN("Belirsiz (Tüm tiplerde arama)");
        
        private final String displayName;
        
        EntityType(String displayName) {
            this.displayName = displayName;
        }
    }

    /**
     * Search materials by query.
     * Also checks if there are related fibers for better context.
     */
    private String searchMaterials(UUID tenantId, Map<String, Object> parameters) {
        String query = (String) parameters.getOrDefault("query", "");
        
        List<MaterialDto> materials = materialFacade.findByTenant(tenantId);
        
        if (query.isBlank()) {
            return String.format("Total materials in system: %d", materials.size());
        }

        // Turkish-to-English translation for material/fiber names
        String normalizedQuery = normalizeFiberQuery(query);

        List<MaterialDto> matching = materials.stream()
            .filter(m -> {
                if (m.getUid() == null) return false;
                String uid = m.getUid().toLowerCase();
                String searchTerm = normalizedQuery.toLowerCase();
                return uid.contains(searchTerm) || uid.contains(query.toLowerCase());
            })
            .collect(Collectors.toList());

        // If no materials found, check if it might be a fiber name
        if (matching.isEmpty()) {
            // Try searching fibers instead
            List<FiberDto> fibers = fiberFacade.findAll();
            List<FiberDto> matchingFibers = fibers.stream()
                .filter(f -> {
                    if (f.getFiberName() == null) return false;
                    String fiberName = f.getFiberName().toLowerCase();
                    String searchTerm = normalizedQuery.toLowerCase();
                    return fiberName.contains(searchTerm) || 
                           fiberName.contains(query.toLowerCase());
                })
                .toList();
            
            if (!matchingFibers.isEmpty()) {
                StringBuilder result = new StringBuilder();
                result.append(String.format("⚠️ '%s' için Material bulunamadı, ancak Fiber bulundu:\n\n", query));
                for (FiberDto f : matchingFibers) {
                    result.append(String.format("- Fiber: %s (UID: %s, Status: %s)\n", 
                        f.getFiberName(), f.getUid(), f.getStatus()));
                    if (f.getMaterialId() != null) {
                        materialFacade.findById(tenantId, f.getMaterialId()).ifPresent(m -> {
                            result.append(String.format("  → İlişkili Material: %s (UID: %s)\n", 
                                m.getMaterialType(), m.getUid()));
                        });
                    }
                }
                result.append("\n💡 Not: Material ve Fiber farklı entity'lerdir. Fiber aramak için 'search_fibers' fonksiyonunu kullanın.");
                return result.toString();
            }
            
            return String.format("No materials found matching '%s'. Try searching fibers with 'search_fibers' if you're looking for fiber names like 'cotton' or 'pamuk'.", query);
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
     * Supports Turkish-to-English translation for common fiber names.
     */
    private String searchFibers(UUID tenantId, Map<String, Object> parameters) {
        String query = (String) parameters.getOrDefault("query", "");
        
        List<FiberDto> fibers = fiberFacade.findAll();
        
        if (query.isBlank()) {
            return String.format("Toplam fiber sayısı: %d", fibers.size());
        }

        // Turkish-to-English translation for fiber names
        String normalizedQuery = normalizeFiberQuery(query);

        List<FiberDto> matching = fibers.stream()
            .filter(f -> {
                if (f.getFiberName() == null) return false;
                String fiberName = f.getFiberName().toLowerCase();
                String searchTerm = normalizedQuery.toLowerCase();
                
                // Check if fiber name contains the search term (Turkish or English)
                return fiberName.contains(searchTerm) || 
                       fiberName.contains(query.toLowerCase());
            })
            .toList();

        if (matching.isEmpty()) {
            // Try to suggest if it's a translation issue
            if (!normalizedQuery.equalsIgnoreCase(query)) {
                return String.format("'%s' (veya '%s') için fiber bulunamadı. Lütfen fiber adını kontrol edin veya 'search_fibers' fonksiyonunu kullanın.", 
                    query, normalizedQuery);
            }
            return String.format("'%s' için fiber bulunamadı. Material aramak için 'search_materials' kullanın.", query);
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

    /**
     * Normalize fiber query by translating Turkish fiber names to English.
     * This helps users search with Turkish names (e.g., "pamuk") and find English-named fibers (e.g., "Cotton").
     */
    private String normalizeFiberQuery(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }
        
        String lowerQuery = query.toLowerCase().trim();
        
        // Turkish-to-English fiber name mapping
        // Using HashMap to avoid Map.of() 10-pair limit
        Map<String, String> translations = new HashMap<>();
        translations.put("pamuk", "cotton");
        translations.put("polyester", "polyester");
        translations.put("poliester", "polyester");
        translations.put("yün", "wool");
        translations.put("naylon", "nylon");
        translations.put("nylon", "nylon");
        translations.put("viscose", "viscose");
        translations.put("viskoz", "viscose");
        translations.put("elastan", "elastane");
        translations.put("spandeks", "elastane");
        translations.put("elastane", "elastane");
        translations.put("polypropilen", "polypropylene");
        translations.put("polypropylene", "polypropylene");
        translations.put("keten", "linen");
        translations.put("linen", "linen");
        translations.put("ipek", "silk");
        translations.put("silk", "silk");
        translations.put("akrilik", "acrylic");
        translations.put("acrylic", "acrylic");
        translations.put("materyal", "material");
        translations.put("materyali", "material");
        
        // Check for exact matches first
        for (Map.Entry<String, String> entry : translations.entrySet()) {
            if (lowerQuery.contains(entry.getKey())) {
                // Replace Turkish word with English, but keep other parts of query
                return lowerQuery.replace(entry.getKey(), entry.getValue());
            }
        }
        
        // If no translation found, return original query
        return query;
    }
}

