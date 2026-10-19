package com.fabricmanagement.product.yarn.dto;

import com.fabricmanagement.product.fiber.domain.MaterialSource;
import java.math.BigDecimal;
import java.util.UUID;

/** One fibre of a yarn's declared composition, with the fibre snapshots the yarn keeps. */
public record YarnCompositionLineDto(
    UUID fiberId,
    String isoCode,
    String fiberName,
    MaterialSource materialSource,
    BigDecimal percentage) {}
