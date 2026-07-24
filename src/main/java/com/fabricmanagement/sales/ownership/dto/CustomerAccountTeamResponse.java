package com.fabricmanagement.sales.ownership.dto;

import com.fabricmanagement.sales.ownership.domain.OwnerResolutionReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record CustomerAccountTeamResponse(
    UUID customerId,
    @Schema(nullable = true) UUID acquiredById,
    @Schema(nullable = true) UUID defaultOwnerId,
    OwnerResolutionReason defaultOwnerReason,
    List<CustomerAccountTeamMemberResponse> members) {}
