package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.EnumSet;
import java.util.Set;

/**
 * Incoterms rule agreed for an order. The term decides which event the order's dates refer to and
 * what its named place means. A domestic "collect from the mill" deal is not assumed to be EXW: the
 * term is recorded only when it was agreed.
 */
@Schema(name = "DeliveryTerm", enumAsRef = true)
public enum DeliveryTerm {
  EXW(DeliveryEvent.AVAILABLE_FOR_COLLECTION, NamedPlaceRole.PLACE_OF_DELIVERY, false, both()),
  FCA(DeliveryEvent.HANDED_TO_CARRIER, NamedPlaceRole.PLACE_OF_DELIVERY, false, both()),
  CPT(DeliveryEvent.HANDED_TO_CARRIER, NamedPlaceRole.PLACE_OF_DESTINATION, false, both()),
  CIP(DeliveryEvent.HANDED_TO_CARRIER, NamedPlaceRole.PLACE_OF_DESTINATION, false, both()),
  DAT(
      DeliveryEvent.UNLOADED_AT_DESTINATION,
      NamedPlaceRole.PLACE_OF_DELIVERY,
      false,
      EnumSet.of(IncotermsVersion.INCOTERMS_2010)),
  DAP(
      DeliveryEvent.READY_FOR_UNLOADING_AT_DESTINATION,
      NamedPlaceRole.PLACE_OF_DELIVERY,
      false,
      both()),
  DPU(
      DeliveryEvent.UNLOADED_AT_DESTINATION,
      NamedPlaceRole.PLACE_OF_DELIVERY,
      false,
      EnumSet.of(IncotermsVersion.INCOTERMS_2020)),
  DDP(
      DeliveryEvent.READY_FOR_UNLOADING_AT_DESTINATION,
      NamedPlaceRole.PLACE_OF_DELIVERY,
      false,
      both()),
  FAS(DeliveryEvent.ALONGSIDE_VESSEL, NamedPlaceRole.PORT_OF_SHIPMENT, true, both()),
  FOB(DeliveryEvent.ON_BOARD_VESSEL, NamedPlaceRole.PORT_OF_SHIPMENT, true, both()),
  CFR(DeliveryEvent.ON_BOARD_VESSEL, NamedPlaceRole.PORT_OF_DESTINATION, true, both()),
  CIF(DeliveryEvent.ON_BOARD_VESSEL, NamedPlaceRole.PORT_OF_DESTINATION, true, both());

  /**
   * What the place named with the term is. Under C-terms it is the destination, not where the
   * delivery event happens.
   */
  @Schema(name = "DeliveryNamedPlaceRole", enumAsRef = true)
  public enum NamedPlaceRole {
    PLACE_OF_DELIVERY,
    PLACE_OF_DESTINATION,
    PORT_OF_SHIPMENT,
    PORT_OF_DESTINATION
  }

  private final DeliveryEvent event;
  private final NamedPlaceRole namedPlaceRole;
  private final boolean seaAndInlandWaterwayOnly;
  private final Set<IncotermsVersion> versions;

  DeliveryTerm(
      DeliveryEvent event,
      NamedPlaceRole namedPlaceRole,
      boolean seaAndInlandWaterwayOnly,
      Set<IncotermsVersion> versions) {
    this.event = event;
    this.namedPlaceRole = namedPlaceRole;
    this.seaAndInlandWaterwayOnly = seaAndInlandWaterwayOnly;
    this.versions = versions;
  }

  private static Set<IncotermsVersion> both() {
    return EnumSet.allOf(IncotermsVersion.class);
  }

  public DeliveryEvent event() {
    return event;
  }

  public NamedPlaceRole namedPlaceRole() {
    return namedPlaceRole;
  }

  public boolean seaAndInlandWaterwayOnly() {
    return seaAndInlandWaterwayOnly;
  }

  public Set<IncotermsVersion> versions() {
    return EnumSet.copyOf(versions);
  }

  public boolean existsIn(IncotermsVersion version) {
    return versions.contains(version);
  }
}
