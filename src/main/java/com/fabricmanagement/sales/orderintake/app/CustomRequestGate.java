package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.orderintake.domain.AcceptanceTerms;
import com.fabricmanagement.sales.orderintake.domain.CustomerProductRequest;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerProductRequestRepository;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Custom-request conditions of confirming an order (SOI R18, A10, A11, N01): no unfinished request
 * may ride along, and a line made from a request needs the customer's approval of its current
 * terms.
 */
@Component
@RequiredArgsConstructor
class CustomRequestGate {

  private final CustomerProductRequestRepository requests;

  List<ConfirmationGate.Block> blocks(SalesOrder order, List<SalesOrderLine> lines) {
    UUID tenantId = TenantContext.requireTenantId();
    List<ConfirmationGate.Block> blocks = new ArrayList<>();
    for (CustomerProductRequest request :
        requests.findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByRecordedAtAscIdAsc(
            tenantId, order.getId())) {
      if (!request.getStatus().isFinished()) {
        blocks.add(
            new ConfirmationGate.Block(
                null,
                "CUSTOM_REQUEST_OPEN",
                "Custom request "
                    + request.getUid()
                    + " is "
                    + request.getStatus()
                    + "; resolve it, close it or take it off this order",
                List.of()));
      }
    }
    if (lines.isEmpty()) {
      return blocks;
    }
    Map<UUID, SalesOrderLine> byId =
        lines.stream().collect(Collectors.toMap(SalesOrderLine::getId, Function.identity()));
    for (CustomerProductRequest request :
        requests.findByTenantIdAndResolvedLineIdInAndIsActiveTrue(tenantId, byId.keySet())) {
      SalesOrderLine line = byId.get(request.getResolvedLineId());
      if (!request.coversLine(AcceptanceTerms.fingerprint(line))) {
        blocks.add(
            new ConfirmationGate.Block(
                line.getId(),
                "SAMPLE_APPROVAL_STALE",
                "The line made from a custom request changed after the customer's approval",
                List.of()));
      }
    }
    return blocks;
  }
}
