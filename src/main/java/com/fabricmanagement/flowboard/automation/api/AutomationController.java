package com.fabricmanagement.flowboard.automation.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.flowboard.automation.app.AutomationRuleService;
import com.fabricmanagement.flowboard.automation.dto.AutomationRuleRequest;
import com.fabricmanagement.flowboard.automation.dto.AutomationRuleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flowboard/automations")
@RequiredArgsConstructor
@Tag(name = "FlowBoard — Otomasyon", description = "Otomasyon kurallarının yönetimi")
@Slf4j
public class AutomationController {

  private final AutomationRuleService automationRuleService;

  @GetMapping
  @Operation(summary = "Aktif tenant'ın tüm kurallarını veya board bazlı kurallarını getirir")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<List<AutomationRuleResponse>>> getAutomations(
      @RequestParam(required = false) UUID boardId) {
    List<AutomationRuleResponse> responses = automationRuleService.getAutomations(boardId);
    return ResponseEntity.ok(ApiResponse.success(responses));
  }

  @PostMapping
  @Operation(summary = "Yeni otomasyon kuralı oluştur")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<AutomationRuleResponse>> createRule(
      @Valid @RequestBody AutomationRuleRequest request) {
    AutomationRuleResponse response = automationRuleService.createRule(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Mevcut otomasyon kuralını güncelle")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<AutomationRuleResponse>> updateRule(
      @PathVariable UUID id, @Valid @RequestBody AutomationRuleRequest request) {
    AutomationRuleResponse response = automationRuleService.updateRule(id, request);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PatchMapping("/{id}/toggle")
  @Operation(summary = "Otomasyon kuralını aktif/pasif yap")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<AutomationRuleResponse>> toggleActive(
      @PathVariable UUID id, @RequestParam boolean active) {
    AutomationRuleResponse response = automationRuleService.toggleActive(id, active);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Otomasyon kuralını iptal et (soft delete)")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable UUID id) {
    automationRuleService.deleteRule(id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }
}
