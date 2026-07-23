package com.fabricmanagement.production.execution.batch.domain.port;

import java.util.UUID;

/** Minimal production-owned label for a warehouse location. */
public record WarehouseLocationRef(UUID id, String code, String name) {}
