package com.fabricmanagement.sales.ownership.app;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.ownership.domain.CustomerAccountTeamMember;
import com.fabricmanagement.sales.ownership.domain.DefaultOwnerPolicy;
import com.fabricmanagement.sales.ownership.domain.OwnerResolution;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionContext;
import com.fabricmanagement.sales.ownership.dto.CustomerAccountTeamMemberResponse;
import com.fabricmanagement.sales.ownership.dto.CustomerAccountTeamResponse;
import com.fabricmanagement.sales.ownership.infra.repository.CustomerAccountTeamMemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerAccountTeamService {

  private final CustomerEligibilityService customerEligibilityService;
  private final CustomerAccountTeamMemberRepository memberRepository;
  private final UserQueryService userQueryService;
  private final DefaultOwnerPolicy defaultOwnerPolicy;

  @Transactional(readOnly = true)
  public CustomerAccountTeamResponse getAccountTeam(UUID tenantId, UUID customerId) {
    CustomerEligibilityService.EligibleCustomer customer =
        customerEligibilityService.requireEligible(tenantId, customerId);
    List<CustomerAccountTeamMemberResponse> members =
        memberRepository
            .findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
                tenantId, customer.customerId())
            .stream()
            .map(member -> toResponse(tenantId, member))
            .toList();
    OwnerResolution resolution =
        defaultOwnerPolicy.resolve(
            new OwnerResolutionContext(tenantId, customer.customerId(), null));

    return new CustomerAccountTeamResponse(
        customer.customerId(),
        customer.acquiredById(),
        resolution.ownerId(),
        resolution.reason(),
        members);
  }

  @Transactional
  public CustomerAccountTeamMemberResponse addMember(UUID tenantId, UUID customerId, UUID userId) {
    CustomerEligibilityService.EligibleCustomer customer =
        customerEligibilityService.requireEligible(tenantId, customerId);
    UserDto user = requireUser(tenantId, userId);
    if (!Boolean.TRUE.equals(user.getIsActive())) {
      throw SalesDomainException.accountTeamUserInactive(userId.toString());
    }

    CustomerAccountTeamMember member =
        memberRepository
            .findByTenantIdAndCustomerIdAndUserId(tenantId, customer.customerId(), userId)
            .map(
                existing -> {
                  existing.activate();
                  return existing;
                })
            .orElseGet(() -> CustomerAccountTeamMember.create(customer.customerId(), userId));

    return toResponse(tenantId, memberRepository.save(member));
  }

  @Transactional
  public void deactivateMember(UUID tenantId, UUID customerId, UUID userId) {
    CustomerEligibilityService.EligibleCustomer customer =
        customerEligibilityService.requireEligible(tenantId, customerId);
    CustomerAccountTeamMember member =
        memberRepository
            .findByTenantIdAndCustomerIdAndUserId(tenantId, customer.customerId(), userId)
            .orElseThrow(
                () -> new NotFoundException("Customer account-team member not found: " + userId));
    member.deactivate();
    memberRepository.save(member);
  }

  private UserDto requireUser(UUID tenantId, UUID userId) {
    return userQueryService
        .findById(tenantId, userId)
        .orElseThrow(() -> new NotFoundException("User not found: " + userId));
  }

  private CustomerAccountTeamMemberResponse toResponse(
      UUID tenantId, CustomerAccountTeamMember member) {
    Optional<UserDto> user = userQueryService.findById(tenantId, member.getUserId());
    String displayName = user.map(UserDto::getDisplayName).orElse(null);
    boolean active =
        Boolean.TRUE.equals(member.getIsActive())
            && user.map(UserDto::getIsActive).map(Boolean.TRUE::equals).orElse(false);
    return new CustomerAccountTeamMemberResponse(
        member.getUserId(), displayName, active, member.getCreatedAt());
  }
}
