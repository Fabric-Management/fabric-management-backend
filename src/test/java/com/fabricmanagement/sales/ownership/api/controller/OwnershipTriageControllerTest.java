package com.fabricmanagement.sales.ownership.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.ownership.app.CustomerCommercialAssignmentService;
import com.fabricmanagement.sales.ownership.app.OwnershipTriageService;
import com.fabricmanagement.sales.ownership.domain.ActorRef;
import com.fabricmanagement.sales.ownership.domain.AssignmentSource;
import com.fabricmanagement.sales.ownership.domain.CustomerCommercialAssignment;
import com.fabricmanagement.sales.ownership.dto.AssignPrimaryRepresentativeRequest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OwnershipTriageControllerTest {

  @Mock private OwnershipTriageService triageService;
  @Mock private CustomerCommercialAssignmentService assignmentService;

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void resolutionEndpointUsesTheExistingAssignmentServiceWithTriageProvenance() {
    UUID tenantId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID representativeId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(actorId);
    CustomerCommercialAssignment assignment =
        CustomerCommercialAssignment.open(
            customerId,
            representativeId,
            Instant.parse("2026-07-28T12:00:00Z"),
            AssignmentSource.TRIAGE_RESOLUTION,
            ActorRef.user(actorId),
            "OWNERSHIP_POLICY_V1",
            null);
    assignment.setId(UUID.randomUUID());
    assignment.setTenantId(tenantId);
    when(assignmentService.assignPrimary(
            tenantId,
            customerId,
            representativeId,
            AssignmentSource.TRIAGE_RESOLUTION,
            ActorRef.user(actorId)))
        .thenReturn(assignment);
    OwnershipTriageController controller =
        new OwnershipTriageController(triageService, assignmentService);

    var response =
        controller.resolve(customerId, new AssignPrimaryRepresentativeRequest(representativeId));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    verify(assignmentService)
        .assignPrimary(
            tenantId,
            customerId,
            representativeId,
            AssignmentSource.TRIAGE_RESOLUTION,
            ActorRef.user(actorId));
  }
}
