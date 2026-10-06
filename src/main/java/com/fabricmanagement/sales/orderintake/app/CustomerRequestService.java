package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.common.exception.OrderIntakeException;
import com.fabricmanagement.sales.orderintake.domain.AcceptanceTerms;
import com.fabricmanagement.sales.orderintake.domain.CustomerProductRequest;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestDecision;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestRevision;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestStatus;
import com.fabricmanagement.sales.orderintake.dto.CustomerRequestDtos;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerProductRequestRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerRequestDecisionRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerRequestRevisionRepository;
import com.fabricmanagement.sales.salesorder.app.CatalogLineValidator;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The salesperson's side of a custom request (SOI D7): record, complete, present, record the
 * customer's answer and turn an approved request into an order line. Evaluation and revisions
 * belong to planning ({@link CustomerRequestEvaluationService}).
 */
@Service
@RequiredArgsConstructor
public class CustomerRequestService {

  private final OrderIntakeAccess access;
  private final CustomerProductRequestRepository requests;
  private final CustomerRequestRevisionRepository revisions;
  private final CustomerRequestDecisionRepository decisions;
  private final IntakeAttachmentService attachments;
  private final CustomerRequestViews views;
  private final CatalogLineValidator catalogLineValidator;
  private final SalesOrderLineRepository lines;
  private final com.fabricmanagement.sales.salesorder.app.SalesOrderRevision orderRevision;
  private final Clock clock;

  @Transactional
  public CustomerRequestDtos.RequestDto create(
      UUID orderId, CustomerRequestDtos.RequestInput input, UUID actor) {
    SalesOrder order = draft(access.writableOrder(orderId, actor));
    boolean hasFiles = input.attachmentIds() != null && !input.attachmentIds().isEmpty();
    CustomerProductRequest request =
        requests.save(
            CustomerProductRequest.record(
                order.getTradingPartnerId(),
                order.getId(),
                details(input),
                hasFiles,
                actor,
                clock.instant()));
    attachments.linkToRequest(
        order.getId(), order.getTradingPartnerId(), request.getId(), input.attachmentIds());
    return views.view(request);
  }

  @Transactional
  public CustomerRequestDtos.RequestDto update(
      UUID orderId, UUID requestId, CustomerRequestDtos.RequestInput input, UUID actor) {
    SalesOrder order = draft(access.writableOrder(orderId, actor));
    CustomerProductRequest request = attached(order, requestId);
    attachments.linkToRequest(
        order.getId(), order.getTradingPartnerId(), request.getId(), input.attachmentIds());
    request.update(details(input), attachments.requestHasFiles(request.getId()));
    return views.view(requests.save(request));
  }

  @Transactional(readOnly = true)
  public List<CustomerRequestDtos.RequestDto> forOrder(UUID orderId, UUID actor) {
    SalesOrder order = access.readableOrder(orderId, actor);
    return requests
        .findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByRecordedAtAscIdAsc(
            TenantContext.requireTenantId(), order.getId())
        .stream()
        .map(views::view)
        .toList();
  }

  /** The customer's open requests not attached to any order. */
  @Transactional(readOnly = true)
  public List<CustomerRequestDtos.RequestDto> unattached(UUID orderId, UUID actor) {
    SalesOrder order = access.readableOrder(orderId, actor);
    return requests
        .findByTenantIdAndCustomerIdAndSalesOrderIdIsNullAndStatusNotInAndIsActiveTrueOrderByRecordedAtAsc(
            TenantContext.requireTenantId(),
            order.getTradingPartnerId(),
            EnumSet.of(CustomerRequestStatus.RESOLVED, CustomerRequestStatus.CLOSED))
        .stream()
        .map(views::view)
        .toList();
  }

  @Transactional
  public CustomerRequestDtos.RequestDto attach(UUID orderId, UUID requestId, UUID actor) {
    SalesOrder order = draft(access.writableOrder(orderId, actor));
    CustomerProductRequest request = load(requestId);
    if (!request.getCustomerId().equals(order.getTradingPartnerId())) {
      throw OrderIntakeException.notFound("Custom request", requestId);
    }
    if (request.getSalesOrderId() != null) {
      throw OrderIntakeException.conflict(
          "REQUEST_ALREADY_ATTACHED", "The request already belongs to an order");
    }
    request.attachTo(order.getId());
    return views.view(requests.save(request));
  }

