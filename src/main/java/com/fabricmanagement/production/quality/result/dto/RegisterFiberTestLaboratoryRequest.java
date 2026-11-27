package com.fabricmanagement.production.quality.result.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(name = "RegisterFiberTestLaboratoryRequest")
public record RegisterFiberTestLaboratoryRequest(@NotNull UUID tradingPartnerId) {}
