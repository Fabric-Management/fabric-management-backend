package com.fabricmanagement.sales.orderintake.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.salesorder.app.CustomerApprovalService;
import com.fabricmanagement.sales.salesorder.dto.CustomerApprovalDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sales sends the order to the customer: the draft's details for information, or the evaluated
 * order for the customer's approval. The order is confirmed only by that approval.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Customer Approval", description = "Sending the order for the customer's approval")
public class CustomerApprovalController {

  private static final String BASE =
      "/api/v1/sales/order-intake/orders/{orderId}/customer-approval";

  private final CustomerApprovalService service;

  @GetMapping(BASE)
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "getCustomerApproval",
      summary = "Versions sent to the customer, approval requests and your actions")
  public ResponseEntity<ApiResponse<CustomerApprovalDtos.State>> getCustomerApproval(
      @PathVariable UUID orderId) {
    return ResponseEntity.ok(
        ApiResponse.success(service.state(orderId, OrderIntakeActor.current())));
  }

  @PostMapping(BASE + "/information")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "sendOrderInformation",
      summary = "Send the draft's details to the customer for information (nothing to approve)")
  public ResponseEntity<ApiResponse<CustomerApprovalDtos.State>> sendOrderInformation(
      @PathVariable UUID orderId) {
    return ResponseEntity.ok(
        ApiResponse.success(service.sendInformation(orderId, OrderIntakeActor.current())));
  }

  @PostMapping(BASE + "/send")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "sendOrderForApproval",
      summary = "Send the evaluated order for the customer's approval")
  public ResponseEntity<ApiResponse<CustomerApprovalDtos.State>> sendOrderForApproval(
      @PathVariable UUID orderId,
      @Valid @RequestBody(required = false) CustomerApprovalDtos.SendForApproval request) {
    return ResponseEntity.ok(
        ApiResponse.success(service.sendForApproval(orderId, request, OrderIntakeActor.current())));
  }

  @PostMapping(BASE + "/resend")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "resendOrderApprovalLink",
      summary = "Send a new approval link for the same version, possibly to a corrected address")
  public ResponseEntity<ApiResponse<CustomerApprovalDtos.State>> resendOrderApprovalLink(
      @PathVariable UUID orderId,
      @Valid @RequestBody(required = false) CustomerApprovalDtos.Resend request) {
    return ResponseEntity.ok(
        ApiResponse.success(service.resend(orderId, request, OrderIntakeActor.current())));
  }

  @GetMapping("/api/v1/sales/order-intake/customer-change-requests")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(
      operationId = "listCustomerChangeRequests",
      summary = "The customers' change requests sales has not followed up yet")
  public ResponseEntity<ApiResponse<List<CustomerApprovalDtos.ChangeRequestItem>>>
      listCustomerChangeRequests() {
    return ResponseEntity.ok(
        ApiResponse.success(service.changeRequests(OrderIntakeActor.current())));
  }
}