  /**
   * Takes an unfinished request off the draft so its ready catalogue lines can be confirmed (A10:
   * blocking is per line). Shipping is a separate question: unless the customer allowed partial
   * delivery, the origin order's delivery outlook keeps waiting for the request.
   */
  @Transactional
  public CustomerRequestDtos.RequestDto detach(UUID orderId, UUID requestId, UUID actor) {
    SalesOrder order = draft(access.writableOrder(orderId, actor));
    CustomerProductRequest request = attached(order, requestId);
    request.detach();
    return views.view(requests.save(request));
  }

  @Transactional
  public CustomerRequestDtos.RequestDto close(UUID orderId, UUID requestId, UUID actor) {
    SalesOrder order = draft(access.writableOrder(orderId, actor));
    CustomerProductRequest request = attached(order, requestId);
    request.close();
    return views.view(requests.save(request));
  }

  @Transactional
  public CustomerRequestDtos.RequestDto markSent(
      UUID orderId, UUID requestId, int revisionNo, UUID actor) {
    SalesOrder order = access.writableOrder(orderId, actor);
    CustomerProductRequest request = attached(order, requestId);
    CustomerRequestRevision revision = latest(request, revisionNo);
    revision.markSent(clock.instant());
    revisions.save(revision);
    request.sent();
    return views.view(requests.save(request));
  }

  /** Records the customer's answer to the latest revision (SOI A11). */
  @Transactional
  public CustomerRequestDtos.RequestDto decide(
      UUID orderId,
      UUID requestId,
      int revisionNo,
      CustomerRequestDtos.RecordDecision input,
      UUID actor) {
    SalesOrder order = access.writableOrder(orderId, actor);
    // The customer's answer defines what is ordered; it is not recorded while planning evaluates.
    order.assertCommercialContentEditable();
    CustomerProductRequest request = attached(order, requestId);
    CustomerRequestRevision revision = latest(request, revisionNo);
    attachments.requireAttachmentOfCustomer(input.evidenceAttachmentId(), request.getCustomerId());
    if (request.getResolvedLineId() != null) {
      SalesOrderLine resolved =
          lines.lockAllForOrder(TenantContext.requireTenantId(), orderId).stream()
              .filter(line -> line.getId().equals(request.getResolvedLineId()))
              .findFirst()
              .orElseThrow(
                  () ->
                      OrderIntakeException.notFound("Resolved line", request.getResolvedLineId()));
      if (input.expectedLineVersion() == null
          || !input.expectedLineVersion().equals(resolved.getVersion())) {
        throw OrderIntakeException.conflict(
            "STALE_VERSION",
            "The final line changed; show the current terms to the customer again");
      }
    }
    String terms = currentTerms(request);
    decisions.save(
        CustomerRequestDecision.record(
            request.getId(),
            revision.getId(),
            input.outcome(),
            terms,
            input.customerContact(),
            input.channel(),
            input.decidedAt(),
            input.note(),
            input.evidenceAttachmentId(),
            input.customerStatementConfirmed(),
            actor,
            clock.instant()));
    revision.decided(input.outcome());
    revisions.save(revision);
    request.decided(input.outcome(), terms);
    return views.view(requests.save(request));
  }

