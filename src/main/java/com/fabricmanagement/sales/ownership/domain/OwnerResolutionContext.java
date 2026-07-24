package com.fabricmanagement.sales.ownership.domain;

import java.util.UUID;

/** Stable inputs to commercial-owner resolution; deliberately excludes the requesting user. */
public record OwnerResolutionContext(UUID tenantId, UUID customerId, UUID requestedOwnerId) {}
