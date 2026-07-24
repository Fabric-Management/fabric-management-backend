package com.fabricmanagement.sales.ownership.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.ownership.domain.CustomerAccountTeamMember;
import com.fabricmanagement.sales.ownership.domain.DefaultOwnerPolicy;
import com.fabricmanagement.sales.ownership.domain.OwnerResolution;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionContext;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionReason;
import com.fabricmanagement.sales.ownership.dto.CustomerAccountTeamMemberResponse;
import com.fabricmanagement.sales.ownership.dto.CustomerAccountTeamResponse;
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
class CustomerAccountTeamServiceTest {

  @Mock private CustomerEligibilityService customerEligibilityService;
  @Mock private CustomerAccountTeamMemberRepository memberRepository;
  @Mock private UserQueryService userQueryService;
  @Mock private DefaultOwnerPolicy defaultOwnerPolicy;
  @InjectMocks private CustomerAccountTeamService service;

  private UUID tenantId;
  private UUID customerId;
  private UUID acquirerId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    customerId = UUID.randomUUID();
    acquirerId = UUID.randomUUID();
  }

  @Test
  void addsActiveMemberAndReturnsThinResponse() {
    UUID userId = UUID.randomUUID();
    eligibleCustomer();
    when(userQueryService.findById(tenantId, userId))
        .thenReturn(
            Optional.of(
                UserDto.builder().id(userId).displayName("Emma Clarke").isActive(true).build()));
    when(memberRepository.findByTenantIdAndCustomerIdAndUserId(tenantId, customerId, userId))
        .thenReturn(Optional.empty());
    when(memberRepository.save(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              CustomerAccountTeamMember member = invocation.getArgument(0);
              member.setTenantId(tenantId);
              member.setCreatedAt(Instant.parse("2026-07-24T12:00:00Z"));
              return member;
            });

    CustomerAccountTeamMemberResponse response = service.addMember(tenantId, customerId, userId);

    assertThat(response.userId()).isEqualTo(userId);
    assertThat(response.displayName()).isEqualTo("Emma Clarke");
    assertThat(response.active()).isTrue();
  }

  @Test
  void reactivatesExistingMembershipIdempotently() {
    UUID userId = UUID.randomUUID();
    CustomerAccountTeamMember existing = CustomerAccountTeamMember.create(customerId, userId);
    existing.deactivate();
    eligibleCustomer();
    when(userQueryService.findById(tenantId, userId))
        .thenReturn(Optional.of(UserDto.builder().id(userId).isActive(true).build()));
    when(memberRepository.findByTenantIdAndCustomerIdAndUserId(tenantId, customerId, userId))
        .thenReturn(Optional.of(existing));
    when(memberRepository.save(existing)).thenReturn(existing);

    CustomerAccountTeamMemberResponse response = service.addMember(tenantId, customerId, userId);

    assertThat(response.active()).isTrue();
    assertThat(existing.getIsActive()).isTrue();
    verify(memberRepository).save(existing);
  }

  @Test
  void rejectsInactiveUserWithLockedCode() {
    UUID userId = UUID.randomUUID();
    eligibleCustomer();
    when(userQueryService.findById(tenantId, userId))
        .thenReturn(Optional.of(UserDto.builder().id(userId).isActive(false).build()));

    assertThatThrownBy(() -> service.addMember(tenantId, customerId, userId))
        .isInstanceOfSatisfying(
            SalesDomainException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo("SALES_019_ACCOUNT_TEAM_USER_INACTIVE"));
  }

  @Test
  void rejectsUnknownOrCrossTenantUserAsNotFound() {
    UUID userId = UUID.randomUUID();
    eligibleCustomer();
    when(userQueryService.findById(tenantId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.addMember(tenantId, customerId, userId))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void deactivatesWithoutHardDelete() {
    UUID userId = UUID.randomUUID();
    CustomerAccountTeamMember existing = CustomerAccountTeamMember.create(customerId, userId);
    eligibleCustomer();
    when(memberRepository.findByTenantIdAndCustomerIdAndUserId(tenantId, customerId, userId))
        .thenReturn(Optional.of(existing));
    when(memberRepository.save(existing)).thenReturn(existing);

    service.deactivateMember(tenantId, customerId, userId);

    assertThat(existing.getIsActive()).isFalse();
    assertThat(existing.getDeletedAt()).isNotNull();
    verify(memberRepository).save(existing);
  }

  @Test
  void returnsStableTriagePreviewWithoutViewerFallback() {
    eligibleCustomer();
    when(memberRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
            tenantId, customerId))
        .thenReturn(List.of());
    when(defaultOwnerPolicy.resolve(new OwnerResolutionContext(tenantId, customerId, null)))
        .thenReturn(new OwnerResolution(null, OwnerResolutionReason.TRIAGE_REQUIRED));

    CustomerAccountTeamResponse response = service.getAccountTeam(tenantId, customerId);

    assertThat(response.defaultOwnerId()).isNull();
    assertThat(response.defaultOwnerReason()).isEqualTo(OwnerResolutionReason.TRIAGE_REQUIRED);
    assertThat(response.acquiredById()).isEqualTo(acquirerId);
  }

  @Test
  void reportsMembershipInactiveWhenItsUserWasDeactivated() {
    UUID userId = UUID.randomUUID();
    CustomerAccountTeamMember member = CustomerAccountTeamMember.create(customerId, userId);
    member.setCreatedAt(Instant.parse("2026-07-24T12:00:00Z"));
    eligibleCustomer();
    when(memberRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
            tenantId, customerId))
        .thenReturn(List.of(member));
    when(userQueryService.findById(tenantId, userId))
        .thenReturn(
            Optional.of(
                UserDto.builder().id(userId).displayName("Departed Rep").isActive(false).build()));
    when(defaultOwnerPolicy.resolve(new OwnerResolutionContext(tenantId, customerId, null)))
        .thenReturn(new OwnerResolution(null, OwnerResolutionReason.TRIAGE_REQUIRED));

    CustomerAccountTeamResponse response = service.getAccountTeam(tenantId, customerId);

    assertThat(response.members())
        .singleElement()
        .satisfies(returnedMember -> assertThat(returnedMember.active()).isFalse());
  }

  private void eligibleCustomer() {
    when(customerEligibilityService.requireEligible(tenantId, customerId))
        .thenReturn(new CustomerEligibilityService.EligibleCustomer(customerId, acquirerId));
  }
}