  /**
   * Turns an approved request into an order line of the approved product (SOI R16, R18, N01).
   * Quantity and unit are required now; the line passes the same catalogue checks as any line.
   */
  @Transactional
  public CustomerRequestDtos.RequestDto resolve(
      UUID orderId, UUID requestId, CustomerRequestDtos.ResolveRequest input, UUID actor) {
    SalesOrder writable = access.writableOrder(orderId, actor);
    // Lock order (CEDIT-02 §5.5): the order row first, and the draft judged on its current state,
    // before the new line is checked against the other lines and written.
    orderRevision.lockFresh(writable);
    access.requireActive(writable);
    SalesOrder order = draft(writable);
    CustomerProductRequest request = attached(order, requestId);
    if (!request.hasQuantity()) {
      throw OrderIntakeException.rule(
          "QUANTITY_REQUIRED", "Quantity and unit are required before the request becomes a line");
    }
    if (request.getStatus() != CustomerRequestStatus.CUSTOMER_APPROVED
        || !views.approvalCoversCurrentTerms(request)) {
      throw OrderIntakeException.rule(
          "SAMPLE_APPROVAL_REQUIRED",
          "The customer's approval of the latest revision must cover the current request");
    }
    CustomerRequestRevision revision =
        views
            .latestRevision(request)
            .orElseThrow(
                () -> OrderIntakeException.rule("SAMPLE_APPROVAL_REQUIRED", "No revision"));
    BigDecimal price = price(input);
    SalesOrderLine line =
        SalesOrderLine.builder()
            .salesOrderId(order.getId())
            .productId(revision.getProductId())
            .productDesc(request.getDescription())
            .requestedQty(request.getRequestedQty())
            .unit(request.getUnit())
            .currency(currency(input))
            .unitPriceAmount(price)
            .lineStatus(SalesOrderLineStatus.PENDING)
            .colorId(input.colorId())
            .finishedWidth(input.finishedWidth())
            .finishedWidthUnit(
                input.finishedWidthUnit() == null
                    ? null
                    : input.finishedWidthUnit().trim().toUpperCase(Locale.ROOT))
            .requestedDeliveryDate(
                input.requestedDeliveryDate() != null
                    ? input.requestedDeliveryDate()
                    : request.getRequestedDeliveryDate())
            .build();
    List<SalesOrderLine> all =
        new ArrayList<>(lines.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId()));
    all.add(line);
    catalogLineValidator.validate(
        TenantContext.requireTenantId(), order.getTradingPartnerId(), all);
    SalesOrderLine saved = lines.save(line);
    orderRevision.linesChanged(order);
    request.resolve(saved.getId());
    return views.view(requests.save(request));
  }

  private String currentTerms(CustomerProductRequest request) {
    if (request.getResolvedLineId() == null) {
      return request.requestTerms();
    }
    return lines
        .findByTenantIdAndId(TenantContext.requireTenantId(), request.getResolvedLineId())
        .map(AcceptanceTerms::fingerprint)
        .orElseThrow(
            () -> OrderIntakeException.notFound("Order line", request.getResolvedLineId()));
  }

  private CustomerRequestRevision latest(CustomerProductRequest request, int revisionNo) {
    if (revisionNo != request.getCurrentRevisionNo()) {
      throw OrderIntakeException.conflict(
          "REVISION_SUPERSEDED", "Only the latest revision can be presented or decided");
    }
    return views
        .latestRevision(request)
        .orElseThrow(() -> OrderIntakeException.notFound("Revision", revisionNo));
  }

  /** The agreed currency of the new line; lines of one order may differ in currency. */
  private static String currency(CustomerRequestDtos.ResolveRequest input) {
    return input.currency() == null ? null : input.currency().toUpperCase(java.util.Locale.ROOT);
  }

  /** The agreed unit price exactly as entered (four decimals); Money would round it. */
  private static BigDecimal price(CustomerRequestDtos.ResolveRequest input) {
    if (input.unitPrice() == null) {
      return null;
    }
    if (input.currency() == null) {
      throw OrderIntakeException.rule(
          "CURRENCY_REQUIRED", "A priced line must name its agreed currency");
    }
    return input.unitPrice();
  }

  private CustomerProductRequest attached(SalesOrder order, UUID requestId) {
    CustomerProductRequest request = load(requestId);
    if (!order.getId().equals(request.getSalesOrderId())) {
      throw OrderIntakeException.notFound("Custom request", requestId);
    }
    return request;
  }

  private CustomerProductRequest load(UUID requestId) {
    return requests
        .findByTenantIdAndIdAndIsActiveTrue(TenantContext.requireTenantId(), requestId)
        .orElseThrow(() -> OrderIntakeException.notFound("Custom request", requestId));
  }

  private static SalesOrder draft(SalesOrder order) {
    if (order.getStatus() != OrderStatus.DRAFT) {
      throw OrderIntakeException.conflict(
          "ORDER_NOT_DRAFT", "Custom requests change only while the order is a draft");
    }
    order.assertCommercialContentEditable();
    return order;
  }

  static CustomerProductRequest.Details details(CustomerRequestDtos.RequestInput input) {
    return new CustomerProductRequest.Details(
        input.description(),
        input.referenceProductId(),
        input.requestedQty(),
        input.unit(),
        input.requestedColorNote(),
        input.requestedWidth(),
        input.requestedWidthUnit(),
        input.requestedDeliveryDate(),
        input.sampleReceivedAt(),
        input.sampleNote());
  }
}
