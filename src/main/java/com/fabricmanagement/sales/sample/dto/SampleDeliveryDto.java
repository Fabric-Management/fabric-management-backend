package com.fabricmanagement.sales.sample.dto;

import com.fabricmanagement.sales.sample.domain.DeliveryMethod;
import java.time.Instant;
import java.util.UUID;
import lombok.Value;

@Value
public class SampleDeliveryDto {
  UUID id;
  UUID sampleRequestId;
  DeliveryMethod deliveryMethod;
  String trackingNumber;
  String cargoCompany;
  UUID deliveredById;
  Instant dispatchedAt;
  Instant deliveredAt;
  String recipientName;
  String deliveryPhoto;
  boolean isActive;
}
