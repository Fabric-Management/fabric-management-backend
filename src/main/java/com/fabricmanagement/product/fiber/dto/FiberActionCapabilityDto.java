package com.fabricmanagement.product.fiber.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Backend action capability ({@code action, allowed, reason, routesTo, needsApproval}) for one
 * fibre row. Clients display it and never derive editability themselves; commands still validate.
 */
@Schema(name = "FiberActionCapabilityDto")
public record FiberActionCapabilityDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FiberAction action,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowed,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "Stable denial code; null when allowed")
        FiberActionBlockedReason reason,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "Alternative flow for a denied action, if any")
        FiberActionRoute routesTo,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean needsApproval) {

  public static FiberActionCapabilityDto allowed(FiberAction action) {
    return new FiberActionCapabilityDto(action, true, null, null, false);
  }

  public static FiberActionCapabilityDto denied(FiberAction action, FiberActionBlockedReason why) {
    return new FiberActionCapabilityDto(action, false, why, null, false);
  }
}
