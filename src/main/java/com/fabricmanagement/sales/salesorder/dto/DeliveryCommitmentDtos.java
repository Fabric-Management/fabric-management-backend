package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.CommitmentChangeOrigin;
import com.fabricmanagement.sales.salesorder.domain.CommitmentChannel;
import com.fabricmanagement.sales.salesorder.domain.DeliveryCommitment;
import com.fabricmanagement.sales.salesorder.domain.DeliveryEvent;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Delivery terms catalogue and the delivery-commitment history of an order. */
public final class DeliveryCommitmentDtos {

  private DeliveryCommitmentDtos() {}

  /** One Incoterms rule: the event its dates refer to and what its named place means. */
  @Schema(name = "DeliveryTermOption")
  public record DeliveryTermOption(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DeliveryTerm term,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DeliveryEvent event,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          DeliveryTerm.NamedPlaceRole namedPlaceRole,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean eventAtDestination,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean seaAndInlandWaterwayOnly,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Set<IncotermsVersion> versions) {

    public static DeliveryTermOption of(DeliveryTerm term) {
      return new DeliveryTermOption(
          term,
          term.event(),
          term.namedPlaceRole(),
          term.event().isAtDestination(),
          term.seaAndInlandWaterwayOnly(),
          term.versions());
    }
  }

  /**
   * A commitment the customer accepted, as the approval of a sent order version carries it (not an
   * API request). {@code basedOnCommitmentId} is the commitment it replaces (null for the first
   * promise); a stale one is rejected. The delivery term is the order's unless renegotiated.
   */
  public record RecordDeliveryCommitment(
      UUID basedOnCommitmentId,
      @NotNull LocalDate committedOn,
      @Schema(description = "Who asked for the change; omitted for the first promise")
          CommitmentChangeOrigin origin,
      @Size(max = DeliveryCommitment.MAX_REASON_LENGTH) String reason,
      @NotBlank @Size(max = DeliveryCommitment.MAX_CONTACT_LENGTH) String customerContact,
      @NotNull CommitmentChannel channel,
      @NotNull Instant agreedAt,
      @Schema(description = "A renegotiated term; omit to keep the order's term")
          DeliveryTerm deliveryTerm,
      @Size(max = 200) String deliveryPlace,
      IncotermsVersion incotermsVersion) {}

  @Schema(name = "DeliveryCommitmentView")
  public record CommitmentView(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sequence,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDate committedOn,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DeliveryTerm deliveryTerm,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String deliveryPlace,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) IncotermsVersion incotermsVersion,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DeliveryEvent deliveryEvent,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CommitmentChangeOrigin origin,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String reason,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String customerContact,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CommitmentChannel channel,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant agreedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          Long shiftFromPreviousDays,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID recordedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recordedAt) {}

  /**
   * The history with two separate measures. {@code shiftFromInitialDays} is how far the current
   * promise moved from the first one; it is split into the part the buyer asked for and the part
   * the seller revised, so a buyer's postponement is not counted as a broken promise. Whether the
   * current promise is being kept needs the actual event date and is not part of this view. When
   * the delivery event changed along the way, day differences compare different events and {@code
   * sameEventThroughout} is false.
   */
  @Schema(name = "DeliveryCommitmentHistory")
  public record CommitmentHistory(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID orderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) CommitmentView current,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) CommitmentView initial,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          Long shiftFromInitialDays,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long buyerRequestedShiftDays,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sellerRevisionShiftDays,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean sameEventThroughout,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CommitmentView> revisions) {}
}
