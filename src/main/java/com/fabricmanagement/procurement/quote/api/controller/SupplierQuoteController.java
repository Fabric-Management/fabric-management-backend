package com.fabricmanagement.procurement.quote.api.controller;

import com.fabricmanagement.procurement.quote.app.SupplierQuoteService;
import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteLine;
import com.fabricmanagement.procurement.quote.dto.CreateSupplierQuoteRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/procurement/supplier-quotes")
@RequiredArgsConstructor
public class SupplierQuoteController {

  private final SupplierQuoteService quoteService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SupplierQuote createQuote(@Valid @RequestBody CreateSupplierQuoteRequest req) {
    return quoteService.createQuote(req);
  }

  @PostMapping("/{quoteId}/lines")
  @ResponseStatus(HttpStatus.CREATED)
  public SupplierQuote addLine(
      @PathVariable UUID quoteId, @Valid @RequestBody SupplierQuoteLine line) {
    return quoteService.addLine(quoteId, line);
  }

  @PostMapping("/{quoteId}/accept")
  public SupplierQuote acceptQuote(@PathVariable UUID quoteId) {
    return quoteService.markAsAccepted(quoteId);
  }

  @PostMapping("/{quoteId}/reject")
  public SupplierQuote rejectQuote(@PathVariable UUID quoteId) {
    return quoteService.markAsRejected(quoteId);
  }
}
