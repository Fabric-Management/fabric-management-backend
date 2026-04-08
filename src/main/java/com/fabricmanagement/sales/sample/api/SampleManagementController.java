package com.fabricmanagement.sales.sample.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.sales.sample.app.SampleManagementService;
import com.fabricmanagement.sales.sample.domain.SampleDelivery;
import com.fabricmanagement.sales.sample.domain.SampleRequest;
import com.fabricmanagement.sales.sample.dto.CreateSampleRequestDto;
import com.fabricmanagement.sales.sample.dto.DispatchSampleRequest;
import com.fabricmanagement.sales.sample.dto.MarkDeliveredRequest;
import com.fabricmanagement.sales.sample.dto.SampleDeliveryDto;
import com.fabricmanagement.sales.sample.dto.SampleRequestDto;
import com.fabricmanagement.sales.sample.mapper.SampleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales/samples")
@RequiredArgsConstructor
@Tag(name = "Sample Management", description = "Sample request and delivery management")
public class SampleManagementController {

  private final SampleManagementService sampleService;
  private final SampleMapper mapper;

  // ═══════════════════════════════════════════════════════════════════════════
  // READ
  // ═══════════════════════════════════════════════════════════════════════════

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "List all sample requests (paginated)")
  public ResponseEntity<ApiResponse<PagedResponse<SampleRequestDto>>> listSampleRequests(
      @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
    Page<SampleRequestDto> page = sampleService.findAll(pageable).map(mapper::toDto);
    return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(page)));
  }

  @GetMapping("/requests/{requestId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(summary = "Get a sample request by ID")
  public ResponseEntity<ApiResponse<SampleRequestDto>> getSampleRequest(
      @PathVariable UUID requestId) {
    return ResponseEntity.ok(
        ApiResponse.success(
            mapper.toDto(
                sampleService
                    .findById(requestId)
                    .orElseThrow(
                        () ->
                            new EntityNotFoundException(
                                "Sample request not found: " + requestId)))));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // WRITE
  // ═══════════════════════════════════════════════════════════════════════════

  @PostMapping("/requests")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Create a new sample request")
  public ResponseEntity<ApiResponse<SampleRequestDto>> requestSample(
      @Valid @RequestBody CreateSampleRequestDto request) {
    SampleRequest entity = mapper.toEntity(request);
    SampleRequest saved = sampleService.requestSample(entity);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(mapper.toDto(saved)));
  }

  @PostMapping("/requests/{requestId}/dispatch")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Dispatch a sample for delivery")
  public ResponseEntity<ApiResponse<SampleDeliveryDto>> dispatchSample(
      @PathVariable UUID requestId, @Valid @RequestBody DispatchSampleRequest req) {
    SampleDelivery delivery =
        sampleService.dispatchSample(
            requestId,
            req.getDeliveryMethod(),
            req.getTrackingNumber(),
            req.getCargoCompany(),
            req.getDeliveredById());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(mapper.toDto(delivery)));
  }

  @PostMapping("/deliveries/{deliveryId}/mark-delivered")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(summary = "Mark a sample delivery as delivered")
  public ResponseEntity<ApiResponse<SampleDeliveryDto>> markDelivered(
      @PathVariable UUID deliveryId, @Valid @RequestBody MarkDeliveredRequest req) {
    SampleDelivery delivery =
        sampleService.markAsDelivered(deliveryId, req.getRecipientName(), req.getPhotoUrl());
    return ResponseEntity.ok(ApiResponse.success(mapper.toDto(delivery)));
  }
}
