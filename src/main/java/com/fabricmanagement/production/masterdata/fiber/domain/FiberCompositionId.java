package com.fabricmanagement.production.masterdata.fiber.domain;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for FiberComposition junction entity.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FiberCompositionId implements Serializable {

    private UUID blendedFiberId;
    private UUID baseFiberId;
}

