package com.fabricmanagement.flowboard.decision.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

@Schema(name = "DecisionProjectionRebuildRequest")
public record DecisionProjectionRebuildRequest(@Size(max = 100) List<UUID> caseIds) {
  public DecisionProjectionRebuildRequest {
    caseIds = caseIds == null ? List.of() : List.copyOf(caseIds);
  }
}
