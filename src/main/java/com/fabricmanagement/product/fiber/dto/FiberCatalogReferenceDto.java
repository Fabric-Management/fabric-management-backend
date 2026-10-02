package com.fabricmanagement.product.fiber.dto;

import java.util.UUID;

/** Exact reference to one canonical shared pure fibre, looked up by its shared ISO code. */
public record FiberCatalogReferenceDto(
    UUID fiberId, UUID productId, UUID isoCodeId, String isoCode, String fiberName) {}
