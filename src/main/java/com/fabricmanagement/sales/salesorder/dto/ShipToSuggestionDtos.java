package com.fabricmanagement.sales.salesorder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Which address card of a consignee the order page offers first (CONSIGNEE-1 C11). */
public final class ShipToSuggestionDtos {

  private ShipToSuggestionDtos() {}

  /** Why a card is offered. */
  @Schema(name = "ShipToSuggestionSource", enumAsRef = true)
  public enum Source {
    /** The verified card this tenant's deliveries to the consignee used most. */
    MOST_USED,
    /** The consignee's shipping address, when no verified card was used before. */
    SHIPPING,
    /** The consignee's primary address, when it has no shipping address. */
    PRIMARY,
    /** The consignee has no address card to offer. */
    NONE
  }

  @Schema(name = "ShipToSuggestion")
  public record Suggestion(
      Source source,
      @Schema(nullable = true, description = "The address card to preselect; empty for NONE")
          UUID addressId) {

    public static Suggestion none() {
      return new Suggestion(Source.NONE, null);
    }
  }
}
