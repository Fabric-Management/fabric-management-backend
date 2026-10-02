package com.fabricmanagement.product.fiber.domain;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.util.List;
import java.util.UUID;

/**
 * Ownership constants of the shared fibre catalogue (FIBER-CATALOG-1).
 *
 * <p>The golden template tenant is the only catalogue owner. Categories, ISO codes, certification
 * schemes and canonical pure fibres (with their products) are published once by that owner and read
 * by every tenant; they are never copied. Another tenant of type TEMPLATE (for example the demo
 * company) is not a catalogue owner.
 */
public final class FiberCatalog {

  /** Exact catalogue owner. Never resolve "a TEMPLATE tenant" instead. */
  public static final UUID OWNER_ID = TenantContext.TEMPLATE_TENANT_ID;

  /** Category every blend belongs to. */
  public static final String MIXED_BLEND_CATEGORY_CODE = "MIXED_BLEND";

  private FiberCatalog() {}

  /** Fibre read scope for a tenant: its own rows plus the shared catalogue, without duplicates. */
  public static List<UUID> readScope(UUID tenantId) {
    return OWNER_ID.equals(tenantId) ? List.of(OWNER_ID) : List.of(tenantId, OWNER_ID);
  }

  public static boolean isOwner(UUID tenantId) {
    return OWNER_ID.equals(tenantId);
  }
}
