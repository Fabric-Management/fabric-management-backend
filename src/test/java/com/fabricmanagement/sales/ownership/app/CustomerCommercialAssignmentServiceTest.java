package com.fabricmanagement.sales.ownership.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.ownership.domain.ActorRef;
import com.fabricmanagement.sales.ownership.domain.AssignmentActorType;
import com.fabricmanagement.sales.ownership.domain.AssignmentClosureReason;
import com.fabricmanagement.sales.ownership.domain.AssignmentSource;
import com.fabricmanagement.sales.ownership.domain.CustomerCommercialAssignment;
import com.fabricmanagement.sales.ownership.infra.repository.CustomerCommercialAssignmentRepository;
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
class CustomerCommercialAssignmentServiceTest {

  @Mock private CustomerCommercialAssignmentRepository repository;
  @Mock private CustomerEligibilityService customerEligibilityService;
  @Mock private UserQueryService userQueryService;
  @InjectMocks private CustomerCommercialAssignmentService service;

  private UUID tenantId;
  private UUID customerId;
  private UUID representativeId;

  @BeforeEach
  void setUp() {
    tenantId = UUID.randomUUID();
    customerId = UUID.randomUUID();
    representativeId = UUID.randomUUID();
    lenient()
        .when(customerEligibilityService.requireEligible(tenantId, customerId))
        .thenReturn(new CustomerEligibilityService.EligibleCustomer(customerId, null));
  }

  @Test
  void rejectsInactiveRepresentativeWithUnprocessableEntity() {
    when(userQueryService.findById(tenantId, representativeId))
        .thenReturn(Optional.of(UserDto.builder().id(representativeId).isActive(false).build()));

    assertThatThrownBy(
            () ->
                service.assignPrimary(
                    tenantId,
                    customerId,
                    representativeId,
                    AssignmentSource.MANUAL,
                    ActorRef.user(UUID.randomUUID())))
        .isInstanceOfSatisfying(
            SalesDomainException.class,
            error -> {
              assertThat(error.getHttpStatus()).isEqualTo(422);
              assertThat(error.getErrorCode())
                  .isEqualTo("SALES_021_COMMERCIAL_ASSIGNMENT_USER_INACTIVE");
            });
    verify(repository, never()).save(any());
  }

  @Test
  void closesAndFlushesCurrentAssignmentBeforeOpeningReplacement() {
    UUID decidingUserId = UUID.randomUUID();
    CustomerCommercialAssignment current =
        assignment(representativeId, ActorRef.user(decidingUserId));
    UUID replacementId = UUID.randomUUID();
    when(userQueryService.findById(tenantId, replacementId))
        .thenReturn(Optional.of(UserDto.builder().id(replacementId).isActive(true).build()));
    when(repository.findByTenantIdAndCustomerIdAndValidToIsNull(tenantId, customerId))
        .thenReturn(Optional.of(current));
    when(repository.save(any(CustomerCommercialAssignment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CustomerCommercialAssignment replacement =
        service.assignPrimary(
            tenantId,
            customerId,
            replacementId,
            AssignmentSource.MANUAL,
            ActorRef.user(UUID.randomUUID()));

    assertThat(current.getClosureReason()).isEqualTo(AssignmentClosureReason.SUPERSEDED);
    assertThat(current.getDecidedByType()).isEqualTo(AssignmentActorType.USER);
    assertThat(current.getDecidedByUserId()).isEqualTo(decidingUserId);
    assertThat(replacement.getRepresentativeId()).isEqualTo(replacementId);
    assertThat(replacement.getSupersedesAssignmentId()).isEqualTo(current.getId());
    verify(repository).flush();
  }

  @Test
  void acquisitionConsumerDoesNothingForExistingHistory() {
    when(repository.existsByTenantIdAndCustomerId(tenantId, customerId)).thenReturn(true);

    assertThat(
            service.createAcquisitionIfAbsent(
                tenantId, customerId, representativeId, Instant.now()))
        .isEmpty();
    verify(userQueryService, never()).findById(any(), any());
    verify(repository, never()).save(any());
  }

  @Test
  void acquisitionConsumerUsesTheRelationshipTimestampForNewTruth() {
    Instant establishedAt = Instant.parse("2026-07-28T07:30:00Z");
    when(repository.existsByTenantIdAndCustomerId(tenantId, customerId)).thenReturn(false);
    when(userQueryService.findById(tenantId, representativeId))
        .thenReturn(Optional.of(UserDto.builder().id(representativeId).isActive(true).build()));
    when(repository.save(any(CustomerCommercialAssignment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CustomerCommercialAssignment assignment =
        service
            .createAcquisitionIfAbsent(tenantId, customerId, representativeId, establishedAt)
            .orElseThrow();

    assertThat(assignment.getValidFrom()).isEqualTo(establishedAt);
    assertThat(assignment.getSource()).isEqualTo(AssignmentSource.ACQUISITION);
    assertThat(assignment.getDecidedByType()).isEqualTo(AssignmentActorType.SYSTEM);
    assertThat(assignment.getDecidedBySystemCode()).isEqualTo("OWNERSHIP_POLICY");
  }

  @Test
  void deactivationClosesEveryOpenAssignmentWithoutChangingCreationActor() {
    UUID decidingUserId = UUID.randomUUID();
    CustomerCommercialAssignment first =
        assignment(representativeId, ActorRef.user(decidingUserId));
    CustomerCommercialAssignment second =
        CustomerCommercialAssignment.open(
            UUID.randomUUID(),
            representativeId,
            Instant.parse("2026-07-28T08:00:00Z"),
            AssignmentSource.ACQUISITION,
            ActorRef.system("OWNERSHIP_POLICY"),
            "OWNERSHIP_POLICY_V1",
            null);
    Instant occurredAt = Instant.parse("2026-07-28T10:00:00Z");
    when(repository.findAllByTenantIdAndRepresentativeIdAndValidToIsNullOrderByCustomerId(
            tenantId, representativeId))
        .thenReturn(List.of(first, second));

    int closed =
        service.closeAllForDeactivatedRepresentative(tenantId, representativeId, occurredAt);

    assertThat(closed).isEqualTo(2);
    assertThat(first.getValidTo()).isEqualTo(occurredAt);
    assertThat(first.getDecidedByUserId()).isEqualTo(decidingUserId);
    assertThat(second.getDecidedBySystemCode()).isEqualTo("OWNERSHIP_POLICY");
    assertThat(first.getClosureReason())
        .isEqualTo(AssignmentClosureReason.REPRESENTATIVE_DEACTIVATED);
    assertThat(first.getClosedBySystemCode()).isEqualTo("OWNERSHIP_POLICY");
  }

  private CustomerCommercialAssignment assignment(UUID repId, ActorRef actor) {
    CustomerCommercialAssignment assignment =
        CustomerCommercialAssignment.open(
            customerId,
            repId,
            Instant.parse("2026-07-28T09:00:00Z"),
            AssignmentSource.MANUAL,
            actor,
            "OWNERSHIP_POLICY_V1",
            null);
    assignment.setId(UUID.randomUUID());
    assignment.setTenantId(tenantId);
    return assignment;
  }
}
