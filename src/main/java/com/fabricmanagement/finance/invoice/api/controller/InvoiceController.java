package com.fabricmanagement.finance.invoice.api.controller;

import com.fabricmanagement.finance.invoice.app.InvoiceService;
import com.fabricmanagement.finance.invoice.domain.InvoiceStatus;
import com.fabricmanagement.finance.invoice.dto.CreateInvoiceRequest;
import com.fabricmanagement.finance.invoice.dto.InvoiceDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for Invoices.
 *
 * <p>Uses TradingPartner for customer/vendor references (Faz 1.5 pattern). Supports both AR
 * (Accounts Receivable) and AP (Accounts Payable) invoices.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices", description = "Invoice management with TradingPartner integration")
public class InvoiceController {

  private final InvoiceService invoiceService;

  // ═══════════════════════════════════════════════════════════════════════════
  // CRUD
  // ═══════════════════════════════════════════════════════════════════════════

  @PostMapping
  @Operation(summary = "Create a new invoice")
  public ResponseEntity<InvoiceDto> createInvoice(
      @Valid @RequestBody CreateInvoiceRequest request) {
    InvoiceDto invoice = invoiceService.createInvoice(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(invoice);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get invoice by ID")
  public ResponseEntity<InvoiceDto> getInvoice(@PathVariable UUID id) {
    return invoiceService
        .findById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/number/{invoiceNumber}")
  @Operation(summary = "Get invoice by invoice number")
  public ResponseEntity<InvoiceDto> getInvoiceByNumber(@PathVariable String invoiceNumber) {
    return invoiceService
        .findByInvoiceNumber(invoiceNumber)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping
  @Operation(summary = "Get all invoices (paginated)")
  public ResponseEntity<Page<InvoiceDto>> getAllInvoices(
      @PageableDefault(size = 20, sort = "issueDate") Pageable pageable) {
    return ResponseEntity.ok(invoiceService.findAll(pageable));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Delete invoice (soft delete)")
  public ResponseEntity<Void> deleteInvoice(@PathVariable UUID id) {
    invoiceService.deleteInvoice(id);
    return ResponseEntity.noContent().build();
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // QUERIES
  // ═══════════════════════════════════════════════════════════════════════════

  @GetMapping("/partner/{partnerId}")
  @Operation(summary = "Get invoices by partner ID")
  public ResponseEntity<List<InvoiceDto>> getInvoicesByPartner(@PathVariable UUID partnerId) {
    return ResponseEntity.ok(invoiceService.findByPartner(partnerId));
  }

  @GetMapping("/partner/{partnerId}/unpaid")
  @Operation(summary = "Get unpaid invoices by partner")
  public ResponseEntity<List<InvoiceDto>> getUnpaidByPartner(@PathVariable UUID partnerId) {
    return ResponseEntity.ok(invoiceService.findUnpaidByPartner(partnerId));
  }

  @GetMapping("/partner/{partnerId}/outstanding")
  @Operation(summary = "Get outstanding amount for partner")
  public ResponseEntity<BigDecimal> getOutstandingAmount(@PathVariable UUID partnerId) {
    return ResponseEntity.ok(invoiceService.getOutstandingAmount(partnerId));
  }

  @GetMapping("/status/{status}")
  @Operation(summary = "Get invoices by status")
  public ResponseEntity<List<InvoiceDto>> getInvoicesByStatus(@PathVariable InvoiceStatus status) {
    return ResponseEntity.ok(invoiceService.findByStatus(status));
  }

  @GetMapping("/overdue")
  @Operation(summary = "Get overdue invoices")
  public ResponseEntity<List<InvoiceDto>> getOverdueInvoices() {
    return ResponseEntity.ok(invoiceService.findOverdueInvoices());
  }

  @GetMapping("/awaiting-payment")
  @Operation(summary = "Get invoices awaiting payment")
  public ResponseEntity<List<InvoiceDto>> getAwaitingPayment() {
    return ResponseEntity.ok(invoiceService.findAwaitingPayment());
  }

  @GetMapping("/ar")
  @Operation(summary = "Get accounts receivable (sales invoices)")
  public ResponseEntity<List<InvoiceDto>> getAccountsReceivable() {
    return ResponseEntity.ok(invoiceService.findAccountsReceivable());
  }

  @GetMapping("/ap")
  @Operation(summary = "Get accounts payable (purchase invoices)")
  public ResponseEntity<List<InvoiceDto>> getAccountsPayable() {
    return ResponseEntity.ok(invoiceService.findAccountsPayable());
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // LIFECYCLE
  // ═══════════════════════════════════════════════════════════════════════════

  @PostMapping("/{id}/issue")
  @Operation(summary = "Issue an invoice")
  public ResponseEntity<InvoiceDto> issueInvoice(@PathVariable UUID id) {
    return ResponseEntity.ok(invoiceService.issueInvoice(id));
  }

  @PostMapping("/{id}/send")
  @Operation(summary = "Send an invoice")
  public ResponseEntity<InvoiceDto> sendInvoice(@PathVariable UUID id) {
    return ResponseEntity.ok(invoiceService.sendInvoice(id));
  }

  @PostMapping("/{id}/payment")
  @Operation(summary = "Record a payment")
  public ResponseEntity<InvoiceDto> recordPayment(
      @PathVariable UUID id, @RequestParam BigDecimal amount) {
    return ResponseEntity.ok(invoiceService.recordPayment(id, amount));
  }

  @PostMapping("/{id}/cancel")
  @Operation(summary = "Cancel an invoice")
  public ResponseEntity<InvoiceDto> cancelInvoice(@PathVariable UUID id) {
    return ResponseEntity.ok(invoiceService.cancelInvoice(id));
  }

  @PostMapping("/{id}/void")
  @Operation(summary = "Void an invoice")
  public ResponseEntity<InvoiceDto> voidInvoice(@PathVariable UUID id) {
    return ResponseEntity.ok(invoiceService.voidInvoice(id));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // BATCH OPERATIONS
  // ═══════════════════════════════════════════════════════════════════════════

  @PostMapping("/batch/mark-overdue")
  @Operation(summary = "Mark overdue invoices (batch job)")
  public ResponseEntity<Integer> markOverdueInvoices() {
    return ResponseEntity.ok(invoiceService.markOverdueInvoices());
  }
}
