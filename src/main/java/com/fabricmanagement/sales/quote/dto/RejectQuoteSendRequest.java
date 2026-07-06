package com.fabricmanagement.sales.quote.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Decision note for rejecting a quote send request")
public record RejectQuoteSendRequest(
    @NotBlank
        @Size(max = 1000)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 1000)
        String decisionNote) {}
