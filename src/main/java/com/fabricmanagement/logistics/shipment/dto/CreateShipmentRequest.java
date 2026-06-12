package com.fabricmanagement.logistics.shipment.dto;

import com.fabricmanagement.logistics.shipment.domain.ShipmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

/** Request DTO for creating a new shipment. */
@Data
public class CreateShipmentRequest {

  /**
   * Trading partner ID (customer for outbound, supplier for inbound). Can be either a
   * TradingPartner.id or legacy Company.id - resolved by TradingPartnerResolver.
   */
  @NotNull(message = "Partner ID is required")
  private UUID partnerId;

  /** Reference to related order. */
  private String orderReference;

  /** Shipment type. */
  private ShipmentType shipmentType = ShipmentType.OUTBOUND;

  /** Carrier name. */
  private String carrierName;

  /** Carrier code. */
  private String carrierCode;

  /** Tracking number (if known at creation). */
  private String trackingNumber;

  /** Ship date (planned or actual). */
  private LocalDate shipDate;

  /** Estimated delivery date. */
  private LocalDate estimatedDeliveryDate;

  /** Origin address. */
  private String originAddress;

  /** Destination address. */
  @NotBlank(message = "Destination address is required")
  private String destinationAddress;

  /** Total weight. */
  private BigDecimal totalWeight;

  /** Weight unit. */
  private String weightUnit = "KG";

  /** Number of packages. */
  private Integer packageCount;

  /** Shipping cost. */
  private BigDecimal shippingCost;

  /** Currency code. */
  private String currency;

  /** Notes. */
  private String notes;

  /** Additional metadata. */
  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
  private Map<String, Object> metadata;
}
