package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.AddressSnapshot;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthority;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthorityBasis;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthorityEnd;
import com.fabricmanagement.sales.salesorder.domain.BillTo;
import com.fabricmanagement.sales.salesorder.domain.BillToRelationship;
import com.fabricmanagement.sales.salesorder.domain.DeliveryEvent;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermSetting;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermStatus;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.PartyMode;
import com.fabricmanagement.sales.salesorder.domain.PartyReference;
import com.fabricmanagement.sales.salesorder.domain.PartySnapshot;
import com.fabricmanagement.sales.salesorder.domain.RequestedDate;
import com.fabricmanagement.sales.salesorder.domain.RequestedDateStatus;
import com.fabricmanagement.sales.salesorder.domain.RequestedDeliveryEvent;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The order's parties and order-level defaults (ADR-0014 D4, D7, D9). Every write names the order
 * version it was based on; a stale write is rejected, never merged.
 */
public final class OrderPartyDtos {

  private OrderPartyDtos() {}

  // ─── Shared inputs and views ───────────────────────────────────────────────

  @Schema(name = "PartySnapshotInput", description = "An unregistered party as entered")
  public record PartySnapshotInput(String name, String contactName, String email, String phone) {
    public PartySnapshot toSnapshot() {
      return PartySnapshot.of(name, contactName, email, phone);
    }
  }

  @Schema(name = "PartySnapshotView")
  public record PartySnapshotView(String name, String contactName, String email, String phone) {
    static PartySnapshotView of(PartySnapshot value) {
      return value == null
          ? null
          : new PartySnapshotView(
              value.getName(), value.getContactName(), value.getEmail(), value.getPhone());
    }
  }

  @Schema(
      name = "AddressInput",
      description =
          "An address as used on the order (a copy). Any part may be missing in a draft; the gate"
              + " that needs a complete address asks for it.")
  public record AddressInput(
      String line1,
      String line2,
      String city,
      String region,
      String postalCode,
      @Schema(description = "ISO 3166-1 alpha-2") String countryCode,
      @Schema(description = "The partner address card it was copied from, if any")
          UUID sourceAddressId) {
    public AddressSnapshot toSnapshot() {
      return AddressSnapshot.of(
          line1, line2, city, region, postalCode, countryCode, sourceAddressId);
    }
  }

  @Schema(name = "AddressView")
  public record AddressView(
      @Schema(description = "Street line, city and country are all present") boolean complete,
      String line1,
      String line2,
      String city,
      String region,
      String postalCode,
      String countryCode,
      UUID sourceAddressId) {
    static AddressView of(AddressSnapshot value) {
      return value == null
          ? null
          : new AddressView(
              value.isComplete(),
              value.getLine1(),
              value.getLine2(),
              value.getCity(),
              value.getRegion(),
              value.getPostalCode(),
              value.getCountryCode(),
              value.getSourceAddressId());
    }
  }

  @Schema(name = "PartyInput", description = "Who fills a role; omit the mode while undecided")
  public record PartyInput(PartyMode mode, UUID partnerId, @Valid PartySnapshotInput party) {
    public PartyReference toReference(UUID customerId, String role) {
      return PartyReference.of(
          mode, partnerId, party == null ? null : party.toSnapshot(), customerId, role);
    }
  }

  @Schema(name = "PartyView")
  public record PartyView(PartyMode mode, UUID partnerId, PartySnapshotView party) {
    public static PartyView of(PartyReference value) {
      return new PartyView(value.mode(), value.partnerId(), PartySnapshotView.of(value.snapshot()));
    }
  }

  @Schema(name = "RequestedDateInput")
  public record RequestedDateInput(
      @Schema(description = "Omit while unknown whether the customer asked for a date")
          RequestedDateStatus status,
      LocalDate date,
      @Schema(description = "What the customer meant; UNSPECIFIED when the customer did not say")
          RequestedDeliveryEvent event,
      @Schema(
              description =
                  "Where the customer said it should happen; kept apart from the term's named place",
              maxLength = RequestedDate.MAX_PLACE)
          String place) {
    public RequestedDate toRequestedDate() {
      return RequestedDate.of(status, date, event, place);
    }
  }

  @Schema(name = "RequestedDateView")
  public record RequestedDateView(
      RequestedDateStatus status, LocalDate date, RequestedDeliveryEvent event, String place) {
    public static RequestedDateView of(RequestedDate value) {
      return new RequestedDateView(value.status(), value.date(), value.event(), value.place());
    }
  }

  @Schema(name = "DeliveryTermInput")
  public record DeliveryTermInput(
      DeliveryTerm deliveryTerm,
      String deliveryPlace,
      IncotermsVersion incotermsVersion,
      DeliveryTermStatus status,
      String contractReference) {
    public DeliveryTermSetting toSetting() {
      return DeliveryTermSetting.of(
          com.fabricmanagement.sales.salesorder.domain.DeliveryTerms.of(
              deliveryTerm, deliveryPlace, incotermsVersion),
          status,
          contractReference);
    }
  }

