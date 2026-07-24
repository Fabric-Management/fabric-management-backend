package com.fabricmanagement.sales.ownership.app;

import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.ownership.domain.CustomerAccountTeamMember;
import com.fabricmanagement.sales.ownership.domain.DefaultOwnerPolicy;
import com.fabricmanagement.sales.ownership.domain.OwnerResolution;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionContext;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionReason;
import com.fabricmanagement.sales.ownership.infra.repository.CustomerAccountTeamMemberRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** V1 commercial-owner policy: explicit override, then acquirer, then stable account-team order. */
@Component
@RequiredArgsConstructor
public class AcquirerFirstPolicy implements DefaultOwnerPolicy {

  private final CustomerEligibilityService customerEligibilityService;
  private final CustomerAccountTeamMemberRepository memberRepository;
  private final UserQueryService userQueryService;

  @Override
  @Transactional(readOnly = true)
  public OwnerResolution resolve(OwnerResolutionContext context) {
    CustomerEligibilityService.EligibleCustomer customer =
        customerEligibilityService.requireEligible(context.tenantId(), context.customerId());
    List<CustomerAccountTeamMember> members =
        memberRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
            context.tenantId(), customer.customerId());
    Map<UUID, Boolean> activeUsers = new HashMap<>();

    List<UUID> activeTeamUserIds =
        members.stream()
            .filter(member -> Boolean.TRUE.equals(member.getIsActive()))
            .map(CustomerAccountTeamMember::getUserId)
            .filter(userId -> isActiveUser(context.tenantId(), userId, activeUsers))
            .toList();
    boolean acquirerActive =
        customer.acquiredById() != null
            && isActiveUser(context.tenantId(), customer.acquiredById(), activeUsers);

    if (context.requestedOwnerId() != null) {
      boolean selectable =
          (acquirerActive && context.requestedOwnerId().equals(customer.acquiredById()))
              || activeTeamUserIds.contains(context.requestedOwnerId());
      if (!selectable) {
        throw SalesDomainException.ownerNotSelectable(context.requestedOwnerId().toString());
      }
      return new OwnerResolution(
          context.requestedOwnerId(), OwnerResolutionReason.EXPLICIT_OVERRIDE);
    }

    if (acquirerActive) {
      return new OwnerResolution(customer.acquiredById(), OwnerResolutionReason.ACQUIRER);
    }
    if (!activeTeamUserIds.isEmpty()) {
      return new OwnerResolution(activeTeamUserIds.get(0), OwnerResolutionReason.ACCOUNT_TEAM);
    }
    return new OwnerResolution(null, OwnerResolutionReason.TRIAGE_REQUIRED);
  }

  private boolean isActiveUser(UUID tenantId, UUID userId, Map<UUID, Boolean> activeUsers) {
    return activeUsers.computeIfAbsent(
        userId,
        id ->
            userQueryService
                .findById(tenantId, id)
                .map(UserDto::getIsActive)
                .map(Boolean.TRUE::equals)
                .orElse(false));
  }
}
