package com.fabricmanagement.common.platform.policy.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.policy.app.PolicyService;
import com.fabricmanagement.common.platform.policy.domain.Policy;
import com.fabricmanagement.common.platform.policy.dto.CreatePolicyRequest;
import com.fabricmanagement.common.platform.policy.dto.PolicyDto;
import com.fabricmanagement.common.platform.policy.dto.UpdatePolicyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/common/policies")
@RequiredArgsConstructor
@Slf4j
public class PolicyController {

    private final PolicyService policyService;

    @PostMapping
    public ResponseEntity<ApiResponse<PolicyDto>> createPolicy(@Valid @RequestBody CreatePolicyRequest request) {
        log.info("Creating policy: policyId={}", request.getPolicyId());

        Policy policy = Policy.builder()
            .policyId(request.getPolicyId())
            .resource(request.getResource())
            .action(request.getAction())
            .priority(request.getPriority())
            .effect(request.getEffect())
            .conditions(request.getConditions())
            .description(request.getDescription())
            .build();

        Policy created = policyService.createPolicy(policy);

        return ResponseEntity.ok(ApiResponse.success(PolicyDto.from(created), 
            "Policy created successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PolicyDto>>> getAllPolicies() {
        log.debug("Getting all policies");

        List<PolicyDto> policies = policyService.getAllPolicies()
            .stream()
            .map(PolicyDto::from)
            .toList();

        return ResponseEntity.ok(ApiResponse.success(policies));
    }

    /**
     * Get policy by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PolicyDto>> getPolicy(@PathVariable UUID id) {
        log.debug("Getting policy: id={}", id);

        Policy policy = policyService.getPolicyById(id)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + id));

        return ResponseEntity.ok(ApiResponse.success(PolicyDto.from(policy)));
    }

    /**
     * Update policy.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PolicyDto>> updatePolicy(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePolicyRequest request) {
        log.info("Updating policy: id={}", id);

        Policy updated = policyService.updatePolicy(id, request);

        return ResponseEntity.ok(ApiResponse.success(PolicyDto.from(updated), 
            "Policy updated successfully"));
    }

    /**
     * Delete policy (soft delete - disables it).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePolicy(@PathVariable UUID id) {
        log.info("Deleting policy: id={}", id);

        policyService.deletePolicy(id);

        return ResponseEntity.ok(ApiResponse.success(null, "Policy deleted successfully"));
    }

    /**
     * Enable policy.
     */
    @PutMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<PolicyDto>> enablePolicy(@PathVariable UUID id) {
        log.info("Enabling policy: id={}", id);

        Policy enabled = policyService.enablePolicy(id);

        return ResponseEntity.ok(ApiResponse.success(PolicyDto.from(enabled), 
            "Policy enabled successfully"));
    }

    /**
     * Disable policy.
     */
    @PutMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<PolicyDto>> disablePolicy(@PathVariable UUID id) {
        log.info("Disabling policy: id={}", id);

        Policy disabled = policyService.disablePolicy(id);

        return ResponseEntity.ok(ApiResponse.success(PolicyDto.from(disabled), 
            "Policy disabled successfully"));
    }
}

