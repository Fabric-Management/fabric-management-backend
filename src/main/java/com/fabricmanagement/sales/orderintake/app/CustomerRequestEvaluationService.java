package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.core.api.query.ProductSalesDefinitionQueryService;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.CustomerProductRequest;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestEvaluation;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestRevision;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestStatus;
import com.fabricmanagement.sales.orderintake.dto.CustomerRequestDtos;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerProductRequestRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerRequestEvaluationRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerRequestRevisionRepository;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
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
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<CustomerRequestDtos.RequestDto> queue() {
    return requests
        .findByTenantIdAndStatusInAndIsActiveTrueOrderByRecordedAtAscIdAsc(
            TenantContext.requireTenantId(), QUEUE)
        .stream()
        .map(views::view)
        .toList();
  }

  @Transactional(readOnly = true)
  public CustomerRequestDtos.RequestDto get(UUID requestId) {
    return views.view(load(requestId));
  }

  @Transactional
  public CustomerRequestDtos.RequestDto evaluate(
      UUID requestId, CustomerRequestDtos.EvaluateRequest input, UUID actor) {
    CustomerProductRequest request = load(requestId);
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
    CustomerProductRequest request = load(requestId);
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

  private CustomerProductRequest load(UUID requestId) {
    return requests
        .findByTenantIdAndIdAndIsActiveTrue(TenantContext.requireTenantId(), requestId)
        .orElseThrow(() -> OrderIntakeException.notFound("Custom request", requestId));
  }
}
