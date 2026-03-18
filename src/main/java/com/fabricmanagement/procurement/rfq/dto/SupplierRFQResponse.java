package com.fabricmanagement.procurement.rfq.dto;

import com.fabricmanagement.procurement.rfq.domain.RfqRecipientStatus;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQ;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQModuleType;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQStatus;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;

/**
 * Fix #1 — API response DTO: domain entity'yi doğrudan dönmek yerine sadece gereken alanları
 * istemciye açar; tenantId, version, isActive gibi internal alanlar sızmaz.
 */
@Value
@Builder
public class SupplierRFQResponse {

  UUID id;
  String rfqNumber;
  UUID workOrderId;
  SupplierRFQModuleType moduleType;
  SupplierRFQType rfqType;
  SupplierRFQStatus status;
  Instant deadline;
  String notes;
  List<RfqLineResponse> lines;
  List<RecipientResponse> recipients;
  Instant createdAt;

  @Value
  @Builder
  public static class RfqLineResponse {
    UUID id;
    UUID materialId;
    String productDesc;
    BigDecimal requestedQty;
    String unit;
    String moduleSpecs;
  }

  @Value
  @Builder
  public static class RecipientResponse {
    UUID id;
    UUID tradingPartnerId;
    Instant sentAt;
    RfqRecipientStatus status;
    Instant responseDeadline;
  }

  /** Fabrika metodu — entity + lines aynı transaction içinde okunmalı (LazyInit.Ex önleme). */
  public static SupplierRFQResponse from(SupplierRFQ rfq) {
    List<RfqLineResponse> lineResps =
        rfq.getLines().stream()
            .map(
                l ->
                    RfqLineResponse.builder()
                        .id(l.getId())
                        .materialId(l.getMaterialId())
                        .productDesc(l.getProductDesc())
                        .requestedQty(l.getRequestedQty())
                        .unit(l.getUnit())
                        .moduleSpecs(l.getModuleSpecs())
                        .build())
            .toList();

    List<RecipientResponse> recipientResps =
        rfq.getRecipients().stream()
            .map(
                r ->
                    RecipientResponse.builder()
                        .id(r.getId())
                        .tradingPartnerId(r.getTradingPartnerId())
                        .sentAt(r.getSentAt())
                        .status(r.getStatus())
                        .responseDeadline(r.getResponseDeadline())
                        .build())
            .toList();

    return SupplierRFQResponse.builder()
        .id(rfq.getId())
        .rfqNumber(rfq.getRfqNumber())
        .workOrderId(rfq.getWorkOrderId())
        .moduleType(rfq.getModuleType())
        .rfqType(rfq.getRfqType())
        .status(rfq.getStatus())
        .deadline(rfq.getDeadline())
        .notes(rfq.getNotes())
        .lines(lineResps)
        .recipients(recipientResps)
        .createdAt(rfq.getCreatedAt())
        .build();
  }
}
