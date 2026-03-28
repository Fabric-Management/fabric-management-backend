package com.fabricmanagement.sales.sample.dto;

import com.fabricmanagement.offline.domain.OfflineMetadata;
import com.fabricmanagement.sales.sample.domain.DeliveryMethod;
import com.fabricmanagement.sales.sample.domain.SampleRequestStatus;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Value;

@Value
public class SampleRequestDto {
  UUID id;
  UUID customerId;
  UUID materialId;
  BigDecimal requestedQty;
  String unit;
  DeliveryMethod deliveryMethod;
  String deliveryAddress;
  SampleRequestStatus status;
  UUID salesOrderId;
  String notes;
  boolean isActive;
  OfflineMetadata offlineMetadata;
}
