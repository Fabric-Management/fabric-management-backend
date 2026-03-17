package com.fabricmanagement.procurement.rfq.api.controller;

import com.fabricmanagement.procurement.rfq.app.SupplierRFQService;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQ;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQLine;
import com.fabricmanagement.procurement.rfq.dto.CreateSupplierRFQRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/procurement/rfqs")
@RequiredArgsConstructor
public class SupplierRFQController {

  private final SupplierRFQService rfqService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SupplierRFQ createRfq(@Valid @RequestBody CreateSupplierRFQRequest req) {
    return rfqService.createRfq(req);
  }

  @PostMapping("/{rfqId}/lines")
  @ResponseStatus(HttpStatus.CREATED)
  public SupplierRFQ addLine(@PathVariable UUID rfqId, @Valid @RequestBody SupplierRFQLine line) {
    return rfqService.addLine(rfqId, line);
  }

  @PostMapping("/{rfqId}/recipients")
  @ResponseStatus(HttpStatus.CREATED)
  public SupplierRFQ addRecipient(@PathVariable UUID rfqId, @RequestParam UUID tradingPartnerId) {
    return rfqService.addRecipient(rfqId, tradingPartnerId);
  }

  @PostMapping("/{rfqId}/send")
  public SupplierRFQ sendRfq(@PathVariable UUID rfqId) {
    return rfqService.sendRfq(rfqId);
  }
}
