package com.fabricmanagement.logistics.inventory.api.facade;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Inventory Facade - Internal API for cross-module communication.
 *
 * <p>Other modules should interact with Inventory through this facade.</p>
 * <p>This is IN-PROCESS communication (no HTTP overhead).</p>
 */
public interface InventoryFacade {

    /**
     * Get stock quantity for material by name.
     *
     * @param tenantId Tenant ID
     * @param materialName Material name (or UID) to search
     * @return Stock information if found
     */
    Optional<StockInfo> getStockByMaterialName(UUID tenantId, String materialName);

    /**
     * Stock information DTO.
     */
    record StockInfo(
        String materialName,
        String materialUid,
        BigDecimal quantity,
        String unit,
        String location,
        boolean available
    ) {}
}

