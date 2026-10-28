package com.fabricmanagement.production.core.batch.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.core.batch.app.BatchFinishingService;
import com.fabricmanagement.production.core.batch.domain.BatchFinishingStepStatus;
import com.fabricmanagement.production.core.batch.dto.BatchFinishingDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Finishing history of fabric lots: sending a lot out, receiving it, and what it went through. */
@RestController
@RequestMapping("/api/v1/production")
@RequiredArgsConstructor
@Tag(name = "Lot Finishing", description = "Finishing steps of fabric lots (FABRIC-3b)")
public class BatchFinishingController {

  private final BatchFinishingService finishingService;

  @GetMapping("/lots/{batchId}/finishing")
  @PreAuthorize("@auth.can(authentication, 'production', 'read')")
  @Operation(operationId = "getLotFinishingHistory", summary = "Finishing history of a lot")
  public ResponseEntity<ApiResponse<BatchFinishingDtos.HistoryDto>> history(
      @PathVariable UUID batchId) {
    return ResponseEntity.ok(ApiResponse.success(finishingService.history(batchId)));
  }

  @PostMapping("/lots/{batchId}/finishing/send")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "sendLotForFinishing",
      summary = "Send the whole lot out for a finishing process; it is held until it returns")
  public ResponseEntity<ApiResponse<BatchFinishingDtos.StepDto>> send(
      @PathVariable UUID batchId, @Valid @RequestBody BatchFinishingDtos.SendRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(finishingService.send(batchId, request)));
  }

  @PostMapping("/lots/{batchId}/finishing/history")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "recordFinishedLotStep",
      summary = "Enter a step that was finished before the lot came to us")
  public ResponseEntity<ApiResponse<BatchFinishingDtos.StepDto>> recordDone(
      @PathVariable UUID batchId,
      @Valid @RequestBody BatchFinishingDtos.RecordDoneRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(finishingService.recordDone(batchId, request)));
  }

  @PostMapping("/finishing-steps/{stepId}/receive")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "receiveLotFromFinishing",
      summary = "Receive the lot back: records the returned metres and releases the lot")
  public ResponseEntity<ApiResponse<BatchFinishingDtos.StepDto>> receive(
      @PathVariable UUID stepId, @Valid @RequestBody BatchFinishingDtos.ReceiveRequest request) {
    return ResponseEntity.ok(ApiResponse.success(finishingService.receive(stepId, request)));
  }

  @PostMapping("/finishing-steps/{stepId}/cancel")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "cancelLotFinishing",
      summary = "Withdraw a step entered by mistake; the lot is released")
  public ResponseEntity<ApiResponse<BatchFinishingDtos.StepDto>> cancel(
      @PathVariable UUID stepId, @Valid @RequestBody BatchFinishingDtos.CancelRequest request) {
    return ResponseEntity.ok(ApiResponse.success(finishingService.cancel(stepId, request)));
  }

  @GetMapping("/finishing-steps")
  @PreAuthorize("@auth.can(authentication, 'production', 'read')")
  @Operation(
      operationId = "searchLotFinishingSteps",
      summary = "Finishing steps across lots; with SENT, what is out at the firms")
  public ResponseEntity<ApiResponse<List<BatchFinishingDtos.StepDto>>> search(
      @RequestParam(required = false, defaultValue = "SENT") BatchFinishingStepStatus status,
      @RequestParam(required = false) UUID partnerId,
      @RequestParam(required = false) String jobNumber) {
    return ResponseEntity.ok(
        ApiResponse.success(finishingService.search(status, partnerId, jobNumber)));
  }
}
