package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.sales.common.app.WorkScopeResolver;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.CustomerProductRequest;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestEvaluation;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestRevision;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestStatus;
import com.fabricmanagement.sales.orderintake.dto.CustomerRequestDtos;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerProductRequestRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerRequestEvaluationRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerRequestRevisionRepository;
import com.fabricmanagement.sales.salesorder.app.OrderWorkService;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkAssignment;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.dto.OrderWorkDtos;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planning's side of a custom request (SOI K15, R17): the evaluation queue, the evaluation and the
 * solution revisions presented to the customer. A new product card is created with the existing
 * product endpoints; a revision only references it.
 */
@Service
@RequiredArgsConstructor
public class CustomerRequestEvaluationService {

  private static final EnumSet<CustomerRequestStatus> QUEUE =
      EnumSet.of(
          CustomerRequestStatus.OPEN,
          CustomerRequestStatus.NEEDS_INFO,
          CustomerRequestStatus.CUSTOMER_REJECTED);

  private final CustomerProductRequestRepository requests;
  private final CustomerRequestEvaluationRepository evaluations;
  private final CustomerRequestRevisionRepository revisions;
  private final ProductSalesDefinitionQueryService products;
  private final CustomerRequestViews views;
  private final OrderWorkService work;
  private final SalesOrderRepository orders;
  private final Clock clock;

  /**
   * Requests waiting for planning that the user may see. A request on an order is that order's
   * planning work: it is seen and handled within the scope over the order's planning and while the
   * evaluation is open. A request on no order is evaluated by anyone with the production write
   * permission (handled separately for now).
   */
  @Transactional(readOnly = true)
  public List<CustomerRequestDtos.EvaluationItem> queue(UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    List<CustomerProductRequest> waiting =
        requests.findByTenantIdAndStatusInAndIsActiveTrueOrderByRecordedAtAscIdAsc(tenantId, QUEUE);
    Set<UUID> orderIds =
        waiting.stream()
            .map(CustomerProductRequest::getSalesOrderId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<UUID, OrderWorkAssignment> planning = work.assignments(OrderWorkKind.PLANNING, orderIds);
    Map<UUID, SalesOrder> byId =
        orders.findAllById(orderIds).stream()
            .filter(order -> tenantId.equals(order.getTenantId()))
            .collect(Collectors.toMap(SalesOrder::getId, Function.identity()));
    WorkScopeResolver.WorkActor viewer = work.actor(actor, false);
    OrderWorkService.ViewContext context = work.viewContext(actor);
    List<CustomerRequestDtos.EvaluationItem> result = new ArrayList<>();
    for (CustomerProductRequest request : waiting) {
      UUID orderId = request.getSalesOrderId();
      if (orderId == null) {
        String reason =
            viewer.scope(PermissionKey.PRODUCTION_WRITE) == null
                ? OrderWorkService.NO_PERMISSION
                : null;
        result.add(item(request, null, null, reason));
        continue;
      }
      SalesOrder order = byId.get(orderId);
      OrderWorkAssignment assignment = planning.get(orderId);
      OrderWorkService.WorkAccess access = work.access(assignment, OrderWorkKind.PLANNING, viewer);
      if (order == null || !access.visible()) {
        continue;
      }
      String reason = access.workReason() != null ? access.workReason() : stageReason(order);
      result.add(item(request, order.getOrderNumber(), context.of(assignment), reason));
    }
    return result;
  }

  @Transactional(readOnly = true)
  public CustomerRequestDtos.RequestDto get(UUID requestId, UUID actor) {
    return views.view(readable(requestId, actor));
  }

  /** The request if the user may see it: a request on an order needs scope over its planning. */
  @Transactional(readOnly = true)
  public CustomerProductRequest readable(UUID requestId, UUID actor) {
    CustomerProductRequest request = load(requestId);
    if (request.getSalesOrderId() != null) {
      OrderWorkAssignment assignment = work.find(request.getSalesOrderId(), OrderWorkKind.PLANNING);
      if (!work.access(assignment, OrderWorkKind.PLANNING, work.actor(actor, false)).visible()) {
        throw OrderIntakeException.notFound("Custom request", requestId);
      }
    }
    return request;
  }

  @Transactional
  public CustomerRequestDtos.RequestDto evaluate(
      UUID requestId, CustomerRequestDtos.EvaluateRequest input, UUID actor) {
    CustomerProductRequest request = writable(requestId, actor);
    evaluations.save(
        CustomerRequestEvaluation.record(
            request.getId(), input.outcome(), input.note(), actor, clock.instant()));
    request.evaluated(input.outcome());
    return views.view(requests.save(request));
  }

  @Transactional
  public CustomerRequestDtos.RequestDto propose(
      UUID requestId, CustomerRequestDtos.ProposeRevision input, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    CustomerProductRequest request = writable(requestId, actor);
    if (products.find(tenantId, input.productId()).isEmpty()) {
      throw OrderIntakeException.productNotAvailable(input.productId());
    }
    revisions
        .findByTenantIdAndRequestIdOrderByRevisionNoDesc(tenantId, request.getId())
        .forEach(
            previous -> {
              previous.supersede();
              revisions.save(previous);
            });
    int revisionNo = request.nextRevision();
    revisions.save(
        CustomerRequestRevision.propose(
            request.getId(),
            revisionNo,
            input.solution(),
            input.productId(),
            input.summary(),
            input.counterSampleNote(),
            actor,
            clock.instant()));
    return views.view(requests.save(request));
  }

  /**
   * The request if the user may evaluate it now. On an order: the production permission (checked at
   * the endpoint), scope over the order's planning, and an open evaluation of the order — the same
   * rules as any other planning input, so another planner cannot rewrite its solutions.
   */
  private CustomerProductRequest writable(UUID requestId, UUID actor) {
    CustomerProductRequest request = load(requestId);
    UUID orderId = request.getSalesOrderId();
    if (orderId != null) {
      SalesOrder order =
          orders
              .lockByTenantIdAndId(TenantContext.requireTenantId(), orderId)
              .orElseThrow(() -> OrderIntakeException.notFound("Custom request", requestId));
      try {
        work.requireWork(orderId, OrderWorkKind.PLANNING, actor);
      } catch (com.fabricmanagement.common.infrastructure.web.exception.NotFoundException hidden) {
        throw OrderIntakeException.notFound("Custom request", requestId);
      }
      order.assertAcceptsPlanningInput();
    }
    return request;
  }

  private static String stageReason(SalesOrder order) {
    try {
      order.assertAcceptsPlanningInput();
      return null;
    } catch (OrderDomainException refused) {
      return refused.getErrorCode();
    }
  }

  private CustomerRequestDtos.EvaluationItem item(
      CustomerProductRequest request,
      String orderNumber,
      OrderWorkDtos.AssignmentView assignment,
      String reason) {
    return new CustomerRequestDtos.EvaluationItem(
        views.view(request),
        orderNumber,
        assignment,
        List.of(
            OrderWorkDtos.Capability.of(OrderWorkDtos.Action.EVALUATE_REQUEST, reason),
            OrderWorkDtos.Capability.of(OrderWorkDtos.Action.PROPOSE_REVISION, reason)));
  }

  private CustomerProductRequest load(UUID requestId) {
    return requests
        .findByTenantIdAndIdAndIsActiveTrue(TenantContext.requireTenantId(), requestId)
        .orElseThrow(() -> OrderIntakeException.notFound("Custom request", requestId));
  }
}
