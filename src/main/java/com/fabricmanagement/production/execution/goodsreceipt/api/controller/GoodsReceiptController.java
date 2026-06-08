package com.fabricmanagement.production.execution.goodsreceipt.api.controller;

import com.fabricmanagement.production.execution.goodsreceipt.app.GoodsReceiptService;
import com.fabricmanagement.production.execution.goodsreceipt.dto.CreateGoodsReceiptRequest;
import com.fabricmanagement.production.execution.goodsreceipt.dto.GoodsReceiptResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/production/goods-receipts")
@RequiredArgsConstructor
@Tag(name = "Goods Receipt", description = "Goods Receipt operations")
public class GoodsReceiptController {

  private final GoodsReceiptService goodsReceiptService;

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  public GoodsReceiptResponse getGoodsReceipt(@PathVariable UUID id) {
    return goodsReceiptService.getGoodsReceipt(id);
  }

  /**
   * Creates a GoodsReceipt in DRAFT state. Items are auto-assigned barcodes based on source type
   * and sequence number.
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  public GoodsReceiptResponse createGoodsReceipt(
      @RequestBody @Valid CreateGoodsReceiptRequest request) {
    return goodsReceiptService.createGoodsReceipt(request);
  }

  /**
   * Confirms a GoodsReceipt (DRAFT → CONFIRMED). Triggers IWM stock entry and source order status
   * update. Receipt must have at least one item.
   */
  @PostMapping("/{id}/confirm")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  public GoodsReceiptResponse confirmGoodsReceipt(@PathVariable UUID id) {
    return goodsReceiptService.confirmGoodsReceipt(id);
  }
}
