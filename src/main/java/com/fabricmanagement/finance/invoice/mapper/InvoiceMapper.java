package com.fabricmanagement.finance.invoice.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.invoice.domain.Invoice;
import com.fabricmanagement.finance.invoice.domain.InvoiceLine;
import com.fabricmanagement.finance.invoice.domain.InvoiceTaxLine;
import com.fabricmanagement.finance.invoice.dto.InvoiceDto;
import com.fabricmanagement.finance.invoice.dto.InvoiceLineDto;
import com.fabricmanagement.finance.invoice.dto.InvoiceTaxLineDto;
import java.math.BigDecimal;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(config = MapStructConfig.class, unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface InvoiceMapper {

  @Mapping(target = "tradingPartnerName", ignore = true)
  @Mapping(target = "overdue", expression = "java(entity.isOverdue())")
  @Mapping(target = "daysOverdue", expression = "java(entity.getDaysOverdue())")
  @Mapping(target = "invoiceType", expression = "java(entity.getInvoiceType().name())")
  @Mapping(target = "status", expression = "java(entity.getStatus().name())")
  @Mapping(target = "paymentStatus", expression = "java(entity.getPaymentStatus().name())")
  @Mapping(target = "amountCredited", expression = "java(map(entity.getAmountCredited()))")
  @Mapping(target = "taxBreakdown", source = "taxLines")
  InvoiceDto toDto(Invoice entity);

  List<InvoiceDto> toDtoList(List<Invoice> entities);

  InvoiceLineDto toLineDto(InvoiceLine line);

  List<InvoiceLineDto> toLineDtoList(List<InvoiceLine> lines);

  InvoiceTaxLineDto toTaxLineDto(InvoiceTaxLine taxLine);

  List<InvoiceTaxLineDto> toTaxLineDtoList(List<InvoiceTaxLine> taxLines);

  default BigDecimal map(Money value) {
    return value != null ? value.getAmount() : null;
  }
}
