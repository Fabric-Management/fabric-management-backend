package com.fabricmanagement.platform.auth.dto;

import java.util.UUID;

/** Organization membership available to the current login identity for org switching. */
public record OrganizationMembershipDto(
    UUID tenantId, String tenantName, UUID userId, boolean isCurrent, boolean isDefault) {}
