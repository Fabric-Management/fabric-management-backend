package com.fabricmanagement.platform.policy.api.facade;

import com.fabricmanagement.platform.policy.app.PolicyService;
import com.fabricmanagement.platform.policy.domain.Policy;
import com.fabricmanagement.platform.policy.dto.CreatePolicyRequest;
import com.fabricmanagement.platform.policy.dto.PolicyDto;
import com.fabricmanagement.platform.policy.dto.UpdatePolicyRequest;
import com.fabricmanagement.platform.policy.mapper.PolicyMapper;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyFacade {

  private final PolicyService policyService;
  private final PolicyMapper mapper;

  @Transactional
  public PolicyDto createPolicy(CreatePolicyRequest request) {
    Policy policy =
        Policy.builder()
            .policyId(request.getPolicyId())
            .resource(request.getResource())
            .action(request.getAction())
            .priority(request.getPriority())
            .effect(request.getEffect())
            .conditions(request.getConditions())
            .description(request.getDescription())
            .build();
    Policy created = policyService.createPolicy(policy);
    return mapper.toDto(created);
  }

  @Transactional(readOnly = true)
  public List<PolicyDto> getAllPolicies() {
    return policyService.getAllPolicies().stream().map(mapper::toDto).collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public PolicyDto getPolicy(UUID id) {
    Policy policy =
        policyService
            .getPolicyById(id)
            .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + id));
    return mapper.toDto(policy);
  }

  @Transactional
  public PolicyDto updatePolicy(UUID id, UpdatePolicyRequest request) {
    Policy updated = policyService.updatePolicy(id, request);
    return mapper.toDto(updated);
  }

  @Transactional
  public void deletePolicy(UUID id) {
    policyService.deletePolicy(id);
  }

  @Transactional
  public PolicyDto enablePolicy(UUID id) {
    Policy enabled = policyService.enablePolicy(id);
    return mapper.toDto(enabled);
  }

  @Transactional
  public PolicyDto disablePolicy(UUID id) {
    Policy disabled = policyService.disablePolicy(id);
    return mapper.toDto(disabled);
  }
}
