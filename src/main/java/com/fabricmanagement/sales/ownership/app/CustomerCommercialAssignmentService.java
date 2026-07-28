package com.fabricmanagement.sales.ownership.app;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.ownership.domain.ActorRef;
import com.fabricmanagement.sales.ownership.domain.AssignmentClosureReason;
import com.fabricmanagement.sales.ownership.domain.AssignmentSource;
import com.fabricmanagement.sales.ownership.domain.CustomerCommercialAssignment;
import com.fabricmanagement.sales.ownership.dto.CustomerCommercialAssignmentResponse;
import com.fabricmanagement.sales.ownership.infra.repository.CustomerCommercialAssignmentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerCommercialAssignmentService {

  static final String POLICY_VERSION_V1 = "OWNERSHIP_POLICY_V1";

  private final CustomerCommercialAssignmentRepository repository;
  private final CustomerEligibilityService customerEligibilityService;
  private final UserQueryService userQueryService;

  @Transactional(readOnly = true)
  public Optional<CustomerCommercialAssignment> findCurrent(UUID tenantId, UUID customerId) {
    return repository.findByTenantIdAndCustomerIdAndValidToIsNull(tenantId, customerId);
  }

  @Transactional(readOnly = true)
  public Optional<CustomerCommercialAssignmentResponse> getCurrentAssignment(
      UUID tenantId, UUID customerId) {
    customerEligibilityService.requireEligible(tenantId, customerId);
    return findCurrent(tenantId, customerId).map(CustomerCommercialAssignmentResponse::from);
  }

  @Transactional(readOnly = true)
  public List<CustomerCommercialAssignmentResponse> getAssignmentHistory(
      UUID tenantId, UUID customerId) {
    customerEligibilityService.requireEligible(tenantId, customerId);
    return repository
        .findAllByTenantIdAndCustomerIdOrderByValidFromDescIdDesc(tenantId, customerId)
        .stream()
        .map(CustomerCommercialAssignmentResponse::from)
        .toList();
  }

  @Transactional
  public CustomerCommercialAssignment assignPrimary(
      UUID tenantId,
      UUID customerId,
      UUID representativeId,
      AssignmentSource source,
      ActorRef actor) {
    customerEligibilityService.requireEligible(tenantId, customerId);
    requireActiveRepresentative(tenantId, representativeId);

    Instant effectiveAt = Instant.now();
    Optional<CustomerCommercialAssignment> current =
        repository.findByTenantIdAndCustomerIdAndValidToIsNull(tenantId, customerId);
    if (current.filter(row -> row.getRepresentativeId().equals(representativeId)).isPresent()) {
      return current.orElseThrow();
    }

    UUID supersedesId = null;
    if (current.isPresent()) {
      CustomerCommercialAssignment previous = current.orElseThrow();
      previous.close(effectiveAt, AssignmentClosureReason.SUPERSEDED, actor);
      repository.save(previous);
      repository.flush();
      supersedesId = previous.getId();
    }

    CustomerCommercialAssignment assignment =
        CustomerCommercialAssignment.open(
            customerId,
            representativeId,
            effectiveAt,
            source,
            actor,
            POLICY_VERSION_V1,
            supersedesId);
    assignment.setTenantId(tenantId);
    return repository.save(assignment);
  }

  @Transactional
  public Optional<CustomerCommercialAssignment> createAcquisitionIfAbsent(
      UUID tenantId, UUID customerId, UUID representativeId, Instant establishedAt) {
    if (representativeId == null
        || repository.existsByTenantIdAndCustomerId(tenantId, customerId)
        || !isActiveRepresentative(tenantId, representativeId)) {
      return Optional.empty();
    }
    customerEligibilityService.requireEligible(tenantId, customerId);
    CustomerCommercialAssignment assignment =
        CustomerCommercialAssignment.open(
            customerId,
            representativeId,
            establishedAt,
            AssignmentSource.ACQUISITION,
            ActorRef.system("OWNERSHIP_POLICY"),
            POLICY_VERSION_V1,
            null);
    assignment.setTenantId(tenantId);
    return Optional.of(repository.save(assignment));
  }

  @Transactional
  public boolean closeCurrent(
      UUID tenantId,
      UUID customerId,
      Instant closedAt,
      AssignmentClosureReason reason,
      ActorRef actor) {
    Optional<CustomerCommercialAssignment> current =
        repository.findByTenantIdAndCustomerIdAndValidToIsNull(tenantId, customerId);
    if (current.isEmpty()) {
      return false;
    }
    current.orElseThrow().close(closedAt, reason, actor);
    repository.save(current.orElseThrow());
    return true;
  }

  @Transactional
  public int closeAllForDeactivatedRepresentative(
      UUID tenantId, UUID representativeId, Instant occurredAt) {
    List<CustomerCommercialAssignment> assignments =
        repository.findAllByTenantIdAndRepresentativeIdAndValidToIsNullOrderByCustomerId(
            tenantId, representativeId);
    ActorRef actor = ActorRef.system("OWNERSHIP_POLICY");
    assignments.forEach(
        assignment ->
            assignment.close(
                occurredAt, AssignmentClosureReason.REPRESENTATIVE_DEACTIVATED, actor));
    assignments.forEach(repository::save);
    return assignments.size();
  }

  private void requireActiveRepresentative(UUID tenantId, UUID representativeId) {
    UserDto representative =
        userQueryService
            .findById(tenantId, representativeId)
            .orElseThrow(() -> new NotFoundException("User not found: " + representativeId));
    if (!Boolean.TRUE.equals(representative.getIsActive())) {
      throw SalesDomainException.commercialAssignmentUserInactive(representativeId.toString());
    }
  }

  private boolean isActiveRepresentative(UUID tenantId, UUID representativeId) {
    return userQueryService
        .findById(tenantId, representativeId)
        .map(UserDto::getIsActive)
        .map(Boolean.TRUE::equals)
        .orElse(false);
  }
}
