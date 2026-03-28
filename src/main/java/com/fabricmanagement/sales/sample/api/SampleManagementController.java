package com.fabricmanagement.sales.sample.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.sample.app.SampleManagementService;
import com.fabricmanagement.sales.sample.domain.SampleDelivery;
import com.fabricmanagement.sales.sample.domain.SampleRequest;
import com.fabricmanagement.sales.sample.dto.CreateSampleRequestDto;
import com.fabricmanagement.sales.sample.dto.DispatchSampleRequest;
import com.fabricmanagement.sales.sample.dto.MarkDeliveredRequest;
import com.fabricmanagement.sales.sample.dto.SampleDeliveryDto;
import com.fabricmanagement.sales.sample.dto.SampleRequestDto;
import com.fabricmanagement.sales.sample.mapper.SampleMapper;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales/samples")
@RequiredArgsConstructor
public class SampleManagementController {

  private final SampleManagementService sampleService;
  private final SampleMapper mapper;

  @PostMapping("/requests")
  public ResponseEntity<ApiResponse<SampleRequestDto>> requestSample(
      @Valid @RequestBody CreateSampleRequestDto request) {
    SampleRequest entity = mapper.toEntity(request);
    SampleRequest saved = sampleService.requestSample(entity);
    return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
        .body(ApiResponse.success(mapper.toDto(saved)));
  }

  @PostMapping("/requests/{requestId}/dispatch")
  public ResponseEntity<ApiResponse<SampleDeliveryDto>> dispatchSample(
      @PathVariable UUID requestId, @Valid @RequestBody DispatchSampleRequest req) {
    SampleDelivery delivery =
        sampleService.dispatchSample(
            requestId,
            req.getDeliveryMethod(),
            req.getTrackingNumber(),
            req.getCargoCompany(),
            req.getDeliveredById());
    return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
        .body(ApiResponse.success(mapper.toDto(delivery)));
  }

  @PostMapping("/deliveries/{deliveryId}/mark-delivered")
  public ResponseEntity<ApiResponse<SampleDeliveryDto>> markDelivered(
      @PathVariable UUID deliveryId, @Valid @RequestBody MarkDeliveredRequest req) {
    SampleDelivery delivery =
        sampleService.markAsDelivered(deliveryId, req.getRecipientName(), req.getPhotoUrl());
    return ResponseEntity.ok(ApiResponse.success(mapper.toDto(delivery)));
  }
}
