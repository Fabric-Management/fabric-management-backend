package com.fabricmanagement.common.platform.policy.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.policy.app.PolicyService;
import com.fabricmanagement.common.platform.policy.domain.Policy;
import com.fabricmanagement.common.platform.policy.dto.CreatePolicyRequest;
import com.fabricmanagement.common.platform.policy.dto.PolicyDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}

