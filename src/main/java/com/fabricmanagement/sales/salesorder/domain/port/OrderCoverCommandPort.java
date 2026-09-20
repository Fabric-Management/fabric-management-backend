package com.fabricmanagement.sales.salesorder.domain.port;

import com.fabricmanagement.sales.salesorder.dto.ConfirmProductionCoverPayload;
import java.time.LocalDate;
import java.util.*;

public interface OrderCoverCommandPort {
  Decision confirm(
      UUID tenantId, UUID orderId, UUID actorId, ConfirmProductionCoverPayload payload);

  void attachTask(UUID tenantId, UUID caseId, UUID taskId);

  UUID cancel(UUID tenantId, UUID orderId);

  Optional<UUID> cancelIfPresent(UUID tenantId, UUID orderId);

  ProvisioningSnapshot currentProvisioning(UUID tenantId, UUID orderId, UUID caseId);

  record ProvisioningSnapshot(
      boolean active,
      UUID orderId,
      String orderNumber,
      LocalDate deadline,
      Set<UUID> unresolvedLineIds) {}

  sealed interface Decision {
    record Accepted(UUID resultId, Set<UUID> remainingLineIds) implements Decision {}

    record Rejected(String code, String message) implements Decision {}
  }
}
