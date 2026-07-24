package com.fabricmanagement.sales.ownership.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddCustomerAccountTeamMemberRequest(
    @NotNull(message = "User ID is required") UUID userId) {}
