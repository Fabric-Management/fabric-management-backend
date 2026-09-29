package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.orderintake.domain.CustomerToneAcceptance;
import com.fabricmanagement.sales.orderintake.dto.CustomerToneAcceptanceDto;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeRequests;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerToneAcceptanceRepository;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records that the customer saw and accepted a concrete shade difference between lots (SOI A04-b).
 * Tone only: it never clears width, weight, strength or quality requirements.
 */
@Service
@RequiredArgsConstructor
public class CustomerToneAcceptanceService {

  private final OrderIntakeAccess access;
  private final CustomerToneAcceptanceRepository repository;
  private final QuantityEvaluationService evaluation;
  private final IntakeAttachmentService attachments;
  private final com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository
      lines;

  @Transactional
  public CustomerToneAcceptanceDto record(
      UUID orderId, OrderIntakeRequests.RecordToneAcceptance request, UUID actor) {
    SalesOrder order = access.writableOrder(orderId, actor);
    var line = access.line(order, request.salesOrderLineId());
    var stock =
        evaluation
            .currentStock(order, line)
            .orElseThrow(
                () ->
                    com.fabricmanagement.sales.common.exception.OrderIntakeException.conflict(
                        "EVIDENCE_UNAVAILABLE", "The named lots cannot be verified for this line"));
    var lotIds =
        stock.lots().stream()
            .map(lot -> lot.batchId())
            .collect(java.util.stream.Collectors.toSet());
    if (!lotIds.containsAll(request.batchIds())) {
      throw com.fabricmanagement.sales.common.exception.OrderIntakeException.rule(
          "TONE_LOT_MISMATCH", "The named lots must belong to this line's stock distribution");
    }
    attachments.requireAttachmentOfCustomer(
        request.evidenceAttachmentId(), order.getTradingPartnerId());
    CustomerToneAcceptance saved =
        repository.save(
            CustomerToneAcceptance.record(
                order.getTradingPartnerId(),
                request.salesOrderLineId(),
                request.batchIds(),
                request.evidenceNote(),
                request.evidenceAttachmentId(),
                request.customerContact(),
                request.channel(),
                request.acceptedAt(),
                request.customerStatementConfirmed(),
                actor));
    return CustomerToneAcceptanceDto.from(saved);
  }

  @Transactional(readOnly = true)
  public List<CustomerToneAcceptanceDto> forOrder(UUID orderId, UUID actor) {
    SalesOrder order = access.readableOrder(orderId, actor);
    var lineIds =
        lines
            .findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(
                TenantContext.requireTenantId(), orderId)
            .stream()
            .map(line -> line.getId())
            .collect(java.util.stream.Collectors.toSet());
    return repository
        .findByTenantIdAndCustomerIdAndIsActiveTrue(
            TenantContext.requireTenantId(), order.getTradingPartnerId())
        .stream()
        .filter(value -> lineIds.contains(value.getSalesOrderLineId()))
        .map(CustomerToneAcceptanceDto::from)
        .toList();
  }
}
