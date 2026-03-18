package com.fabricmanagement.sales.sample.api;

import com.fabricmanagement.sales.sample.api.dto.DispatchSampleRequest;
import com.fabricmanagement.sales.sample.api.dto.MarkDeliveredRequest;
import com.fabricmanagement.sales.sample.app.SampleManagementService;
import com.fabricmanagement.sales.sample.domain.SampleDelivery;
import com.fabricmanagement.sales.sample.domain.SampleRequest;
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
@RequestMapping("/api/v1/sales/samples")
@RequiredArgsConstructor
public class SampleManagementController {

  private final SampleManagementService sampleService;

  @PostMapping("/requests")
  @ResponseStatus(HttpStatus.CREATED)
  public SampleRequest requestSample(@RequestBody SampleRequest request) {
    return sampleService.requestSample(request);
  }

  @PostMapping("/requests/{requestId}/dispatch")
  @ResponseStatus(HttpStatus.CREATED)
  public SampleDelivery dispatchSample(
      @PathVariable UUID requestId, @Valid @RequestBody DispatchSampleRequest req) {
    return sampleService.dispatchSample(
        requestId,
        req.getDeliveryMethod(),
        req.getTrackingNumber(),
        req.getCargoCompany(),
        req.getDeliveredById());
  }

  @PostMapping("/deliveries/{deliveryId}/mark-delivered")
  public SampleDelivery markDelivered(
      @PathVariable UUID deliveryId, @Valid @RequestBody MarkDeliveredRequest req) {
    return sampleService.markAsDelivered(deliveryId, req.getRecipientName(), req.getPhotoUrl());
  }
}
