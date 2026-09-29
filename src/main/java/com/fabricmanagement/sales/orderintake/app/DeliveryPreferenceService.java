package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.orderintake.domain.OrderDeliveryPreference;
import com.fabricmanagement.sales.orderintake.domain.PartialDeliveryPreference;
import com.fabricmanagement.sales.orderintake.dto.CustomerRequestDtos;
import com.fabricmanagement.sales.orderintake.infra.repository.OrderDeliveryPreferenceRepository;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records whether the customer accepts partial delivery of an order (SOI A10). */
@Service
@RequiredArgsConstructor
public class DeliveryPreferenceService {

  private final OrderIntakeAccess access;
  private final OrderDeliveryPreferenceRepository repository;
  private final Clock clock;

  @Transactional
  public CustomerRequestDtos.DeliveryPreferenceDto record(
      UUID orderId, CustomerRequestDtos.RecordDeliveryPreference input, UUID actor) {
    SalesOrder order = access.writableOrder(orderId, actor);
    OrderDeliveryPreference preference =
        repository
            .findByTenantIdAndSalesOrderId(TenantContext.requireTenantId(), order.getId())
            .orElseGet(() -> OrderDeliveryPreference.of(order.getId()));
    preference.record(
        input.preference(),
        input.customerContact(),
        input.channel(),
        input.decidedAt(),
        actor,
        clock.instant());
    return toDto(order.getId(), repository.save(preference));
  }

  @Transactional(readOnly = true)
  public CustomerRequestDtos.DeliveryPreferenceDto get(UUID orderId, UUID actor) {
    SalesOrder order = access.readableOrder(orderId, actor);
    return toDto(
        order.getId(),
        repository
            .findByTenantIdAndSalesOrderId(TenantContext.requireTenantId(), order.getId())
            .orElse(null));
  }

  /** UNKNOWN when nothing was recorded: partial shipment is never assumed (A10). */
  public PartialDeliveryPreference preferenceOf(UUID orderId) {
    return repository
        .findByTenantIdAndSalesOrderId(TenantContext.requireTenantId(), orderId)
        .map(OrderDeliveryPreference::getPreference)
        .orElse(PartialDeliveryPreference.UNKNOWN);
  }

  private static CustomerRequestDtos.DeliveryPreferenceDto toDto(
      UUID orderId, OrderDeliveryPreference value) {
    if (value == null) {
      return new CustomerRequestDtos.DeliveryPreferenceDto(
          orderId, PartialDeliveryPreference.UNKNOWN, null, null, null, null, null);
    }
    return new CustomerRequestDtos.DeliveryPreferenceDto(
        orderId,
        value.getPreference(),
        value.getCustomerContact(),
        value.getChannel(),
        value.getDecidedAt(),
        value.getRecordedBy(),
        value.getRecordedAt());
  }
}
