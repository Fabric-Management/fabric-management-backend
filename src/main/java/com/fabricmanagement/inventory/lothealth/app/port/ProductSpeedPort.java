package com.fabricmanagement.inventory.lothealth.app.port;

import com.fabricmanagement.inventory.lothealth.domain.ProductSpeed;
import java.util.UUID;

/**
 * How fast a product sells in a colour (Fatih, 2026-10-14): the measured or overridden speed once
 * it is kept, {@link ProductSpeed#MOVING} until then. Never null, never an exception: a speed that
 * cannot be read is read as moving, the standard rule.
 */
public interface ProductSpeedPort {

  ProductSpeed speedOf(UUID tenantId, UUID productId, UUID colorId);
}
