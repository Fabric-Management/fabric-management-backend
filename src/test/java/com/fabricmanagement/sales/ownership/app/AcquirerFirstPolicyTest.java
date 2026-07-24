package com.fabricmanagement.sales.ownership.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.ownership.domain.CustomerAccountTeamMember;
import com.fabricmanagement.sales.ownership.domain.OwnerResolution;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionContext;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionReason;
import com.fabricmanagement.sales.ownership.infra.repository.CustomerAccountTeamMemberRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AcquirerFirstPolicyTest {

  @Mock private CustomerEligibilityService customerEligibilityService;
  @Mock private CustomerAccountTeamMemberRepository memberRepository;
  @Mock private UserQueryService userQueryService;
  @InjectMocks private AcquirerFirstPolicy policy;

  private UUID tenantId;
  private UUID customerId;
  private UUID acquirerId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    customerId = UUID.randomUUID();
    acquirerId = UUID.randomUUID();
    when(customerEligibilityService.requireEligible(tenantId, customerId))
        .thenReturn(new CustomerEligibilityService.EligibleCustomer(customerId, acquirerId));
  }

  @Test
  void acceptsActiveAcquirerAsExplicitOverrideWithoutTeamMembership() {
    when(memberRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
            tenantId, customerId))
        .thenReturn(List.of());
    active(acquirerId);

    OwnerResolution result =
        policy.resolve(new OwnerResolutionContext(tenantId, customerId, acquirerId));

    assertThat(result.ownerId()).isEqualTo(acquirerId);
    assertThat(result.reason()).isEqualTo(OwnerResolutionReason.EXPLICIT_OVERRIDE);
  }

  @Test
  void acceptsActiveTeamMemberAsExplicitOverride() {
    UUID teamUserId = UUID.randomUUID();
    when(memberRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
            tenantId, customerId))
        .thenReturn(List.of(member(teamUserId, Instant.parse("2026-01-01T00:00:00Z"))));
    active(acquirerId);
    active(teamUserId);

    OwnerResolution result =
        policy.resolve(new OwnerResolutionContext(tenantId, customerId, teamUserId));

    assertThat(result.ownerId()).isEqualTo(teamUserId);
    assertThat(result.reason()).isEqualTo(OwnerResolutionReason.EXPLICIT_OVERRIDE);
  }

  @Test
  void rejectsInactiveAcquirerAsExplicitOverride() {
    when(memberRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
            tenantId, customerId))
        .thenReturn(List.of());
    inactive(acquirerId);

    assertThatThrownBy(
            () -> policy.resolve(new OwnerResolutionContext(tenantId, customerId, acquirerId)))
        .isInstanceOfSatisfying(
            SalesDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("SALES_018_OWNER_NOT_SELECTABLE"));
  }

  @Test
  void rejectsRandomUserOutsideSelectableSet() {
    UUID randomUserId = UUID.randomUUID();
    when(memberRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
            tenantId, customerId))
        .thenReturn(List.of());
    active(acquirerId);

    assertThatThrownBy(
            () -> policy.resolve(new OwnerResolutionContext(tenantId, customerId, randomUserId)))
        .isInstanceOfSatisfying(
            SalesDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("SALES_018_OWNER_NOT_SELECTABLE"));
  }

  @Test
  void rejectsTeamMemberWhoseUserWasDeactivated() {
    UUID inactiveMemberId = UUID.randomUUID();
    when(memberRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
            tenantId, customerId))
        .thenReturn(List.of(member(inactiveMemberId, Instant.parse("2026-01-01T00:00:00Z"))));
    active(acquirerId);
    inactive(inactiveMemberId);

    assertThatThrownBy(
            () ->
                policy.resolve(new OwnerResolutionContext(tenantId, customerId, inactiveMemberId)))
        .isInstanceOfSatisfying(
            SalesDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("SALES_018_OWNER_NOT_SELECTABLE"));
  }

  @Test
  void defaultsToActiveAcquirerBeforeAccountTeam() {
    UUID teamUserId = UUID.randomUUID();
    when(memberRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
            tenantId, customerId))
        .thenReturn(List.of(member(teamUserId, Instant.parse("2026-01-01T00:00:00Z"))));
    active(acquirerId);
    active(teamUserId);

    OwnerResolution result = policy.resolve(new OwnerResolutionContext(tenantId, customerId, null));

    assertThat(result.ownerId()).isEqualTo(acquirerId);
    assertThat(result.reason()).isEqualTo(OwnerResolutionReason.ACQUIRER);
  }

  @Test
  void skipsMemberWhoseUserWasDeactivatedAndPicksNextStableMember() {
    UUID inactiveMemberId = UUID.randomUUID();
    UUID activeMemberId = UUID.randomUUID();
    when(memberRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
            tenantId, customerId))
        .thenReturn(
            List.of(
                member(inactiveMemberId, Instant.parse("2026-01-01T00:00:00Z")),
                member(activeMemberId, Instant.parse("2026-01-02T00:00:00Z"))));
    inactive(acquirerId);
    inactive(inactiveMemberId);
    active(activeMemberId);

    OwnerResolution result = policy.resolve(new OwnerResolutionContext(tenantId, customerId, null));

    assertThat(result.ownerId()).isEqualTo(activeMemberId);
    assertThat(result.reason()).isEqualTo(OwnerResolutionReason.ACCOUNT_TEAM);
  }

  @Test
  void returnsTriageRequiredWhenNoSelectableOwnerExists() {
    when(memberRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
            tenantId, customerId))
        .thenReturn(List.of());
    inactive(acquirerId);

    OwnerResolution result = policy.resolve(new OwnerResolutionContext(tenantId, customerId, null));

    assertThat(result.ownerId()).isNull();
    assertThat(result.reason()).isEqualTo(OwnerResolutionReason.TRIAGE_REQUIRED);
  }

  private CustomerAccountTeamMember member(UUID userId, Instant createdAt) {
    CustomerAccountTeamMember member = CustomerAccountTeamMember.create(customerId, userId);
    member.setTenantId(tenantId);
    member.setCreatedAt(createdAt);
    return member;
  }

  private void active(UUID userId) {
    when(userQueryService.findById(tenantId, userId))
        .thenReturn(
            Optional.of(UserDto.builder().id(userId).tenantId(tenantId).isActive(true).build()));
  }

  private void inactive(UUID userId) {
    when(userQueryService.findById(tenantId, userId))
        .thenReturn(
            Optional.of(UserDto.builder().id(userId).tenantId(tenantId).isActive(false).build()));
  }
}
