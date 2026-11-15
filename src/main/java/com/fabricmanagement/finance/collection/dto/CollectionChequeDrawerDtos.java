package com.fabricmanagement.finance.collection.dto;

import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerKind;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Cheque drawers as the cheque entry form needs them (PAYMENT-PLAN-7 R15, R19). */
public final class CollectionChequeDrawerDtos {
  private CollectionChequeDrawerDtos() {}

  @Schema(name = "FinanceCollectionChequeDrawer")
  public record Drawer(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DrawerType drawerType,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active) {}

  /**
   * The customer itself is always a drawer and comes first as {@code customerName}; it is not one
   * of {@code drawers}. {@code defaultDrawerKind} preselects the kind on a new cheque.
   */
  @Schema(name = "FinanceCollectionChequeDrawers")
  public record Drawers(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID partnerId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String customerName,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "ENDORSED when the customer's default own cheque share is 0, else OWN")
          DrawerKind defaultDrawerKind,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Active drawers only")
          List<Drawer> drawers) {}

  @Schema(name = "FinanceCollectionChequeDrawerAddOutcome", enumAsRef = true)
  public enum AddOutcome {
    /** A new drawer was recorded and can be selected. */
    CREATED,
    /** An active drawer with this name already existed and can be selected. */
    EXISTING,
    /** A deactivated drawer has this name; offer reactivation, do not select it. */
    INACTIVE_DUPLICATE
  }

  @Schema(name = "FinanceCollectionChequeDrawerAddResult")
  public record AddResult(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AddOutcome outcome,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Drawer drawer) {}

  @Schema(name = "AddFinanceCollectionChequeDrawerRequest")
  public record AddRequest(
      @NotBlank
          @Size(max = 200)
          @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 200)
          String name,
      @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DrawerType drawerType) {}
}
