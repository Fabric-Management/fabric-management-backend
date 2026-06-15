package com.fabricmanagement.finance.payment.mapper;

import com.fabricmanagement.common.infrastructure.mapping.MapStructConfig;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.finance.common.app.SettlementFxResult;
import com.fabricmanagement.finance.payment.domain.Payment;
import com.fabricmanagement.finance.payment.domain.PaymentAllocation;
import com.fabricmanagement.finance.payment.dto.PaymentAllocationDto;
import com.fabricmanagement.finance.payment.dto.PaymentDto;
import java.math.BigDecimal;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(config = MapStructConfig.class, unmappedSourcePolicy = ReportingPolicy.IGNORE)
public interface PaymentMapper {

  @Mapping(target = "direction", expression = "java(entity.getDirection().name())")
  @Mapping(target = "method", expression = "java(entity.getMethod().name())")
  @Mapping(target = "status", expression = "java(entity.getStatus().name())")
  @Mapping(target = "allocatedAmount", expression = "java(map(entity.getAllocatedAmount()))")
  @Mapping(target = "unallocatedAmount", expression = "java(map(entity.getUnallocatedAmount()))")
  @Mapping(target = "currency", expression = "java(entity.getCurrency())")
  @Mapping(target = "amount", expression = "java(map(entity.getAmount()))")
  PaymentDto toDto(Payment entity);

  List<PaymentDto> toDtoList(List<Payment> entities);

  @Mapping(
      target = "currency",
      expression = "java(entity.getAmount().getCurrency().getCurrencyCode())")
  @Mapping(target = "amount", expression = "java(map(entity.getAmount()))")
  @Mapping(target = "reportingCurrency", ignore = true)
  @Mapping(target = "realizedFxGainLoss", ignore = true)
  @Mapping(target = "settlementExchangeRate", ignore = true)
  @Mapping(target = "settlementExchangeRateDate", ignore = true)
  PaymentAllocationDto toAllocationDto(PaymentAllocation entity);

  List<PaymentAllocationDto> toAllocationDtoList(List<PaymentAllocation> entities);

  default PaymentAllocationDto toAllocationDto(
      PaymentAllocation entity, SettlementFxResult fxResult) {
    return new PaymentAllocationDto(
        entity.getId(),
        entity.getPaymentId(),
        entity.getInvoiceId(),
        map(entity.getAmount()),
        entity.getAmount().getCurrency().getCurrencyCode(),
        entity.getAllocatedAt(),
        fxResult.reportingCurrency(),
        fxResult.realizedFxGainLoss(),
        fxResult.settlementExchangeRate(),
        fxResult.settlementExchangeRateDate());
  }

  default BigDecimal map(Money value) {
    return value != null ? value.getAmount() : null;
  }
}
