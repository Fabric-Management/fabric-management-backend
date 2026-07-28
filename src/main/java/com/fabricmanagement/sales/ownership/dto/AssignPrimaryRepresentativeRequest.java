package com.fabricmanagement.sales.ownership.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignPrimaryRepresentativeRequest(
    @NotNull(message = "Representative ID is required") UUID representativeId) {}
