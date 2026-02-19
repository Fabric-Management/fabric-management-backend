package com.fabricmanagement.finance.invoice.dto;

import com.fabricmanagement.common.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/** DTO for Invoice entity. */
@Data
@Builder
public class InvoiceDto {
  private UUID id;
  private String uid;
  private UUID tradingPartnerId;
  private TradingPartnerDto tradingPartner;
  private String invoiceNumber;
  private String orderReference;
  private String externalReference;
  private InvoiceType invoiceType;
  private InvoiceStatus status;
  private LocalDate issueDate;
  private LocalDate dueDate;
  private LocalDate paymentDate;
  private BigDecimal subtotal;
  private BigDecimal taxAmount;
  private BigDecimal discountAmount;
  private BigDecimal totalAmount;
  private BigDecimal amountPaid;
  private BigDecimal amountDue;
  private String currency;
  private BigDecimal taxRate;
  private String billingAddress;
  private String notes;
  private Map<String, Object> metadata;
  private Boolean isActive;
  private Boolean isOverdue;
  private Long daysOverdue;
  private Instant createdAt;
  private Instant updatedAt;

  /** Create DTO from entity. */
  public static InvoiceDto from(Invoice invoice) {
    return from(invoice, null);
  }

  /** Create DTO from entity with partner info. */
  public static InvoiceDto from(Invoice invoice, TradingPartnerDto partner) {
    return InvoiceDto.builder()
        .id(invoice.getId())
        .uid(invoice.getUid())
        .tradingPartnerId(invoice.getTradingPartnerId())
        .tradingPartner(partner)
        .invoiceNumber(invoice.getInvoiceNumber())
        .orderReference(invoice.getOrderReference())
        .externalReference(invoice.getExternalReference())
        .invoiceType(invoice.getInvoiceType())
        .status(invoice.getStatus())
        .issueDate(invoice.getIssueDate())
        .dueDate(invoice.getDueDate())
        .paymentDate(invoice.getPaymentDate())
        .subtotal(invoice.getSubtotal())
        .taxAmount(invoice.getTaxAmount())
        .discountAmount(invoice.getDiscountAmount())
        .totalAmount(invoice.getTotalAmount())
        .amountPaid(invoice.getAmountPaid())
        .amountDue(invoice.getAmountDue())
        .currency(invoice.getCurrency())
        .taxRate(invoice.getTaxRate())
        .billingAddress(invoice.getBillingAddress())
        .notes(invoice.getNotes())
        .metadata(invoice.getMetadata())
        .isActive(invoice.getIsActive())
        .isOverdue(invoice.isOverdue())
        .daysOverdue(invoice.getDaysOverdue())
        .createdAt(invoice.getCreatedAt())
        .updatedAt(invoice.getUpdatedAt())
        .build();
  }
}
