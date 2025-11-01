package com.fabricmanagement.common.platform.ai.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.logistics.inventory.api.facade.InventoryFacade;
import com.fabricmanagement.logistics.inventory.api.facade.InventoryFacade.StockInfo;
import com.fabricmanagement.production.masterdata.material.api.facade.MaterialFacade;
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
    private final InventoryFacade inventoryFacade;
    private final com.fabricmanagement.logistics.inventory.app.MaterialMatcher materialMatcher;

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

        StringBuilder result = new StringBuilder(String.format("Found %d materials:\n\n", matching.size()));
        for (MaterialDto m : matching) {
            result.append(String.format("- %s (%s)\n", m.getUid(), m.getMaterialType()));
        }
        return result.toString();
    }

    /**
     * Get production status (placeholder - implement when production endpoints are available).
     */
    private String getProductionStatus(UUID tenantId) {
        // TODO: Implement when production status endpoint is available
        return "Production status endpoint not yet implemented. Please check production dashboard for current status.";
    }
}

