package com.fabricmanagement.logistics.inventory.app;

import com.fabricmanagement.logistics.inventory.api.facade.InventoryFacade;
import com.fabricmanagement.production.masterdata.material.api.facade.MaterialFacade;
import com.fabricmanagement.production.masterdata.material.dto.MaterialDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inventory Service - Business logic for inventory management.
 *
 * <p>Implements InventoryFacade for cross-module communication.</p>
 * <p>Note: Stock quantity and location currently return placeholder values. Real repository integration pending.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService implements InventoryFacade {

    private final MaterialFacade materialFacade;
    private final MaterialMatcher materialMatcher;

    @Override
    @Transactional(readOnly = true)
    public Optional<StockInfo> getStockByMaterialName(UUID tenantId, String materialName) {
        log.debug("Getting stock for material: tenantId={}, materialName={}", tenantId, materialName);

        // Find material by name using intelligent matching
        List<MaterialDto> materials = materialFacade.findByTenant(tenantId);
        List<MaterialDto> matches = materialMatcher.findMatches(materialName, materials);

        if (matches.isEmpty()) {
            log.debug("Material not found: materialName={}", materialName);
            return Optional.empty();
        }

        // Use best match (first in sorted list)
        MaterialDto materialDto = matches.get(0);
        
        // Note: Inventory repository module not yet implemented.
        // When inventory_item and inventory_location tables are created, this should query:
        // SELECT SUM(quantity) FROM logistics.inventory_item 
        // WHERE tenant_id = ? AND material_id = ? AND is_active = true
        // Currently returns placeholder values to indicate material exists in system.
        
        StockInfo stockInfo = new StockInfo(
            materialDto.getUid(),
            materialDto.getUid(),
            BigDecimal.ZERO, // Placeholder - real quantity requires inventory_item table
            materialDto.getUnit() != null ? materialDto.getUnit() : "kg",
            "Main Warehouse", // Placeholder - real location requires inventory_location table
            materialDto.getIsActive() != null && materialDto.getIsActive()
        );

        log.debug("Stock info retrieved: material={}, quantity={}", materialDto.getUid(), stockInfo.quantity());
        return Optional.of(stockInfo);
    }
}

