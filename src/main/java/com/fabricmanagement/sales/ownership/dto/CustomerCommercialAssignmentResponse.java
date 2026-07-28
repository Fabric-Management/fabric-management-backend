package com.fabricmanagement.sales.ownership.dto;

import com.fabricmanagement.sales.ownership.domain.AssignmentActorType;
import com.fabricmanagement.sales.ownership.domain.AssignmentClosureReason;
import com.fabricmanagement.sales.ownership.domain.AssignmentSource;
import com.fabricmanagement.sales.ownership.domain.CustomerCommercialAssignment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record CustomerCommercialAssignmentResponse(
    UUID id,
    UUID customerId,
    UUID representativeId,
    Instant validFrom,
    @Schema(nullable = true) Instant validTo,
    AssignmentSource source,
    AssignmentActorType decidedByType,
    @Schema(nullable = true) UUID decidedByUserId,
    @Schema(nullable = true) String decidedBySystemCode,
    @Schema(nullable = true) AssignmentActorType closedByType,
    @Schema(nullable = true) UUID closedByUserId,
    @Schema(nullable = true) String closedBySystemCode,
    @Schema(nullable = true) AssignmentClosureReason closureReason,
    String policyVersion,
    @Schema(nullable = true) UUID supersedesAssignmentId) {

  public static CustomerCommercialAssignmentResponse from(CustomerCommercialAssignment assignment) {
    return new CustomerCommercialAssignmentResponse(
        assignment.getId(),
        assignment.getCustomerId(),
        assignment.getRepresentativeId(),
        assignment.getValidFrom(),
        assignment.getValidTo(),
        assignment.getSource(),
        assignment.getDecidedByType(),
        assignment.getDecidedByUserId(),
        assignment.getDecidedBySystemCode(),
        assignment.getClosedByType(),
        assignment.getClosedByUserId(),
        assignment.getClosedBySystemCode(),
        assignment.getClosureReason(),
        assignment.getPolicyVersion(),
        assignment.getSupersedesAssignmentId());
  }
}
