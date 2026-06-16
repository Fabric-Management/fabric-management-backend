package com.fabricmanagement.finance.payment.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.finance.payment.app.PaymentService;
import com.fabricmanagement.finance.payment.dto.CreateAllocationRequest;
import com.fabricmanagement.finance.payment.dto.CreatePaymentRequest;
import com.fabricmanagement.finance.payment.dto.PaymentAllocationDto;
import com.fabricmanagement.finance.payment.dto.PaymentDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/finance/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Payment operations")
public class PaymentController {

  private final PaymentService paymentService;

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  public ResponseEntity<ApiResponse<PaymentDto>> createPayment(
      @Valid @RequestBody CreatePaymentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(paymentService.createPayment(request)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  public ResponseEntity<ApiResponse<PaymentDto>> getPayment(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(paymentService.getPayment(id)));
  }

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'finance', 'read')")
  public ResponseEntity<ApiResponse<Page<PaymentDto>>> getPayments(
      @RequestParam(required = false) UUID tradingPartnerId, Pageable pageable) {
    Page<PaymentDto> payments =
        tradingPartnerId != null
            ? paymentService.getPaymentsByPartner(tradingPartnerId, pageable)
            : paymentService.getPayments(pageable);
    return ResponseEntity.ok(ApiResponse.success(payments));
  }

  @PostMapping("/{id}/allocations")
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  public ResponseEntity<ApiResponse<PaymentAllocationDto>> allocatePayment(
      @PathVariable UUID id, @Valid @RequestBody CreateAllocationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(paymentService.allocatePayment(id, request)));
  }

  @DeleteMapping("/{id}/allocations/{allocationId}")
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  public ResponseEntity<ApiResponse<Void>> deallocatePayment(
      @PathVariable UUID id, @PathVariable UUID allocationId) {
    paymentService.deallocatePayment(id, allocationId);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @PatchMapping("/{id}/void")
  @PreAuthorize("@auth.can(authentication, 'finance', 'write')")
  public ResponseEntity<ApiResponse<PaymentDto>> voidPayment(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(paymentService.voidPayment(id)));
  }
}
