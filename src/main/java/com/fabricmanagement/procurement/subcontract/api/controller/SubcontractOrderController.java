package com.fabricmanagement.procurement.subcontract.api.controller;

import com.fabricmanagement.procurement.subcontract.app.SubcontractOrderService;
import com.fabricmanagement.procurement.subcontract.domain.SubcontractOrderStatus;
import com.fabricmanagement.procurement.subcontract.dto.CreateSubcontractOrderRequest;
import com.fabricmanagement.procurement.subcontract.dto.SubcontractOrderResponse;
import com.fabricmanagement.procurement.subcontract.dto.UpdateSubcontractOrderRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/procurement/subcontract-orders")
@RequiredArgsConstructor
public class SubcontractOrderController {

  private final SubcontractOrderService subcontractOrderService;

  @GetMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'products', 'read')")
  public SubcontractOrderResponse getSubcontractOrder(@PathVariable UUID id) {
    return subcontractOrderService.getSubcontractOrder(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  public SubcontractOrderResponse createSubcontractOrder(
      @RequestBody @Valid CreateSubcontractOrderRequest request) {
    return subcontractOrderService.createSubcontractOrder(request);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  public SubcontractOrderResponse updateDraft(
      @PathVariable UUID id, @RequestBody @Valid UpdateSubcontractOrderRequest request) {
    return subcontractOrderService.updateDraft(id, request);
  }

  /**
   * Transitions the SubcontractOrder to a new status (state machine enforced). When completing
   * (COMPLETED), actualReturnedQty must be provided — waste is then computed automatically.
   */
  @PatchMapping("/{id}/status")
  @PreAuthorize("@auth.can(authentication, 'products', 'write')")
  public SubcontractOrderResponse changeStatus(
      @PathVariable UUID id,
      @RequestParam SubcontractOrderStatus status,
      @RequestParam(required = false) BigDecimal actualReturnedQty) {
    return subcontractOrderService.changeStatus(id, status, actualReturnedQty);
  }
}
