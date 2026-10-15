package com.fabricmanagement.inventory.lothealth.app.adapter;

import com.fabricmanagement.inventory.lothealth.app.port.ProductSpeedPort;
import com.fabricmanagement.inventory.lothealth.domain.ProductSpeed;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Every product sells as before (Fatih, 2026-10-14) until product speed is measured nightly and
 * kept in its own table; that work replaces this adapter and reads the kept speed, overridden by
 * planning where it was.
 */
@Component
public class StandingProductSpeedAdapter implements ProductSpeedPort {

  @Override
  public ProductSpeed speedOf(UUID tenantId, UUID productId, UUID colorId) {
    return ProductSpeed.MOVING;
  }
}
