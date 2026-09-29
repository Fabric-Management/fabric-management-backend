package com.fabricmanagement.sales.orderintake.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.orderintake.domain.AcceptanceTerms;
import com.fabricmanagement.sales.orderintake.domain.CustomerProductRequest;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestDecision;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestDecisionOutcome;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestEvaluation;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestRevision;
import com.fabricmanagement.sales.orderintake.dto.CustomerRequestDtos;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerRequestDecisionRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerRequestEvaluationRepository;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerRequestRevisionRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds the read view of a custom request and answers "does the approval still hold?". */
@Component
@RequiredArgsConstructor
class CustomerRequestViews {

  private final CustomerRequestRevisionRepository revisions;
  private final CustomerRequestDecisionRepository decisions;
  private final CustomerRequestEvaluationRepository evaluations;
  private final IntakeAttachmentService attachments;
  private final SalesOrderLineRepository lines;

  CustomerRequestDtos.RequestDto view(CustomerProductRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    List<CustomerRequestRevision> revisionList =
        revisions.findByTenantIdAndRequestIdOrderByRevisionNoDesc(tenantId, request.getId());
    List<CustomerRequestDecision> decisionList =
        decisions.findByTenantIdAndRequestIdOrderByRecordedAtDescIdDesc(tenantId, request.getId());
    return new CustomerRequestDtos.RequestDto(
        request.getId(),
        request.getUid(),
        request.getCustomerId(),
        request.getSalesOrderId(),
        request.getOriginOrderId(),
        request.getDescription(),
        request.getReferenceProductId(),
        request.getRequestedQty(),
        request.getUnit(),
        request.getRequestedColorNote(),
        request.getRequestedWidth(),
        request.getRequestedWidthUnit(),
        request.getRequestedDeliveryDate(),
        request.getSampleReceivedAt(),
        request.getSampleNote(),
        request.getStatus(),
        request.getCurrentRevisionNo(),
        request.getResolvedLineId(),
        approvalCoversCurrentTerms(request),
        revisionList.stream().map(CustomerRequestViews::toDto).toList(),
        decisionList.stream().map(CustomerRequestViews::toDto).toList(),
        evaluations
            .findByTenantIdAndRequestIdOrderByEvaluatedAtDescIdDesc(tenantId, request.getId())
            .stream()
            .map(CustomerRequestViews::toDto)
            .toList(),
        attachments.forRequest(request.getId()),
        request.getRecordedBy(),
        request.getRecordedAt());
  }

  /** The latest revision, when there is one. */
  Optional<CustomerRequestRevision> latestRevision(CustomerProductRequest request) {
    return revisions.findByTenantIdAndRequestIdAndRevisionNo(
        TenantContext.requireTenantId(), request.getId(), request.getCurrentRevisionNo());
  }

  /**
   * True when the customer's latest decision on the latest revision is an approval of the current
   * terms: the request terms before it becomes a line, the line terms afterwards (SOI A11).
   */
  boolean approvalCoversCurrentTerms(CustomerProductRequest request) {
    if (request.getResolvedLineId() != null) {
      return lines
          .findByTenantIdAndId(TenantContext.requireTenantId(), request.getResolvedLineId())
          .filter(line -> Boolean.TRUE.equals(line.getIsActive()))
          .map(line -> request.coversLine(AcceptanceTerms.fingerprint(line)))
          .orElse(false);
    }
    Optional<CustomerRequestRevision> latest = latestRevision(request);
    if (latest.isEmpty()) {
      return false;
    }
    return decisions
        .findByTenantIdAndRequestIdOrderByRecordedAtDescIdDesc(
            TenantContext.requireTenantId(), request.getId())
        .stream()
        .filter(decision -> decision.getRevisionId().equals(latest.get().getId()))
        .findFirst()
        .map(
            decision ->
                decision.getOutcome() == CustomerRequestDecisionOutcome.APPROVED
                    && decision.getTermsFingerprint().equals(request.requestTerms()))
        .orElse(false);
  }

  static CustomerRequestDtos.RevisionDto toDto(CustomerRequestRevision value) {
    return new CustomerRequestDtos.RevisionDto(
        value.getId(),
        value.getRevisionNo(),
        value.getSolution(),
        value.getProductId(),
        value.getSummary(),
        value.getCounterSampleNote(),
        value.getStatus(),
        value.getProposedBy(),
        value.getProposedAt(),
        value.getSentAt());
  }

  static CustomerRequestDtos.DecisionDto toDto(CustomerRequestDecision value) {
    return new CustomerRequestDtos.DecisionDto(
        value.getId(),
        value.getRevisionId(),
        value.getOutcome(),
        value.getCustomerContact(),
        value.getChannel(),
        value.getDecidedAt(),
        value.getNote(),
        value.getEvidenceAttachmentId(),
        value.isCustomerStatementConfirmed(),
        value.getRecordedBy(),
        value.getRecordedAt());
  }

  static CustomerRequestDtos.EvaluationDto toDto(CustomerRequestEvaluation value) {
    return new CustomerRequestDtos.EvaluationDto(
        value.getId(),
        value.getOutcome(),
        value.getNote(),
        value.getEvaluatedBy(),
        value.getEvaluatedAt());
  }
}