  @Schema(name = "DeliveryTermView")
  public record DeliveryTermView(
      DeliveryTerm deliveryTerm,
      String deliveryPlace,
      IncotermsVersion incotermsVersion,
      DeliveryTermStatus status,
      String contractReference,
      @Schema(description = "The event the term's dates refer to") DeliveryEvent event) {
    public static DeliveryTermView of(DeliveryTermSetting value) {
      return new DeliveryTermView(
          value.terms().term(),
          value.terms().place(),
          value.terms().version(),
          value.status(),
          value.contractReference(),
          value.terms().event());
    }
  }

  // ─── Order-level writes ────────────────────────────────────────────────────

  @Schema(name = "SetBillToRequest")
  public record SetBillToRequest(
      @NotNull Long expectedVersion,
      @Valid PartyInput party,
      @Valid AddressInput address,
      BillToRelationship relationship,
      String reason) {}

  @Schema(name = "SetReleasePolicyRequest")
  public record SetReleasePolicyRequest(@NotNull Long expectedVersion, boolean releaseTogether) {}

  @Schema(name = "SetRequestedDateRequest")
  public record SetRequestedDateRequest(
      @NotNull Long expectedVersion, @Valid RequestedDateInput requestedDate) {}

  @Schema(name = "SetApproverRequest")
  public record SetApproverRequest(
      @NotNull Long expectedVersion,
      @Schema(description = "A customer contact holding an approval authority; null to clear")
          UUID contactId) {}

  // ─── Order-level view ──────────────────────────────────────────────────────

  @Schema(name = "BillToView")
  public record BillToView(
      PartyView party,
      AddressView address,
      BillToRelationship relationship,
      String reason,
      @Schema(description = "Invoicing someone other than the customer needs finance acceptance")
          boolean differsFromCustomer) {
    static BillToView of(BillTo value) {
      return new BillToView(
          PartyView.of(value.party()),
          AddressView.of(value.address()),
          value.relationship(),
          value.reason(),
          value.differsFromCustomer());
    }
  }

  @Schema(name = "OrderPartiesView")
  public record OrderPartiesView(
      UUID orderId,
      @Schema(description = "Send it back as expectedVersion with the next write")
          Long orderVersion,
      UUID customerId,
      BillToView billTo,
      RequestedDateView requestedDate,
      DeliveryTermView deliveryTerm,
      boolean releaseTogether,
      UUID approverContactId) {
    public static OrderPartiesView of(SalesOrder order) {
      return new OrderPartiesView(
          order.getId(),
          order.getVersion(),
          order.getTradingPartnerId(),
          BillToView.of(order.getBillTo()),
          RequestedDateView.of(order.getRequestedDate()),
          DeliveryTermView.of(order.getDeliveryTermSetting()),
          order.isReleaseTogether(),
          order.getApproverContactId());
    }
  }

  // ─── Approval authority ────────────────────────────────────────────────────

  @Schema(name = "GrantApprovalAuthorityRequest")
  public record GrantApprovalAuthorityRequest(
      @Schema(description = "The person who holds the authority, as named in its basis") @NotNull
          String representativeName,
      @Schema(
              description =
                  "The representative's e-mail contact point among the customer's contacts")
          @NotNull
          UUID contactId,
      @Schema(
              description =
                  "The address the grantor confirms as the representative's; it must be what the"
                      + " contact point reads now, and requests go only there")
          @NotNull
          String email,
      @NotNull ApprovalAuthorityBasis basis,
      @NotNull String basisReference,
      @NotNull LocalDate validFrom,
      LocalDate validUntil) {}

  @Schema(name = "RevokeApprovalAuthorityRequest")
  public record RevokeApprovalAuthorityRequest(@NotNull String reason) {}

  @Schema(name = "ApprovalAuthorityView")
  public record ApprovalAuthorityView(
      UUID id,
      UUID customerId,
      String representativeName,
      UUID contactId,
      @Schema(
              description =
                  "The address the contact point read when the authority was granted; requests go"
                      + " only here")
          String authorisedEmail,
      @Schema(
              description =
                  "The contact point now reads another address (or was removed): no request can be"
                      + " sent under this authority until it is granted again at the new address")
          boolean contactAddressChanged,
      ApprovalAuthorityBasis basis,
      String basisReference,
      LocalDate validFrom,
      LocalDate validUntil,
      UUID grantedBy,
      Instant grantedAt,
      UUID revokedBy,
      Instant revokedAt,
      String revocationReason,
      @Schema(
              description =
                  "Why it ended: revoked by a user, or its contact point changed or was removed;"
                      + " every ending is permanent")
          ApprovalAuthorityEnd revocationCause,
      @Schema(description = "Valid today and not revoked") boolean active) {
    public static ApprovalAuthorityView of(
        ApprovalAuthority value, LocalDate today, boolean contactAddressChanged) {
      return new ApprovalAuthorityView(
          value.getId(),
          value.getTradingPartnerId(),
          value.getRepresentativeName(),
          value.getContactId(),
          value.getAuthorisedEmail(),
          contactAddressChanged,
          value.getBasis(),
          value.getBasisReference(),
          value.getValidFrom(),
          value.getValidUntil(),
          value.getGrantedBy(),
          value.getGrantedAt(),
          value.getRevokedBy(),
          value.getRevokedAt(),
          value.getRevocationReason(),
          value.getRevocationCause(),
          value.isActiveOn(today));
    }
  }
}
