package com.fabricmanagement.sales.salesorder.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.salesorder.app.PublicOrderApprovalService;
import com.fabricmanagement.sales.salesorder.dto.PublicOrderApprovalDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer's approval page behind the e-mailed link. No sign-in: the link identifies the
 * request, a one-time code sent to the same address verifies the person, and the verified session
 * is required for a decision.
 */
@RestController
@RequestMapping("/api/v1/public/sales/order-approvals/{token}")
@RequiredArgsConstructor
@Tag(name = "Public Order Approvals", description = "Customer-facing order approval")
public class PublicOrderApprovalController {

  private final PublicOrderApprovalService service;

  @GetMapping
  @Operation(
      operationId = "getOrderApprovalLink",
      summary = "Who asks for the approval and of which order; no content before the code")
  public ResponseEntity<ApiResponse<PublicOrderApprovalDtos.LinkView>> getOrderApprovalLink(
      @PathVariable String token) {
    return ResponseEntity.ok(ApiResponse.success(service.view(token)));
  }

  @PostMapping("/code")
  @Operation(
      operationId = "sendOrderApprovalCode",
      summary = "E-mail a one-time code to the address the link went to")
  public ResponseEntity<ApiResponse<PublicOrderApprovalDtos.LinkView>> sendOrderApprovalCode(
      @PathVariable String token) {
    return ResponseEntity.ok(ApiResponse.success(service.sendCode(token)));
  }

  @PostMapping("/verify")
  @Operation(
      operationId = "verifyOrderApprovalCode",
      summary = "Check the code; a right one opens the order and starts a session")
  public ResponseEntity<ApiResponse<PublicOrderApprovalDtos.Verified>> verifyOrderApprovalCode(
      @PathVariable String token, @Valid @RequestBody PublicOrderApprovalDtos.VerifyCode request) {
    return ResponseEntity.ok(ApiResponse.success(service.verify(token, request.code())));
  }

  @PostMapping("/version")
  @Operation(
      operationId = "getOrderApprovalVersion",
      summary = "The sent order again, within the verified session")
  public ResponseEntity<ApiResponse<PublicOrderApprovalDtos.VersionView>> getOrderApprovalVersion(
      @PathVariable String token, @Valid @RequestBody PublicOrderApprovalDtos.Session request) {
    return ResponseEntity.ok(ApiResponse.success(service.version(token, request.session())));
  }

  @PostMapping("/approve")
  @Operation(operationId = "approveOrderVersion", summary = "Approve the sent order")
  public ResponseEntity<ApiResponse<PublicOrderApprovalDtos.LinkView>> approveOrderVersion(
      @PathVariable String token,
      @Valid @RequestBody PublicOrderApprovalDtos.Session request,
      HttpServletRequest http) {
    return ResponseEntity.ok(
        ApiResponse.success(
            service.approve(
                token, request.session(), http.getRemoteAddr(), http.getHeader("User-Agent"))));
  }

  @PostMapping("/request-changes")
  @Operation(
      operationId = "requestOrderChanges",
      summary = "Ask for changes to the sent order, saying what should change")
  public ResponseEntity<ApiResponse<PublicOrderApprovalDtos.LinkView>> requestOrderChanges(
      @PathVariable String token,
      @Valid @RequestBody PublicOrderApprovalDtos.RequestChanges request,
      HttpServletRequest http) {
    return ResponseEntity.ok(
        ApiResponse.success(
            service.requestChanges(
                token,
                request.session(),
                request.note(),
                http.getRemoteAddr(),
                http.getHeader("User-Agent"))));
  }
}
