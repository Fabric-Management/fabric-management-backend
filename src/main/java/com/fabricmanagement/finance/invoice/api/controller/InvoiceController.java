package com.fabricmanagement.finance.invoice.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.finance.invoice.app.InvoiceService;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.dto.*;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/finance/invoices")
@RequiredArgsConstructor
public class InvoiceController {

  private final InvoiceService invoiceService;

  @GetMapping
  @PreAuthorize("@financeAccessService.canRead(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<PagedResponse<InvoiceDto>>> getAll(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(invoiceService.getAllInvoices(pageable))));
  }

  @GetMapping("/{id}")
  @PreAuthorize("@financeAccessService.canRead(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<InvoiceDto>> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(invoiceService.getInvoice(id)));
  }

  @PostMapping
  @PreAuthorize("@financeAccessService.canWrite(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<InvoiceDto>> create(
      @Valid @RequestBody CreateInvoiceRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(invoiceService.createInvoice(request)));
  }

  @PutMapping("/{id}")
  @PreAuthorize("@financeAccessService.canWrite(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<InvoiceDto>> update(
      @PathVariable UUID id, @Valid @RequestBody UpdateInvoiceRequest request) {
    return ResponseEntity.ok(ApiResponse.success(invoiceService.updateInvoice(id, request)));
  }

  @PatchMapping("/{id}/issue")
  @PreAuthorize("@financeAccessService.canWrite(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<InvoiceDto>> issue(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(invoiceService.issueInvoice(id)));
  }

  @PatchMapping("/{id}/send")
  @PreAuthorize("@financeAccessService.canWrite(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<InvoiceDto>> send(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(invoiceService.sendInvoice(id)));
  }

  @PatchMapping("/{id}/payment")
  @PreAuthorize("@financeAccessService.canWrite(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<InvoiceDto>> recordPayment(
      @PathVariable UUID id, @Valid @RequestBody RecordPaymentRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(invoiceService.recordPayment(id, request.amount())));
  }

  @PatchMapping("/{id}/cancel")
  @PreAuthorize("@financeAccessService.canWrite(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<InvoiceDto>> cancel(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(invoiceService.cancelInvoice(id)));
  }

  @PatchMapping("/{id}/void")
  @PreAuthorize("@financeAccessService.canWrite(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<InvoiceDto>> voidInvoice(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(invoiceService.voidInvoice(id)));
  }

  @PatchMapping("/{id}/dispute")
  @PreAuthorize("@financeAccessService.canWrite(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<InvoiceDto>> dispute(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(invoiceService.disputeInvoice(id)));
  }

  @PatchMapping("/{id}/resolve-dispute")
  @PreAuthorize("@financeAccessService.canWrite(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<InvoiceDto>> resolveDispute(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(invoiceService.resolveDispute(id)));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("@financeAccessService.canWrite(authentication, 'INVOICE')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    invoiceService.deleteInvoice(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/partner/{partnerId}")
  @PreAuthorize("@financeAccessService.canRead(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<PagedResponse<InvoiceDto>>> getByPartner(
      @PathVariable UUID partnerId, @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(invoiceService.getByPartner(partnerId, pageable))));
  }

  @GetMapping("/partner/{partnerId}/unpaid")
  @PreAuthorize("@financeAccessService.canRead(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<PagedResponse<InvoiceDto>>> getUnpaidByPartner(
      @PathVariable UUID partnerId, @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(
            PagedResponse.from(invoiceService.getUnpaidByPartner(partnerId, pageable))));
  }

  @GetMapping("/status/{status}")
  @PreAuthorize("@financeAccessService.canRead(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<PagedResponse<InvoiceDto>>> getByStatus(
      @PathVariable InvoiceStatus status, @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(invoiceService.getByStatus(status, pageable))));
  }

  @GetMapping("/overdue")
  @PreAuthorize("@financeAccessService.canRead(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<PagedResponse<InvoiceDto>>> getOverdue(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(invoiceService.getOverdue(pageable))));
  }

  @GetMapping("/awaiting-payment")
  @PreAuthorize("@financeAccessService.canRead(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<PagedResponse<InvoiceDto>>> getAwaitingPayment(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(invoiceService.getAwaitingPayment(pageable))));
  }

  @GetMapping("/ar")
  @PreAuthorize("@financeAccessService.canRead(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<PagedResponse<InvoiceDto>>> getAccountsReceivable(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(invoiceService.getAccountsReceivable(pageable))));
  }

  @GetMapping("/ap")
  @PreAuthorize("@financeAccessService.canRead(authentication, 'INVOICE')")
  public ResponseEntity<ApiResponse<PagedResponse<InvoiceDto>>> getAccountsPayable(
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(PagedResponse.from(invoiceService.getAccountsPayable(pageable))));
  }
}
