package com.fabricmanagement.sales.orderintake.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.orderintake.app.CustomerRequestService;
import com.fabricmanagement.sales.orderintake.app.DeliveryPreferenceService;
import com.fabricmanagement.sales.orderintake.app.IntakeAttachmentService;
import com.fabricmanagement.sales.orderintake.domain.IntakeAttachmentKind;
import com.fabricmanagement.sales.orderintake.dto.CustomerRequestDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Custom customer requests on an order, the customer's answers to their revisions, files received
 * from the customer and the partial-delivery preference (SOI D7). Nothing is sent to the customer
 * from here; replies that arrived elsewhere are recorded.
 */
@RestController
@RequestMapping("/api/v1/sales-orders/{orderId}/intake")
@RequiredArgsConstructor
@Tag(name = "Order Intake", description = "Catalogue order entry, proposals and acceptance")
public class CustomerRequestController {

  private final CustomerRequestService requests;
  private final IntakeAttachmentService attachments;
  private final DeliveryPreferenceService deliveryPreference;

  @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "uploadIntakeAttachment",
      summary = "Store a file received from the customer (image, PDF or text, up to 10 MB)")
  public ResponseEntity<ApiResponse<CustomerRequestDtos.AttachmentDto>> uploadIntakeAttachment(
      @PathVariable UUID orderId,
      @RequestParam("kind") IntakeAttachmentKind kind,
      @RequestParam("file") MultipartFile file) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                attachments.upload(orderId, kind, file, OrderIntakeActor.current())));
  }

  @GetMapping("/attachments")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(operationId = "listIntakeAttachments", summary = "Files stored with the order")
  public ResponseEntity<ApiResponse<List<CustomerRequestDtos.AttachmentDto>>> listIntakeAttachments(
      @PathVariable UUID orderId) {
    return ResponseEntity.ok(
        ApiResponse.success(attachments.forOrder(orderId, OrderIntakeActor.current())));
  }

  @GetMapping("/attachments/{attachmentId}/content")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(operationId = "downloadIntakeAttachment", summary = "Download a stored file")
  public ResponseEntity<byte[]> downloadIntakeAttachment(
      @PathVariable UUID orderId, @PathVariable UUID attachmentId) {
    return AttachmentResponses.of(
        attachments.downloadForOrder(orderId, attachmentId, OrderIntakeActor.current()));
  }

  @PostMapping("/custom-requests")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "createCustomerProductRequest",
      summary = "Record a custom request; product, technical data and quantity may be unknown")
  public ResponseEntity<ApiResponse<CustomerRequestDtos.RequestDto>> createCustomerProductRequest(
      @PathVariable UUID orderId, @Valid @RequestBody CustomerRequestDtos.RequestInput request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(requests.create(orderId, request, OrderIntakeActor.current())));
  }

  @GetMapping("/custom-requests")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(operationId = "listCustomerProductRequests", summary = "Custom requests of the order")
  public ResponseEntity<ApiResponse<List<CustomerRequestDtos.RequestDto>>>
      listCustomerProductRequests(@PathVariable UUID orderId) {
    return ResponseEntity.ok(
        ApiResponse.success(requests.forOrder(orderId, OrderIntakeActor.current())));
  }

  @GetMapping("/custom-requests/unattached")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "listUnattachedCustomerProductRequests",
      summary = "The customer's unfinished requests that belong to no order")
  public ResponseEntity<ApiResponse<List<CustomerRequestDtos.RequestDto>>>
      listUnattachedCustomerProductRequests(@PathVariable UUID orderId) {
    return ResponseEntity.ok(
        ApiResponse.success(requests.unattached(orderId, OrderIntakeActor.current())));
  }

  @PutMapping("/custom-requests/{requestId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "updateCustomerProductRequest",
      summary = "Complete or correct a custom request")
  public ResponseEntity<ApiResponse<CustomerRequestDtos.RequestDto>> updateCustomerProductRequest(
      @PathVariable UUID orderId,
      @PathVariable UUID requestId,
      @Valid @RequestBody CustomerRequestDtos.RequestInput request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            requests.update(orderId, requestId, request, OrderIntakeActor.current())));
  }

  @PostMapping("/custom-requests/{requestId}/attach")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "attachCustomerProductRequest",
      summary = "Bring one of the customer's unattached requests into this draft")
  public ResponseEntity<ApiResponse<CustomerRequestDtos.RequestDto>> attachCustomerProductRequest(
      @PathVariable UUID orderId, @PathVariable UUID requestId) {
    return ResponseEntity.ok(
        ApiResponse.success(requests.attach(orderId, requestId, OrderIntakeActor.current())));
  }

  @PostMapping("/custom-requests/{requestId}/detach")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "detachCustomerProductRequest",
      summary = "Take an unfinished request off the draft so the ready lines can be confirmed")
  public ResponseEntity<ApiResponse<CustomerRequestDtos.RequestDto>> detachCustomerProductRequest(
      @PathVariable UUID orderId, @PathVariable UUID requestId) {
    return ResponseEntity.ok(
        ApiResponse.success(requests.detach(orderId, requestId, OrderIntakeActor.current())));
  }

  @PostMapping("/custom-requests/{requestId}/close")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(operationId = "closeCustomerProductRequest", summary = "Close an unresolved request")
  public ResponseEntity<ApiResponse<CustomerRequestDtos.RequestDto>> closeCustomerProductRequest(
      @PathVariable UUID orderId, @PathVariable UUID requestId) {
    return ResponseEntity.ok(
        ApiResponse.success(requests.close(orderId, requestId, OrderIntakeActor.current())));
  }

  @PostMapping("/custom-requests/{requestId}/revisions/{revisionNo}/sent")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "markCustomerRequestRevisionSent",
      summary = "Record that the latest revision was presented to the customer")
  public ResponseEntity<ApiResponse<CustomerRequestDtos.RequestDto>>
      markCustomerRequestRevisionSent(
          @PathVariable UUID orderId, @PathVariable UUID requestId, @PathVariable int revisionNo) {
    return ResponseEntity.ok(
        ApiResponse.success(
            requests.markSent(orderId, requestId, revisionNo, OrderIntakeActor.current())));
  }

  @PostMapping("/custom-requests/{requestId}/revisions/{revisionNo}/decision")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "recordCustomerRequestDecision",
      summary = "Record the customer's approval or rejection of the latest revision")
  public ResponseEntity<ApiResponse<CustomerRequestDtos.RequestDto>> recordCustomerRequestDecision(
      @PathVariable UUID orderId,
      @PathVariable UUID requestId,
      @PathVariable int revisionNo,
      @Valid @RequestBody CustomerRequestDtos.RecordDecision request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            requests.decide(orderId, requestId, revisionNo, request, OrderIntakeActor.current())));
  }

  @PostMapping("/custom-requests/{requestId}/resolve")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "resolveCustomerProductRequest",
      summary = "Turn an approved request into an order line of the approved product")
  public ResponseEntity<ApiResponse<CustomerRequestDtos.RequestDto>> resolveCustomerProductRequest(
      @PathVariable UUID orderId,
      @PathVariable UUID requestId,
      @Valid @RequestBody CustomerRequestDtos.ResolveRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            requests.resolve(orderId, requestId, request, OrderIntakeActor.current())));
  }

  @GetMapping("/partial-delivery")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "getPartialDeliveryPreference",
      summary = "The customer's partial-delivery answer; UNKNOWN when none was recorded")
  public ResponseEntity<ApiResponse<CustomerRequestDtos.DeliveryPreferenceDto>>
      getPartialDeliveryPreference(@PathVariable UUID orderId) {
    return ResponseEntity.ok(
        ApiResponse.success(deliveryPreference.get(orderId, OrderIntakeActor.current())));
  }

  @PutMapping("/partial-delivery")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "recordPartialDeliveryPreference",
      summary = "Record whether the customer allows partial delivery")
  public ResponseEntity<ApiResponse<CustomerRequestDtos.DeliveryPreferenceDto>>
      recordPartialDeliveryPreference(
          @PathVariable UUID orderId,
          @Valid @RequestBody CustomerRequestDtos.RecordDeliveryPreference request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            deliveryPreference.record(orderId, request, OrderIntakeActor.current())));
  }
}
