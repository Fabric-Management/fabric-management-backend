package com.fabricmanagement.sales.orderintake.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.orderintake.app.CustomerRequestEvaluationService;
import com.fabricmanagement.sales.orderintake.app.IntakeAttachmentService;
import com.fabricmanagement.sales.orderintake.dto.CustomerRequestDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Planning's evaluation of custom requests (SOI K15, R17). Guarded by production permissions
 * (IK-10): the evaluator is planning or a manager, not necessarily in the order's sales scope.
 */
@RestController
@RequestMapping("/api/v1/sales/order-intake/custom-requests")
@RequiredArgsConstructor
@Tag(name = "Custom Request Evaluation", description = "Technical evaluation of custom requests")
public class CustomerRequestEvaluationController {

  private final CustomerRequestEvaluationService evaluation;
  private final IntakeAttachmentService attachments;

  @GetMapping("/evaluation-queue")
  @PreAuthorize("@auth.can(authentication, 'production', 'read')")
  @Operation(
      operationId = "listCustomerRequestEvaluationQueue",
      summary = "Custom requests waiting for an evaluation or a new revision")
  public ResponseEntity<ApiResponse<List<CustomerRequestDtos.EvaluationItem>>> evaluationQueue() {
    return ResponseEntity.ok(ApiResponse.success(evaluation.queue(OrderIntakeActor.current())));
  }

  @GetMapping("/{requestId}")
  @PreAuthorize("@auth.can(authentication, 'production', 'read')")
  @Operation(
      operationId = "getCustomerProductRequestForEvaluation",
      summary = "A custom request with its files, evaluations, revisions and decisions")
  public ResponseEntity<ApiResponse<CustomerRequestDtos.RequestDto>> getForEvaluation(
      @PathVariable UUID requestId) {
    return ResponseEntity.ok(
        ApiResponse.success(evaluation.get(requestId, OrderIntakeActor.current())));
  }

  @GetMapping("/{requestId}/attachments/{attachmentId}/content")
  @PreAuthorize("@auth.can(authentication, 'production', 'read')")
  @Operation(
      operationId = "downloadCustomerRequestAttachment",
      summary = "Download a file of a custom request")
  public ResponseEntity<byte[]> downloadAttachment(
      @PathVariable UUID requestId, @PathVariable UUID attachmentId) {
    evaluation.readable(requestId, OrderIntakeActor.current());
    return AttachmentResponses.of(attachments.downloadForRequest(requestId, attachmentId));
  }

  @PostMapping("/{requestId}/evaluations")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "evaluateCustomerProductRequest",
      summary = "Record the technical evaluation of a custom request")
  public ResponseEntity<ApiResponse<CustomerRequestDtos.RequestDto>> evaluate(
      @PathVariable UUID requestId,
      @Valid @RequestBody CustomerRequestDtos.EvaluateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                evaluation.evaluate(requestId, request, OrderIntakeActor.current())));
  }

  @PostMapping("/{requestId}/revisions")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "proposeCustomerRequestRevision",
      summary = "Propose a product solution to present to the customer; supersedes older ones")
  public ResponseEntity<ApiResponse<CustomerRequestDtos.RequestDto>> propose(
      @PathVariable UUID requestId,
      @Valid @RequestBody CustomerRequestDtos.ProposeRevision request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                evaluation.propose(requestId, request, OrderIntakeActor.current())));
  }
}
