package com.fabricmanagement.sales.ownership.domain;

import java.util.UUID;

/** Commercial-owner resolution result. Owner is null only when triage is required. */
public record OwnerResolution(UUID ownerId, OwnerResolutionReason reason) {}
