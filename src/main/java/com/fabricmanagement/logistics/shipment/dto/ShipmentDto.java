package com.fabricmanagement.logistics.shipment.dto;

import com.fabricmanagement.logistics.shipment.domain.Shipment;
import com.fabricmanagement.logistics.shipment.domain.ShipmentStatus;
import com.fabricmanagement.logistics.shipment.domain.ShipmentType;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/** DTO for Shipment entity. */
@Data
@Builder
public class ShipmentDto {
  private UUID id;
  private String uid;
  private UUID tradingPartnerId;
  private TradingPartnerDto tradingPartner;
  private String shipmentNumber;
  private String orderReference;
  private ShipmentType shipmentType;
  private ShipmentStatus status;
  private String carrierName;
  private String carrierCode;
  private String trackingNumber;
  private String trackingUrl;
  private LocalDate shipDate;
  private LocalDate estimatedDeliveryDate;
  private LocalDate actualDeliveryDate;
  private Instant pickedUpAt;
  private Instant deliveredAt;
  private String originAddress;
  private String destinationAddress;
  private BigDecimal totalWeight;
  private String weightUnit;
  private Integer packageCount;
  private BigDecimal shippingCost;
  private String currency;
  private String deliveryProof;
  private String recipientName;
  private String notes;
  private Map<String, Object> metadata;
  private Boolean isActive;
  private Boolean isLate;
  private Instant createdAt;
  private Instant updatedAt;

  /** Create DTO from entity. */
  public static ShipmentDto from(Shipment shipment) {
    return from(shipment, null);
  }

  /** Create DTO from entity with partner info. */
  public static ShipmentDto from(Shipment shipment, TradingPartnerDto partner) {
    return ShipmentDto.builder()
        .id(shipment.getId())
        .uid(shipment.getUid())
        .tradingPartnerId(shipment.getTradingPartnerId())
        .tradingPartner(partner)
        .shipmentNumber(shipment.getShipmentNumber())
        .orderReference(shipment.getOrderReference())
        .shipmentType(shipment.getShipmentType())
        .status(shipment.getStatus())
        .carrierName(shipment.getCarrierName())
        .carrierCode(shipment.getCarrierCode())
        .trackingNumber(shipment.getTrackingNumber())
        .trackingUrl(shipment.getTrackingUrl())
        .shipDate(shipment.getShipDate())
        .estimatedDeliveryDate(shipment.getEstimatedDeliveryDate())
        .actualDeliveryDate(shipment.getActualDeliveryDate())
        .pickedUpAt(shipment.getPickedUpAt())
        .deliveredAt(shipment.getDeliveredAt())
        .originAddress(shipment.getOriginAddress())
        .destinationAddress(shipment.getDestinationAddress())
        .totalWeight(shipment.getTotalWeight())
        .weightUnit(shipment.getWeightUnit())
        .packageCount(shipment.getPackageCount())
        .shippingCost(shipment.getShippingCost())
        .currency(shipment.getCurrency())
        .deliveryProof(shipment.getDeliveryProof())
        .recipientName(shipment.getRecipientName())
        .notes(shipment.getNotes())
        .metadata(shipment.getMetadata())
        .isActive(shipment.getIsActive())
        .isLate(shipment.isLate())
        .createdAt(shipment.getCreatedAt())
        .updatedAt(shipment.getUpdatedAt())
        .build();
  }
}
