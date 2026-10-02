package com.fabricmanagement.common.infrastructure.bootstrap;

import java.util.UUID;

/**
 * Whether a tenant is a playground experience that initial provisioning has installed fixtures for
 * (FIBER-CATALOG-1 §9): a newly created legacy PLAYGROUND tenant or a register-first PLAYGROUND
 * signup. Playground-only demo data keys off this fact, never off tenant type or demo flags, so a
 * TRIAL signup, the template company at start-up, and a tenant after reset receive none of it.
 * Consumer-owned port (backend §17); production's playground module answers it.
 */
public interface PlaygroundSeedScopePort {

  boolean isInitialPlaygroundProvisioning(UUID tenantId);
}
