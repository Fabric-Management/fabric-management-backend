package com.fabricmanagement.sales.ownership.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.user.app.UserDisplayNameResolver;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.ownership.domain.CustomerAccountTeamMember;
import com.fabricmanagement.sales.ownership.domain.DefaultOwnerPolicy;
import com.fabricmanagement.sales.ownership.domain.OwnerResolution;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionContext;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionReason;
import com.fabricmanagement.sales.ownership.dto.CustomerAccountTeamCandidateResponse;
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
  @Mock private UserDisplayNameResolver userDisplayNameResolver;
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
  void resolvesAcquirerAndDefaultOwnerNamesWhenAcquirerIsNotATeamMember() {
    eligibleCustomer();
    when(memberRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
            tenantId, customerId))
        .thenReturn(List.of());
    when(defaultOwnerPolicy.resolve(new OwnerResolutionContext(tenantId, customerId, null)))
        .thenReturn(new OwnerResolution(acquirerId, OwnerResolutionReason.ACQUIRER));
    when(userDisplayNameResolver.resolveDisplayName(tenantId, acquirerId))
        .thenReturn(Optional.of("Emma Clarke"));

    CustomerAccountTeamResponse response = service.getAccountTeam(tenantId, customerId);

    assertThat(response.acquiredById()).isEqualTo(acquirerId);
    assertThat(response.acquiredByDisplayName()).isEqualTo("Emma Clarke");
    assertThat(response.defaultOwnerId()).isEqualTo(acquirerId);
    assertThat(response.defaultOwnerDisplayName()).isEqualTo("Emma Clarke");
    assertThat(response.defaultOwnerReason()).isEqualTo(OwnerResolutionReason.ACQUIRER);
  }

  @Test
  void returnsNullDisplayNamesWhenOwnerAndAcquirerIdsAreNull() {
    eligibleCustomer(null);
    when(memberRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
            tenantId, customerId))
        .thenReturn(List.of());
    when(defaultOwnerPolicy.resolve(new OwnerResolutionContext(tenantId, customerId, null)))
        .thenReturn(new OwnerResolution(null, OwnerResolutionReason.TRIAGE_REQUIRED));

    CustomerAccountTeamResponse response = service.getAccountTeam(tenantId, customerId);

    assertThat(response.acquiredById()).isNull();
    assertThat(response.acquiredByDisplayName()).isNull();
    assertThat(response.defaultOwnerId()).isNull();
    assertThat(response.defaultOwnerDisplayName()).isNull();
    assertThat(response.defaultOwnerReason()).isEqualTo(OwnerResolutionReason.TRIAGE_REQUIRED);
    verifyNoInteractions(userDisplayNameResolver);
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

  @Test
  void returnsOnlyActiveUsersFromTheRequestedTenantAsCandidates() {
    UUID activeUserId = UUID.randomUUID();
    UUID inactiveUserId = UUID.randomUUID();
    UUID otherTenantId = UUID.randomUUID();
    eligibleCustomer();
    when(memberRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
            tenantId, customerId))
        .thenReturn(List.of());
    when(userQueryService.findByTenant(tenantId))
        .thenReturn(
            List.of(
                user(activeUserId, tenantId, "Active Rep", true),
                user(inactiveUserId, tenantId, "Inactive Rep", false),
                user(UUID.randomUUID(), otherTenantId, "Other Tenant Rep", true)));

    List<CustomerAccountTeamCandidateResponse> candidates =
        service.listCandidates(tenantId, customerId);

    assertThat(candidates)
        .extracting(CustomerAccountTeamCandidateResponse::userId)
        .containsExactly(activeUserId);
  }

  @Test
  void excludesActiveMembersAndKeepsDeactivatedMembersReAddable() {
    UUID activeMemberId = UUID.randomUUID();
    UUID formerMemberId = UUID.randomUUID();
    CustomerAccountTeamMember activeMember =
        CustomerAccountTeamMember.create(customerId, activeMemberId);
    CustomerAccountTeamMember formerMember =
        CustomerAccountTeamMember.create(customerId, formerMemberId);
    formerMember.deactivate();
    eligibleCustomer();
    when(memberRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
            tenantId, customerId))
        .thenReturn(List.of(activeMember, formerMember));
    when(userQueryService.findByTenant(tenantId))
        .thenReturn(
            List.of(
                user(activeMemberId, tenantId, "Active Member", true),
                user(formerMemberId, tenantId, "Former Member", true)));

    List<CustomerAccountTeamCandidateResponse> candidates =
        service.listCandidates(tenantId, customerId);

    assertThat(candidates)
        .extracting(CustomerAccountTeamCandidateResponse::userId)
        .containsExactly(formerMemberId);
  }

  @Test
  void ordersCandidatesDeterministicallyByDisplayNameThenUserIdWithNullsLast() {
    UUID firstAlexId = UUID.fromString("10000000-0000-4000-8000-000000000001");
    UUID secondAlexId = UUID.fromString("20000000-0000-4000-8000-000000000002");
    UUID nullNameId = UUID.fromString("30000000-0000-4000-8000-000000000003");
    eligibleCustomer();
    when(memberRepository.findAllByTenantIdAndCustomerIdOrderByCreatedAtAscUserIdAsc(
            tenantId, customerId))
        .thenReturn(List.of());
    when(userQueryService.findByTenant(tenantId))
        .thenReturn(
            List.of(
                user(nullNameId, tenantId, null, true),
                user(secondAlexId, tenantId, "alex", true),
                user(firstAlexId, tenantId, "Alex", true)));

    List<CustomerAccountTeamCandidateResponse> firstCall =
        service.listCandidates(tenantId, customerId);
    List<CustomerAccountTeamCandidateResponse> secondCall =
        service.listCandidates(tenantId, customerId);

    assertThat(firstCall).isEqualTo(secondCall);
    assertThat(firstCall)
        .extracting(CustomerAccountTeamCandidateResponse::userId)
        .containsExactly(firstAlexId, secondAlexId, nullNameId);
  }

  @Test
  void rejectsIneligibleCustomerBeforeReadingCandidates() {
    when(customerEligibilityService.requireEligible(tenantId, customerId))
        .thenThrow(SalesDomainException.customerNotEligible(customerId.toString()));

    assertThatThrownBy(() -> service.listCandidates(tenantId, customerId))
        .isInstanceOfSatisfying(
            SalesDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("SALES_020_CUSTOMER_NOT_ELIGIBLE"));

    verifyNoInteractions(memberRepository, userQueryService);
  }

  @Test
  void rejectsUnknownCustomerBeforeReadingCandidates() {
    when(customerEligibilityService.requireEligible(tenantId, customerId))
        .thenThrow(new NotFoundException("Customer not found: " + customerId));

    assertThatThrownBy(() -> service.listCandidates(tenantId, customerId))
        .isInstanceOf(NotFoundException.class);

    verifyNoInteractions(memberRepository, userQueryService);
  }

  private void eligibleCustomer() {
    eligibleCustomer(acquirerId);
  }

  private void eligibleCustomer(UUID acquiredById) {
    when(customerEligibilityService.requireEligible(tenantId, customerId))
        .thenReturn(new CustomerEligibilityService.EligibleCustomer(customerId, acquiredById));
  }

  private UserDto user(UUID userId, UUID userTenantId, String displayName, boolean active) {
    return UserDto.builder()
        .id(userId)
        .tenantId(userTenantId)
        .displayName(displayName)
        .isActive(active)
        .build();
  }
}
