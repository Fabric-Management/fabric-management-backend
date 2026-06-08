package com.fabricmanagement.platform.policy.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.policy.api.facade.PolicyFacade;
import com.fabricmanagement.platform.policy.dto.CreatePolicyRequest;
import com.fabricmanagement.platform.policy.dto.PolicyDto;
import com.fabricmanagement.platform.policy.dto.UpdatePolicyRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/common/policies")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Policy", description = "Policy operations")
public class PolicyController {

  private final PolicyFacade facade;

  @PostMapping
  public ResponseEntity<ApiResponse<PolicyDto>> createPolicy(
      @Valid @RequestBody CreatePolicyRequest request) {
    log.info("Creating policy: policyId={}", request.getPolicyId());
    return ResponseEntity.ok(
        ApiResponse.success(facade.createPolicy(request), "Policy created successfully"));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<PolicyDto>>> getAllPolicies() {
    log.debug("Getting all policies");
    return ResponseEntity.ok(ApiResponse.success(facade.getAllPolicies()));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<PolicyDto>> getPolicy(@PathVariable UUID id) {
    log.debug("Getting policy: id={}", id);
    return ResponseEntity.ok(ApiResponse.success(facade.getPolicy(id)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<PolicyDto>> updatePolicy(
      @PathVariable UUID id, @Valid @RequestBody UpdatePolicyRequest request) {
    log.info("Updating policy: id={}", id);
    return ResponseEntity.ok(
        ApiResponse.success(facade.updatePolicy(id, request), "Policy updated successfully"));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deletePolicy(@PathVariable UUID id) {
    log.info("Deleting policy: id={}", id);
    facade.deletePolicy(id);
    return ResponseEntity.ok(ApiResponse.success(null, "Policy deleted successfully"));
  }

  @PutMapping("/{id}/enable")
  public ResponseEntity<ApiResponse<PolicyDto>> enablePolicy(@PathVariable UUID id) {
    log.info("Enabling policy: id={}", id);
    return ResponseEntity.ok(
        ApiResponse.success(facade.enablePolicy(id), "Policy enabled successfully"));
  }

  @PutMapping("/{id}/disable")
  public ResponseEntity<ApiResponse<PolicyDto>> disablePolicy(@PathVariable UUID id) {
    log.info("Disabling policy: id={}", id);
    return ResponseEntity.ok(
        ApiResponse.success(facade.disablePolicy(id), "Policy disabled successfully"));
  }
}
