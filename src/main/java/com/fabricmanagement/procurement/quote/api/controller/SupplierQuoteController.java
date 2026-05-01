package com.fabricmanagement.procurement.quote.api.controller;

import com.fabricmanagement.procurement.quote.app.SupplierQuoteService;
import com.fabricmanagement.procurement.quote.dto.AddQuoteLineRequest;
import com.fabricmanagement.procurement.quote.dto.CreateSupplierQuoteRequest;
import com.fabricmanagement.procurement.quote.dto.SupplierQuoteResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fix #1 — Entity değil SupplierQuoteResponse dönüyor. Fix #2 — addLine: SupplierQuoteLine entity
 * değil AddQuoteLineRequest DTO. Fix #3 — Tüm endpoint'lerde @PreAuthorize eklendi.
 */
@RestController
@RequestMapping("/api/v1/procurement/supplier-quotes")
@RequiredArgsConstructor
public class SupplierQuoteController {

  private final SupplierQuoteService quoteService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@auth.can(authentication, 'materials', 'write')")
  public SupplierQuoteResponse createQuote(@Valid @RequestBody CreateSupplierQuoteRequest req) {
    return SupplierQuoteResponse.from(quoteService.createQuote(req));
  }

  @PostMapping("/{quoteId}/lines")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@auth.can(authentication, 'materials', 'write')")
  public SupplierQuoteResponse addLine(
      @PathVariable UUID quoteId, @Valid @RequestBody AddQuoteLineRequest req) {
    return SupplierQuoteResponse.from(quoteService.addLine(quoteId, req));
  }

  @PostMapping("/{quoteId}/accept")
  @PreAuthorize("@auth.can(authentication, 'materials', 'write')")
  public SupplierQuoteResponse acceptQuote(@PathVariable UUID quoteId) {
    return SupplierQuoteResponse.from(quoteService.markAsAccepted(quoteId));
  }

  @PostMapping("/{quoteId}/reject")
  @PreAuthorize("@auth.can(authentication, 'materials', 'write')")
  public SupplierQuoteResponse rejectQuote(@PathVariable UUID quoteId) {
    return SupplierQuoteResponse.from(quoteService.markAsRejected(quoteId));
  }
}
