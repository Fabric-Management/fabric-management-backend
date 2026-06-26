package com.fabricmanagement.procurement.rfq.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.procurement.rfq.app.SupplierRFQService;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQModuleType;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQStatus;
import com.fabricmanagement.procurement.rfq.dto.AddRecipientRequest;
import com.fabricmanagement.procurement.rfq.dto.AddRfqLineRequest;
import com.fabricmanagement.procurement.rfq.dto.CreateSupplierRFQRequest;
import com.fabricmanagement.procurement.rfq.dto.SupplierRFQResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fix #1 — Entity değil SupplierRFQResponse dönüyor. Fix #2 — addLine: SupplierRFQLine entity değil
 * AddRfqLineRequest DTO ile alınıyor. Fix #3 — Tüm endpoint'lerde @PreAuthorize eklendi. Fix #10 —
 * addRecipient: @RequestParam → AddRecipientRequest DTO.
 */
@RestController
@RequestMapping("/api/v1/procurement/rfqs")
@RequiredArgsConstructor
@Tag(name = "Supplier R F Q", description = "Supplier R F Q operations")
public class SupplierRFQController {

  private final SupplierRFQService rfqService;

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'procurement', 'read')")
  @Operation(summary = "Get Supplier RFQ by ID")
  public ResponseEntity<ApiResponse<SupplierRFQResponse>> getRfq(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResponse.success(rfqService.getRfq(id)));
  }

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'procurement', 'read')")
  @Operation(summary = "List Supplier RFQs")
  public ResponseEntity<ApiResponse<PagedResponse<SupplierRFQResponse>>> listRfqs(
      @RequestParam(required = false) SupplierRFQStatus status,
      @RequestParam(required = false) SupplierRFQModuleType moduleType,
      Pageable pageable) {
    return ResponseEntity.ok(
        ApiResponse.success(rfqService.listRfqs(status, moduleType, pageable)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@auth.can(authentication, 'procurement', 'write')")
  public SupplierRFQResponse createRfq(@Valid @RequestBody CreateSupplierRFQRequest req) {
    return rfqService.createRfq(req);
  }

  @PostMapping("/{rfqId}/lines")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@auth.can(authentication, 'procurement', 'write')")
  public SupplierRFQResponse addLine(
      @PathVariable UUID rfqId, @Valid @RequestBody AddRfqLineRequest req) {
    return rfqService.addLine(rfqId, req);
  }

  @PostMapping("/{rfqId}/recipients")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@auth.can(authentication, 'procurement', 'write')")
  public SupplierRFQResponse addRecipient(
      @PathVariable UUID rfqId, @Valid @RequestBody AddRecipientRequest req) {
    return rfqService.addRecipient(rfqId, req);
  }

  @PostMapping("/{rfqId}/send")
  @PreAuthorize("@auth.can(authentication, 'procurement', 'write')")
  public SupplierRFQResponse sendRfq(@PathVariable UUID rfqId) {
    return rfqService.sendRfq(rfqId);
  }
}
