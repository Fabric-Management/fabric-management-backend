package com.fabricmanagement.sales.salesorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the current user may do with a sales order; the backend says whether and why not. The
 * frontend shows these, it does not derive them from the status.
 */
@Schema(name = "SalesOrderCapability")
public record SalesOrderCapabilityDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Action action,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowed,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description =
                "Why the action is not allowed: NO_OBJECT_ACCESS, PERMISSION_DENIED, WRONG_STATUS"
                    + " or ORDER_WITH_PLANNING. Null when allowed.")
        String reason) {

  /** The order's own commands; each one is an endpoint under /api/v1/sales/orders/{id}. */
  @Schema(name = "SalesOrderAction", enumAsRef = true)
  public enum Action {
    /** PUT /{id}: edit the draft's content. */
    UPDATE,
    /** DELETE /{id}. */
    DELETE,
    /** POST /{id}/process. */
    PROCESS,
    /** POST /{id}/ship. */
    SHIP,
    /** POST /{id}/deliver. */
    DELIVER,
    /** POST /{id}/cancel. */
    CANCEL,
    /** POST /{id}/hold. */
    HOLD,
    /** POST /{id}/resume. */
    RESUME,
    /** POST /{id}/revise. */
    REVISE
  }

  public static final String NO_OBJECT_ACCESS = "NO_OBJECT_ACCESS";
  public static final String PERMISSION_DENIED = "PERMISSION_DENIED";
  public static final String WRONG_STATUS = "WRONG_STATUS";

  public static SalesOrderCapabilityDto of(Action action, String reason) {
    return new SalesOrderCapabilityDto(action, reason == null, reason);
  }
}
