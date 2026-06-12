package com.fabricmanagement.finance.invoice.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.finance.invoice.app.InvoiceService;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.dto.*;
import com.fabricmanagement.finance.payment.app.PaymentService;
import com.fabricmanagement.finance.payment.dto.CreateAllocationRequest;
import com.fabricmanagement.finance.payment.dto.CreatePaymentRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/finance/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoice", description = "Invoice operations")
public class InvoiceController {

  private final InvoiceService invoiceService;
  private final PaymentService paymentService;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  public ResponseEntity<ApiResponse<PagedResponse<InvoiceDto>>> getAll(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(invoiceService.getAllInvoices(pageable))));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  public ResponseEntity<ApiResponse<InvoiceDto>> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(invoiceService.getInvoice(id)));
  }

  @PostMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  public ResponseEntity<ApiResponse<InvoiceDto>> create(
      @Valid @RequestBody CreateInvoiceRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(invoiceService.createInvoice(request)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  public ResponseEntity<ApiResponse<InvoiceDto>> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateInvoiceRequest request) {
    return ResponseEntity.ok(ApiResponse.success(invoiceService.updateInvoice(id, request)));
  }

  @PatchMapping("/{id}/issue")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  public ResponseEntity<ApiResponse<InvoiceDto>> issue(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(invoiceService.issueInvoice(id)));
  }

  @PatchMapping("/{id}/send")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  public ResponseEntity<ApiResponse<InvoiceDto>> send(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(invoiceService.sendInvoice(id)));
  }

  @Deprecated
  @PatchMapping("/{id}/payment")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  public ResponseEntity<ApiResponse<InvoiceDto>> recordPayment(
      @PathVariable UUID id, @Valid @RequestBody RecordPaymentRequest request) {

    InvoiceDto invoice = invoiceService.getInvoice(id);
    CreatePaymentRequest paymentReq =
        new CreatePaymentRequest(
            invoice.tradingPartnerId(),
            "INBOUND",
            "OTHER",
            request.amount(),
            invoice.currency(),
            LocalDate.now(),
            "Legacy API",
            "Auto-generated from deprecated /payment endpoint",
            List.of(new CreateAllocationRequest(id, request.amount())));
    paymentService.createPayment(paymentReq);

    return ResponseEntity.ok(ApiResponse.success(invoiceService.getInvoice(id)));
  }

  @PatchMapping("/{id}/cancel")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  public ResponseEntity<ApiResponse<InvoiceDto>> cancel(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(invoiceService.cancelInvoice(id)));
  }

  @PatchMapping("/{id}/void")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  public ResponseEntity<ApiResponse<InvoiceDto>> voidInvoice(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(invoiceService.voidInvoice(id)));
  }

  @PatchMapping("/{id}/dispute")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  public ResponseEntity<ApiResponse<InvoiceDto>> dispute(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(invoiceService.disputeInvoice(id)));
  }

  @PatchMapping("/{id}/resolve-dispute")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  public ResponseEntity<ApiResponse<InvoiceDto>> resolveDispute(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(invoiceService.resolveDispute(id)));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    invoiceService.deleteInvoice(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/partner/{partnerId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  public ResponseEntity<ApiResponse<PagedResponse<InvoiceDto>>> getByPartner(
      @PathVariable UUID partnerId, @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(invoiceService.getByPartner(partnerId, pageable))));
  }

  @GetMapping("/partner/{partnerId}/unpaid")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  public ResponseEntity<ApiResponse<PagedResponse<InvoiceDto>>> getUnpaidByPartner(
      @PathVariable UUID partnerId, @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(invoiceService.getUnpaidByPartner(partnerId, pageable))));
  }

  @GetMapping("/status/{status}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  public ResponseEntity<ApiResponse<PagedResponse<InvoiceDto>>> getByStatus(
      @PathVariable InvoiceStatus status, @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(invoiceService.getByStatus(status, pageable))));
  }

  @GetMapping("/overdue")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  public ResponseEntity<ApiResponse<PagedResponse<InvoiceDto>>> getOverdue(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(invoiceService.getOverdue(pageable))));
  }

  @GetMapping("/awaiting-payment")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  public ResponseEntity<ApiResponse<PagedResponse<InvoiceDto>>> getAwaitingPayment(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(invoiceService.getAwaitingPayment(pageable))));
  }

  @GetMapping("/ar")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  public ResponseEntity<ApiResponse<PagedResponse<InvoiceDto>>> getAccountsReceivable(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(invoiceService.getAccountsReceivable(pageable))));
  }

  @GetMapping("/ap")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  public ResponseEntity<ApiResponse<PagedResponse<InvoiceDto>>> getAccountsPayable(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(invoiceService.getAccountsPayable(pageable))));
  }
}
