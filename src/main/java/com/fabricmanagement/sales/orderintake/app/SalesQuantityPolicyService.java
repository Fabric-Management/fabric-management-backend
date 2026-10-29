package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.common.dto.SalesQuantityTermsDto;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.SalesQuantityPolicyOperation;
import com.fabricmanagement.sales.orderintake.dto.SalesQuantityPolicyResultDto;
import com.fabricmanagement.sales.orderintake.infra.repository.SalesQuantityPolicyOperationRepository;
import com.fabricmanagement.sales.quote.dto.QuoteRollSelectionRequests;
import com.fabricmanagement.sales.salesorder.app.SalesOrderLeaseGuard;
import com.fabricmanagement.sales.salesorder.app.SalesOrderRevision;
import com.fabricmanagement.sales.salesorder.domain.OrderEditKey;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SalesQuantityPolicyService {
  private final OrderIntakeAccess access;
  private final SalesOrderRevision revision;
  private final SalesOrderLeaseGuard leases;
  private final SalesQuantityPolicyOperationRepository operations;
  private final Clock clock;
  private final jakarta.persistence.EntityManager entityManager;
  private final ProvisionalStockLifecycleService provisional;

  @Transactional
  public SalesQuantityPolicyResultDto record(
      UUID orderId, UUID lineId, QuoteRollSelectionRequests.Policy request, UUID actor) {
    var order = access.writableOrderFresh(orderId, actor);
    revision.lockFresh(order);
    access.requireActive(order);
    access.writableOrderFresh(orderId, actor);
    entityManager
        .createNativeQuery(
            "select 1 from (select pg_advisory_xact_lock(hashtextextended(:key, 0))) held")
        .setParameter(
            "key",
            TenantContext.requireTenantId() + ":QUANTITY_POLICY_OPERATION:" + request.operationId())
        .getSingleResult();
    var prior =
        operations.findByTenantIdAndOperationId(
            TenantContext.requireTenantId(), request.operationId());
    if (prior.isPresent()) {
      var row = prior.get();
      if (!row.getOrderId().equals(orderId)
          || !row.getLineId().equals(lineId)
          || !row.getActorId().equals(actor)
          || !row.getExpectedVersion().equals(request.expectedVersion())
          || !row.getReason().equals(request.reason())
          || row.getResult().policy() != request.quantityPolicy())
        throw OrderIntakeException.conflict(
            "IDEMPOTENCY_KEY_REUSED", "The operation belongs to another quantity decision");
      return new SalesQuantityPolicyResultDto(
          orderId, lineId, row.getOrderVersion(), row.getResult());
    }
    if (order.getStatus() != OrderStatus.DRAFT
        || !Objects.equals(order.getVersion(), request.expectedVersion()))
      throw OrderIntakeException.conflict(
          "STALE_VERSION", "Reload the editable draft before changing its quantity decision");
    order.assertCommercialContentEditable();
    leases.assertLineFieldFree(order, lineId, OrderEditKey.LINE_QUANTITY, actor);
    var line = access.line(order, lineId);
    revision.lockFresh(line);
    var previous = line.getQuantityPolicy();
    line.recordQuantityPolicy(request.quantityPolicy(), actor, clock.instant(), request.reason());
    if (previous != line.getQuantityPolicy()) provisional.ownerLine(lineId);
    revision.linesChanged(order);
    var result = SalesQuantityTermsDto.from(line.getQuantityTerms());
    operations.save(
        SalesQuantityPolicyOperation.record(
            orderId,
            lineId,
            actor,
            request.operationId(),
            request.expectedVersion(),
            order.getVersion(),
            request.reason(),
            result));
    return new SalesQuantityPolicyResultDto(orderId, lineId, order.getVersion(), result);
  }
}
