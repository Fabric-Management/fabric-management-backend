package com.fabricmanagement.production.execution.stockunit.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartTransferRequest(@NotNull UUID targetLocationId) {}
